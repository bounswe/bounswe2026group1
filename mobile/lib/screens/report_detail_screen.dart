import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:provider/provider.dart';
import 'package:video_player/video_player.dart';
import '../theme/app_colors.dart';
import '../models/report_model.dart';
import '../models/sse_event.dart';
import '../services/api_service.dart';
import '../services/auth_service.dart';
import '../services/sse_service.dart';
import '../main.dart' show MainShell, AuthShell;
import 'edit_report_screen.dart';

class ReportDetailScreen extends StatefulWidget {
  final ReportModel report;

  const ReportDetailScreen({super.key, required this.report});

  @override
  State<ReportDetailScreen> createState() => _ReportDetailScreenState();
}

class _ReportDetailScreenState extends State<ReportDetailScreen> {
  /// Mutable so an in-place edit (PUT /api/reports/{id}) can update what's
  /// rendered without popping/repushing the route.
  late ReportModel _report;
  ReportModel get report => _report;

  String? _fetchedUsername;
  bool _usernameLoading = false;

  // ── Video player ───────────────────────────────────────────────────────────
  VideoPlayerController? _videoController;
  bool _videoReady = false;

  bool get _hasVideo {
    if (report.mediaUrls.isEmpty) return false;
    // Strip query params (e.g. S3 pre-signed URLs) before checking extension
    final path = (Uri.tryParse(report.mediaUrls.first)?.path ?? report.mediaUrls.first).toLowerCase();
    return path.endsWith('.mp4') || path.endsWith('.mov') ||
           path.endsWith('.avi') || path.endsWith('.mkv') || path.endsWith('.webm');
  }

  // ── Vote state ─────────────────────────────────────────────────────────────
  late int _agrees;
  late int _disagrees;
  String? _myVote; // 'agree' | 'disagree' | null
  bool _voteLoading = false;

  // ── Live status (may differ from widget.report.status via SSE) ─────────────
  ReportStatus? _liveStatus;
  ReportStatus get _currentStatus => _liveStatus ?? report.status;

  // ── SSE ───────────────────────────────────────────────────────────────────
  StreamSubscription<SseEvent>? _sseSub;

  // ── Comment state ──────────────────────────────────────────────────────────
  List<_CommentData> _comments = [];
  bool _commentsLoading = false;
  String? _commentsError;
  final TextEditingController _commentController = TextEditingController();
  bool _commentSubmitting = false;

  // ── Follow state ─────────────────────────────────────────────────────────
  /// `null` = not yet loaded, otherwise reflects the server's truth.
  bool? _isFollowing;
  bool _followBusy = false;

  @override
  void initState() {
    super.initState();
    _report = widget.report;
    _agrees = report.agrees;
    _disagrees = report.disagrees;
    // Backend returns 'AGREE' / 'DISAGREE' / null — normalise to lowercase.
    _myVote = report.userVote?.toLowerCase();
    if (report.username == null) {
      _usernameLoading = true;
      _loadUsername();
    }
    _loadComments();
    if (_hasVideo) _initVideo();
    // Fetch fresh report so userVote / counts reflect server state.
    _refreshReport();
    _loadFollowStatus();
    // Subscribe to real-time updates.
    _sseSub = context.read<SseService>().events.listen(_onSseEvent);
  }

  void _onSseEvent(SseEvent event) {
    if (!mounted || event.reportId != report.reportId) return;

    if (event.eventType == 'REPORT_DELETED') {
      Navigator.of(context).pop();
      return;
    }

    if (event.eventType == 'REPORT_UPDATED') {
      setState(() {
        if (event.agrees != null) _agrees = event.agrees!;
        if (event.disagrees != null) _disagrees = event.disagrees!;
        if (event.status != null) {
          _liveStatus = ReportStatus.fromJson(event.status!);
        }
      });
    } else if (event.eventType == 'MEDIA_ADDED') {
      // Re-fetch the full report to get updated mediaUrls.
      _refreshReport();
    }
  }

  Future<void> _refreshReport() async {
    try {
      final fresh = await context.read<AuthService>().api.getReport(report.reportId);
      if (!mounted) return;
      setState(() {
        _report = fresh;
        _agrees = fresh.agrees;
        _disagrees = fresh.disagrees;
        _myVote = fresh.userVote?.toLowerCase();
      });
    } catch (_) {
      // Non-fatal — stale data from the list is still shown.
    }
  }

  Future<void> _loadFollowStatus() async {
    final auth = context.read<AuthService>();
    if (!auth.isAuthenticated) return;
    try {
      final following = await auth.api.isFollowingReport(report.reportId);
      if (!mounted) return;
      setState(() => _isFollowing = following);
    } catch (_) {
      // Non-fatal — leave the button in its initial unloaded state, the
      // user can still tap it to retry.
    }
  }

  Future<void> _toggleFollow() async {
    final auth = context.read<AuthService>();
    if (!auth.isAuthenticated) {
      _showLoginRequiredDialog();
      return;
    }
    if (_followBusy) return;
    final wasFollowing = _isFollowing ?? false;
    setState(() => _followBusy = true);
    try {
      final next = wasFollowing
          ? await auth.api.unfollowReport(report.reportId)
          : await auth.api.followReport(report.reportId);
      if (!mounted) return;
      setState(() {
        _isFollowing = next;
        _followBusy = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          duration: const Duration(seconds: 2),
          content: Text(next
              ? 'Following — you\'ll be notified about updates.'
              : 'Stopped following this report.'),
        ),
      );
    } catch (_) {
      if (!mounted) return;
      setState(() => _followBusy = false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Could not update follow status. Try again.'),
        ),
      );
    }
  }

  Future<void> _openEdit() async {
    final outcome = await Navigator.push<EditReportOutcome>(
      context,
      MaterialPageRoute(
        builder: (_) => EditReportScreen(report: report),
        fullscreenDialog: true,
      ),
    );
    if (!mounted) return;
    switch (outcome) {
      case EditReportUpdated(:final report):
        setState(() => _report = report);
      case EditReportDeleted():
        // Edit screen already popped itself; now pop this detail screen so
        // the user lands on whatever was below (home / feed / notification
        // origin). Capture the messenger first so the snackbar lands on the
        // destination scaffold.
        final messenger = ScaffoldMessenger.of(context);
        Navigator.of(context).pop();
        messenger.showSnackBar(
          const SnackBar(content: Text('Report deleted.')),
        );
      case null:
        break;
    }
  }

  /// iOS AVPlayer fails with -9405 when the URL issues a redirect (e.g. S3
  /// global endpoint → regional endpoint via 307). Resolve all redirects first
  /// so AVPlayer receives the final URL directly.
  Future<String> _resolveVideoUrl(String url) async {
    try {
      final client = HttpClient()
        ..connectionTimeout = const Duration(seconds: 8);
      String current = url;
      for (int i = 0; i < 5; i++) {
        final request = await client.getUrl(Uri.parse(current));
        request.followRedirects = false;
        // AVPlayer User-Agent equivalent to avoid WAF blocks
        request.headers.set(HttpHeaders.userAgentHeader, 'AppleCoreMedia/1.0.0.19E258 (iPhone; U; CPU OS 15_4 like Mac OS X; en_us)');
        final response = await request.close();
        
        final status = response.statusCode;
        if (status == 301 || status == 302 || status == 307 || status == 308) {
          final location = response.headers.value(HttpHeaders.locationHeader);
          await response.drain<void>();
          if (location != null) {
            current = Uri.parse(current).resolve(location).toString();
            continue;
          }
        }
        break;
      }
      client.close(force: true);
      return current;
    } catch (_) {}
    return url;
  }

  Future<void> _initVideo() async {
    final rawUrl = report.mediaUrls.first;
    final resolvedUrl = Platform.isIOS
        ? await _resolveVideoUrl(rawUrl)
        : rawUrl;

    final path = Uri.tryParse(resolvedUrl)?.path.toLowerCase() ?? '';
    final formatHint = path.endsWith('.m3u8') ? VideoFormat.hls : null;

    final controller = VideoPlayerController.networkUrl(
      Uri.parse(resolvedUrl),
      formatHint: formatHint,
      httpHeaders: {
        'User-Agent': 'AppleCoreMedia/1.0.0.19E258 (iPhone; U; CPU OS 15_4 like Mac OS X; en_us)',
      },
    );
    try {
      await controller.initialize();
    } catch (_) {
      controller.dispose();
      return;
    }
    if (!mounted) { controller.dispose(); return; }
    setState(() {
      _videoController = controller;
      _videoReady = true;
    });
  }

  @override
  void dispose() {
    _sseSub?.cancel();
    _videoController?.dispose();
    _commentController.dispose();
    super.dispose();
  }

  Future<void> _loadUsername() async {
    final name = await context.read<AuthService>().api.getUserName(report.userId);
    if (mounted) {
      setState(() {
        _fetchedUsername = name;
        _usernameLoading = false;
      });
    }
  }

  Future<void> _loadComments() async {
    setState(() {
      _commentsLoading = true;
      _commentsError = null;
    });
    try {
      final raw = await context
          .read<AuthService>()
          .api
          .getComments(report.reportId);
      if (!mounted) return;
      // Parse each row inside its own try so a single malformed comment
      // doesn't tank the whole list.
      final parsed = <_CommentData>[];
      for (final row in raw) {
        try {
          parsed.add(_CommentData.fromJson(row));
        } catch (_) {
          // Skip the bad row but keep going.
        }
      }
      setState(() {
        _comments = parsed;
        _commentsLoading = false;
      });
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _commentsLoading = false;
          _commentsError = e.userMessage;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _commentsLoading = false;
          _commentsError = 'Could not load comments. ($e)';
        });
      }
    }
  }

  Future<void> _submitComment() async {
    final text = _commentController.text.trim();
    if (text.isEmpty) return;
    final auth = context.read<AuthService>();
    if (!auth.isAuthenticated) {
      _showLoginRequiredDialog();
      return;
    }
    setState(() => _commentSubmitting = true);
    try {
      await auth.api.addComment(
        reportId: report.reportId,
        userId: auth.userId,
        content: text,
      );
      _commentController.clear();
      await _loadComments();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Couldn\'t post comment — ${e.userMessage}')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Couldn\'t post comment — $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _commentSubmitting = false);
    }
  }

  void _showLoginRequiredDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Text(
          'Sign In Required',
          style: TextStyle(
            fontFamily: 'Plus Jakarta Sans',
            fontWeight: FontWeight.w700,
            color: AppColors.onSurface,
          ),
        ),
        content: Text(
          'You need to log in to use this feature.',
          style: TextStyle(color: AppColors.onSurfaceVariant),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text('Cancel', style: TextStyle(color: AppColors.outline)),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
              Navigator.pushAndRemoveUntil(
                context,
                MaterialPageRoute(builder: (_) => const AuthShell()),
                (r) => false,
              );
            },
            child: Text(
              'Sign In',
              style: TextStyle(color: AppColors.primary, fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _vote(String vote) async {
    if (!context.read<AuthService>().isAuthenticated) {
      _showLoginRequiredDialog();
      return;
    }
    if (_voteLoading) return;

    final previousVote = _myVote;
    final previousAgrees = _agrees;
    final previousDisagrees = _disagrees;

    setState(() {
      _voteLoading = true;
      if (vote == 'agree') {
        if (_myVote == 'disagree') _disagrees--;
        if (_myVote == 'agree') {
          _agrees--;
          _myVote = null;
        } else {
          _agrees++;
          _myVote = 'agree';
        }
      } else {
        if (_myVote == 'agree') _agrees--;
        if (_myVote == 'disagree') {
          _disagrees--;
          _myVote = null;
        } else {
          _disagrees++;
          _myVote = 'disagree';
        }
      }
    });

    try {
      final api = context.read<AuthService>().api;
      if (vote == 'agree') {
        await api.verifyReport(report.reportId);
      } else {
        await api.unverifyReport(report.reportId);
      }
      await _refreshReport();
    } catch (e) {
      if (mounted) {
        setState(() {
          _myVote = previousVote;
          _agrees = previousAgrees;
          _disagrees = previousDisagrees;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Vote failed: ${e.toString()}')),
        );
      }
    } finally {
      if (mounted) setState(() => _voteLoading = false);
    }
  }

  String get _displayUsername =>
      _fetchedUsername ?? report.username ?? 'User #${report.userId}';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      body: Stack(
        children: [
          Column(
            children: [
              _buildTopBar(context),
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.fromLTRB(20, 8, 20, 200),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _buildHeroSection(),
                      const SizedBox(height: 20),
                      _buildTitleSection(),
                      const SizedBox(height: 24),
                      _buildDescriptionRow(),
                      if (report.objects.isNotEmpty) ...[
                        const SizedBox(height: 24),
                        _buildObjectsSection(),
                      ],
                      const SizedBox(height: 24),
                      _buildCommunityConsensus(),
                      const SizedBox(height: 24),
                      _buildMetadataRow(),
                      const SizedBox(height: 24),
                      _buildCommentsSection(),
                    ],
                  ),
                ),
              ),
            ],
          ),
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
                  child: _buildFollowButton(),
                ),
                _buildBottomNav(context),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ─── Top bar ────────────────────────────────────────────────────────────────

  Widget _buildTopBar(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.surfaceTint,
          boxShadow: [
            BoxShadow(
              color: AppColors.shadow,
              blurRadius: 6,
              offset: const Offset(0, 3),
            ),
          ],
        ),
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 10),
        child: Row(
          children: [
            IconButton(
              icon: Icon(Icons.arrow_back, color: AppColors.primary),
              onPressed: () => Navigator.pop(context),
            ),
            Expanded(
              child: Center(
                child: Text(
                  'Mapcess',
                  style: TextStyle(
                    fontFamily: 'Plus Jakarta Sans',
                    fontWeight: FontWeight.w800,
                    fontSize: 20,
                    color: AppColors.primary,
                  ),
                ),
              ),
            ),
            // Edit affordance — gated to the report's author. Backend
            // ownership checks still apply, this is just to hide the button
            // for users who can't act on it.
            if (context.watch<AuthService>().userId == report.userId)
              IconButton(
                icon: Icon(Icons.edit_outlined, color: AppColors.primary),
                onPressed: _openEdit,
              ),
            IconButton(
              icon: Icon(Icons.search, color: AppColors.onSurface),
              onPressed: () {},
            ),
          ],
        ),
      ),
    );
  }

  // ─── Hero image / placeholder ───────────────────────────────────────────────

  void _openFullscreen() {
    Navigator.of(context).push(
      PageRouteBuilder(
        opaque: false,
        barrierColor: Colors.black,
        pageBuilder: (ctx, _, __) => _FullscreenMediaPage(
          imageUrl: _hasVideo ? null : report.mediaUrls.firstOrNull,
          videoController: _hasVideo ? _videoController : null,
        ),
      ),
    );
  }

  Widget _buildHeroSection() {
    return Stack(
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(20),
          child: AspectRatio(
            aspectRatio: 16 / 9,
            child: report.mediaUrls.isNotEmpty
                ? _hasVideo
                    ? _buildVideoPlayer()
                    : GestureDetector(
                        onTap: _openFullscreen,
                        child: Image.network(
                          report.mediaUrls.first,
                          fit: BoxFit.cover,
                          errorBuilder: (context, error, stack) =>
                              _heroPlaceholder(),
                        ),
                      )
                : _heroPlaceholder(),
          ),
        ),
        Positioned(
          top: 14,
          right: 14,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
            decoration: BoxDecoration(
              color: _currentStatus.color.withOpacity(0.15),
              borderRadius: BorderRadius.circular(999),
              border: Border.all(color: _currentStatus.color.withOpacity(0.4)),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 7,
                  height: 7,
                  decoration: BoxDecoration(
                    color: _currentStatus.color,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 6),
                Text(
                  _currentStatus.label,
                  style: TextStyle(
                    fontFamily: 'Plus Jakarta Sans',
                    fontWeight: FontWeight.w700,
                    fontSize: 13,
                    color: _currentStatus.color,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _heroPlaceholder() {
    return Stack(
      fit: StackFit.expand,
      children: [
        CustomPaint(
          painter: _ImagePlaceholderPainter(color: report.displayColor),
        ),
        Center(
          child: Icon(
            report.displayIcon,
            size: 80,
            color: report.displayColor.withOpacity(0.25),
          ),
        ),
      ],
    );
  }

  Widget _buildVideoPlayer() {
    if (!_videoReady || _videoController == null) {
      return Container(
        color: Colors.black,
        child: const Center(
          child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
        ),
      );
    }
    return GestureDetector(
      onTap: () => setState(() {
        _videoController!.value.isPlaying
            ? _videoController!.pause()
            : _videoController!.play();
      }),
      child: Stack(
        fit: StackFit.expand,
        children: [
          FittedBox(
            fit: BoxFit.cover,
            child: SizedBox(
              width: _videoController!.value.size.width,
              height: _videoController!.value.size.height,
              child: VideoPlayer(_videoController!),
            ),
          ),
          // Play/pause overlay
          ValueListenableBuilder(
            valueListenable: _videoController!,
            builder: (_, value, __) => AnimatedOpacity(
              opacity: value.isPlaying ? 0.0 : 1.0,
              duration: const Duration(milliseconds: 200),
              child: Container(
                color: Colors.black45,
                child: const Center(
                  child: Icon(Icons.play_circle_fill,
                      color: Colors.white, size: 56),
                ),
              ),
            ),
          ),
          // Fullscreen button
          Positioned(
            bottom: 8,
            right: 8,
            child: GestureDetector(
              onTap: _openFullscreen,
              child: Container(
                padding: const EdgeInsets.all(6),
                decoration: BoxDecoration(
                  color: Colors.black54,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: const Icon(Icons.fullscreen,
                    color: Colors.white, size: 20),
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ─── Title & reporter ───────────────────────────────────────────────────────

  Widget _buildTitleSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '${report.headline} – Report #${report.reportId}',
          style: TextStyle(
            fontFamily: 'Plus Jakarta Sans',
            fontWeight: FontWeight.w800,
            fontSize: 24,
            color: AppColors.onSurface,
            height: 1.2,
          ),
        ),
        const SizedBox(height: 10),
        _buildEnvironmentChip(),
        const SizedBox(height: 14),
        Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: report.displayColor.withOpacity(0.12),
                shape: BoxShape.circle,
              ),
              child: Icon(
                Icons.person_outline,
                color: report.displayColor,
                size: 20,
              ),
            ),
            const SizedBox(width: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _usernameLoading
                    ? Container(
                        width: 90,
                        height: 14,
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerHigh,
                          borderRadius: BorderRadius.circular(6),
                        ),
                      )
                    : Text(
                        _displayUsername,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          color: AppColors.onSurface,
                        ),
                      ),
                Text(
                  'Reported ${report.timeAgo}',
                  style: TextStyle(
                    fontSize: 12,
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ],
        ),
      ],
    );
  }

  /// Indoor/outdoor pill rendered under the title.
  Widget _buildEnvironmentChip() {
    final env = report.environment;
    final outdoor = env == ReportEnvironment.outdoor;
    final fg = outdoor ? AppColors.success : AppColors.info;
    final bg = outdoor ? AppColors.successContainer : AppColors.infoContainer;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(env.icon, size: 14, color: fg),
          const SizedBox(width: 6),
          Text(
            env.label,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              letterSpacing: 0.4,
              color: fg,
            ),
          ),
        ],
      ),
    );
  }

  // ─── Description + mini-map ─────────────────────────────────────────────────

  Widget _buildDescriptionRow() {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          flex: 2,
          child: Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLowest,
              borderRadius: BorderRadius.circular(18),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.description_outlined,
                      color: AppColors.primary,
                      size: 18,
                    ),
                    const SizedBox(width: 8),
                    Text(
                      'Issue Details',
                      style: TextStyle(
                        fontFamily: 'Plus Jakarta Sans',
                        fontWeight: FontWeight.w700,
                        fontSize: 15,
                        color: AppColors.onSurface,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Text(
                  report.description.isNotEmpty
                      ? report.description
                      : 'No description provided.',
                  style: TextStyle(
                    fontSize: 13,
                    color: AppColors.onSurfaceVariant,
                    height: 1.55,
                  ),
                ),
                const SizedBox(height: 12),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 5,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.infoContainer,
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: Text(
                    report.headline,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: AppColors.onInfoContainer,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLowest,
              borderRadius: BorderRadius.circular(18),
            ),
            child: Column(
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: SizedBox(
                    height: 90,
                    width: double.infinity,
                    child: FlutterMap(
                      options: MapOptions(
                        initialCenter: LatLng(
                          report.latitude,
                          report.longitude,
                        ),
                        initialZoom: 15,
                        interactionOptions: const InteractionOptions(
                          flags: InteractiveFlag.none,
                        ),
                      ),
                      children: [
                        TileLayer(
                          urlTemplate:
                              'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                          userAgentPackageName:
                              'com.bounswe2026group1.mapcess',
                        ),
                        MarkerLayer(
                          markers: [
                            Marker(
                              point: LatLng(
                                report.latitude,
                                report.longitude,
                              ),
                              child: Icon(
                                Icons.location_on,
                                color: AppColors.primary,
                                size: 32,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                Text(
                  '${report.latitude.toStringAsFixed(4)}, ${report.longitude.toStringAsFixed(4)}',
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                    color: AppColors.onSurface,
                    letterSpacing: 0.3,
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 3),
                Text(
                  'GPS COORDINATES',
                  style: TextStyle(
                    fontSize: 8,
                    fontWeight: FontWeight.w600,
                    letterSpacing: 0.8,
                    color: AppColors.onSurfaceVariant,
                  ),
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  // ─── Community consensus ────────────────────────────────────────────────────

  Widget _buildObjectsSection() {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.category_outlined,
                  color: AppColors.primary, size: 18),
              const SizedBox(width: 8),
              Text(
                report.objects.length == 1
                    ? 'Reported Object'
                    : 'Reported Objects (${report.objects.length})',
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w700,
                  fontSize: 15,
                  color: AppColors.onSurface,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          for (int i = 0; i < report.objects.length; i++) ...[
            if (i > 0) const SizedBox(height: 12),
            _buildObjectCard(report.objects[i]),
          ],
        ],
      ),
    );
  }

  Widget _buildObjectCard(ReportObject obj) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  color: obj.objectType.color.withValues(alpha: 0.15),
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  obj.objectType.icon,
                  color: obj.objectType.color,
                  size: 18,
                ),
              ),
              const SizedBox(width: 10),
              Text(
                obj.objectType.label,
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w700,
                  fontSize: 14,
                  color: AppColors.onSurface,
                ),
              ),
            ],
          ),
          if (obj.issues.isNotEmpty) ...[
            const SizedBox(height: 12),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: obj.issues
                  .map((issue) => Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: AppColors.errorContainer,
                          borderRadius: BorderRadius.circular(999),
                        ),
                        child: Text(
                          issue.label,
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                            color: AppColors.onErrorContainer,
                          ),
                        ),
                      ))
                  .toList(),
            ),
          ],
          if (obj.measurements != null && obj.measurements!.isNotEmpty) ...[
            const SizedBox(height: 12),
            _buildMeasurementsBlock(obj),
          ],
          if (obj.warnings.isNotEmpty) ...[
            const SizedBox(height: 10),
            for (final w in obj.warnings)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.warning_amber_rounded,
                        size: 14, color: AppColors.warning),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        w.message,
                        style: TextStyle(
                          fontSize: 11,
                          color: AppColors.warning,
                          height: 1.4,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
          ],
        ],
      ),
    );
  }

  /// Renders an object's measurements. The create/edit forms write a
  /// JSON-encoded `{key: "value"}` blob into the backend's free-text
  /// `measurements` field; here we parse it, look up each key in
  /// [measurementSpecsFor], and render a labelled row with units and an
  /// accessibility verdict. Free-text strings (anything that isn't valid
  /// JSON) fall back to a single body line.
  Widget _buildMeasurementsBlock(ReportObject obj) {
    final raw = obj.measurements!;
    final parsed = _tryParseMeasurements(raw);
    if (parsed == null) {
      // Free-text fallback for older / non-JSON entries.
      return Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.straighten, size: 14, color: AppColors.primary),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                raw,
                style: TextStyle(
                  fontSize: 12,
                  color: AppColors.onSurfaceVariant,
                  height: 1.45,
                ),
              ),
            ),
          ],
        ),
      );
    }

    final specs = measurementSpecsFor(obj.objectType);
    // Maintain spec order; append any unknown keys at the end so nothing
    // entered by the user gets silently dropped.
    final knownKeys = specs.map((s) => s.key).toSet();
    final unknownKeys = parsed.keys.where((k) => !knownKeys.contains(k));
    final rows = <Widget>[
      for (final spec in specs)
        if (parsed.containsKey(spec.key))
          _buildMeasurementRow(spec: spec, rawValue: parsed[spec.key]!),
      for (final k in unknownKeys)
        _buildMeasurementRow(
          spec: MeasurementSpec(key: k, label: _humanizeKey(k), unit: ''),
          rawValue: parsed[k]!,
        ),
    ];

    if (rows.isEmpty) return const SizedBox.shrink();

    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(12),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.straighten, size: 14, color: AppColors.primary),
              const SizedBox(width: 8),
              Text(
                'MEASUREMENTS',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.2,
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          for (int i = 0; i < rows.length; i++) ...[
            if (i > 0)
              Divider(
                height: 12,
                thickness: 1,
                color: AppColors.outlineVariant.withValues(alpha: 0.25),
              ),
            rows[i],
          ],
        ],
      ),
    );
  }

  Widget _buildMeasurementRow({
    required MeasurementSpec spec,
    required String rawValue,
  }) {
    final parsedValue = double.tryParse(rawValue);
    final verdict =
        parsedValue == null ? null : spec.isAccessible(parsedValue);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Expanded(
              child: Text(
                spec.label,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: AppColors.onSurface,
                ),
              ),
            ),
            const SizedBox(width: 8),
            Container(
              padding: const EdgeInsets.symmetric(
                  horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainer,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                spec.unit.isEmpty ? rawValue : '$rawValue ${spec.unit}',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: AppColors.onSurface,
                  letterSpacing: 0.2,
                ),
              ),
            ),
          ],
        ),
        if (verdict != null) ...[
          const SizedBox(height: 4),
          _accessibilityVerdict(spec, verdict),
        ] else if (spec.accessibleMin != null || spec.accessibleMax != null) ...[
          const SizedBox(height: 4),
          _accessibilityHintText(_thresholdText(spec),
              tone: AppColors.onSurfaceVariant, icon: Icons.info_outline),
        ],
      ],
    );
  }

  Widget _accessibilityVerdict(MeasurementSpec spec, bool ok) {
    final color = ok ? AppColors.success : AppColors.error;
    final icon = ok ? Icons.check_circle : Icons.error_outline;
    final text = ok ? 'Accessible · ${_thresholdText(spec)}'
                    : 'Below accessible · needs ${_thresholdText(spec)}';
    return _accessibilityHintText(text, tone: color, icon: icon);
  }

  Widget _accessibilityHintText(String text,
      {required Color tone, required IconData icon}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 12, color: tone),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            text,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: tone,
              height: 1.35,
            ),
          ),
        ),
      ],
    );
  }

  String _thresholdText(MeasurementSpec s) {
    final unit = s.unit;
    if (s.accessibleMin != null && s.accessibleMax != null) {
      return '${_fmtNum(s.accessibleMin!)}–${_fmtNum(s.accessibleMax!)} $unit';
    }
    if (s.accessibleMin != null) return '≥ ${_fmtNum(s.accessibleMin!)} $unit';
    if (s.accessibleMax != null) return '≤ ${_fmtNum(s.accessibleMax!)} $unit';
    return '';
  }

  static String _fmtNum(double v) =>
      v == v.roundToDouble() ? v.toStringAsFixed(0) : v.toString();

  static String _humanizeKey(String key) => key
      .split('_')
      .map((w) => w.isEmpty ? w : '${w[0].toUpperCase()}${w.substring(1)}')
      .join(' ');

  /// Parses the JSON map written by the create form. Returns null when the
  /// blob isn't valid JSON or isn't a string-keyed map — those cases are
  /// rendered as free text.
  Map<String, String>? _tryParseMeasurements(String raw) {
    final trimmed = raw.trim();
    if (!trimmed.startsWith('{')) return null;
    try {
      final decoded = jsonDecode(trimmed);
      if (decoded is! Map) return null;
      return decoded.map((k, v) => MapEntry(k.toString(), v.toString()));
    } catch (_) {
      return null;
    }
  }

  Widget _buildCommunityConsensus() {
    final totalVotes = _agrees + _disagrees;
    final consensusPercent =
        totalVotes == 0 ? 0 : ((_agrees / totalVotes) * 100).round();

    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Community Consensus',
                    style: TextStyle(
                      fontFamily: 'Plus Jakarta Sans',
                      fontWeight: FontWeight.w700,
                      fontSize: 17,
                      color: AppColors.onSurface,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '$totalVotes ${totalVotes == 1 ? 'person has' : 'people have'} voted.',
                    style: TextStyle(
                      fontSize: 12,
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  _voteCount(Icons.thumb_up, _agrees, AppColors.primary),
                  const SizedBox(height: 4),
                  _voteCount(Icons.thumb_down, _disagrees, AppColors.errorStrong),
                ],
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: _voteButton(
                  icon: _myVote == 'agree'
                      ? Icons.thumb_up_rounded
                      : Icons.thumb_up_outlined,
                  label: 'Agree',
                  active: _myVote == 'agree',
                  loading: _voteLoading && _myVote != 'agree',
                  onTap: () => _vote('agree'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _voteButton(
                  icon: _myVote == 'disagree'
                      ? Icons.thumb_down_rounded
                      : Icons.thumb_down_outlined,
                  label: 'Disagree',
                  active: _myVote == 'disagree',
                  activeColor: AppColors.errorStrong,
                  activeTextColor: AppColors.errorContainer,
                  loading: _voteLoading && _myVote != 'disagree',
                  onTap: () => _vote('disagree'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'VALIDATION PROGRESS',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.0,
                  color: AppColors.onSurfaceVariant,
                ),
              ),
              Text(
                '$consensusPercent% Agree',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Container(
            height: 10,
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerHigh,
              borderRadius: BorderRadius.circular(999),
            ),
            child: LayoutBuilder(
              builder: (context, constraints) => Align(
                alignment: Alignment.centerLeft,
                child: Container(
                  width: constraints.maxWidth *
                      (consensusPercent / 100).clamp(0.0, 1.0),
                  height: 10,
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      colors: [AppColors.primaryAccent, AppColors.primary],
                    ),
                    borderRadius: BorderRadius.circular(999),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _voteCount(IconData icon, int count, Color color) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 13, color: color),
        const SizedBox(width: 4),
        Text(
          '$count',
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w700,
            color: color,
          ),
        ),
      ],
    );
  }

  Widget _voteButton({
    required IconData icon,
    required String label,
    required bool active,
    required bool loading,
    required VoidCallback onTap,
    Color? activeColor,
    Color? activeTextColor,
  }) {
    activeColor ??= AppColors.primary;
    activeTextColor ??= AppColors.onPrimary;
    return GestureDetector(
      onTap: loading ? null : onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(
          color: active ? activeColor : AppColors.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(999),
          boxShadow: active
              ? [
                  BoxShadow(
                    color: activeColor.withOpacity(0.28),
                    blurRadius: 12,
                    offset: const Offset(0, 4),
                  ),
                ]
              : null,
        ),
        child: loading
            ? Center(
                child: SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: active ? activeTextColor : AppColors.onSurface,
                  ),
                ),
              )
            : Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    icon,
                    color: active ? activeTextColor : AppColors.onSurface,
                    size: 16,
                  ),
                  const SizedBox(width: 8),
                  Text(
                    label,
                    style: TextStyle(
                      fontWeight: FontWeight.w700,
                      color: active ? activeTextColor : AppColors.onSurface,
                      fontSize: 14,
                    ),
                  ),
                ],
              ),
      ),
    );
  }

  // ─── Metadata row ───────────────────────────────────────────────────────────

  Widget _buildMetadataRow() {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Report Info',
            style: TextStyle(
              fontFamily: 'Plus Jakarta Sans',
              fontWeight: FontWeight.w700,
              fontSize: 15,
              color: AppColors.onSurface,
            ),
          ),
          const SizedBox(height: 12),
          _infoRow(Icons.tag, 'Report ID', '#${report.reportId}'),
          _infoRow(Icons.category_outlined, 'Category', report.headline),
          _infoRow(Icons.circle_outlined, 'Status', _currentStatus.label),
          _infoRow(
            Icons.schedule_outlined,
            'Reported',
            report.publishDate.isNotEmpty
                ? report.publishDate.replaceFirst('T', ' ').substring(0, 16)
                : 'Unknown',
          ),
        ],
      ),
    );
  }

  Widget _infoRow(IconData icon, String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        children: [
          Icon(icon, size: 16, color: AppColors.onSurfaceVariant),
          const SizedBox(width: 10),
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              color: AppColors.onSurfaceVariant,
            ),
          ),
          const Spacer(),
          Text(
            value,
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: AppColors.onSurface,
            ),
          ),
        ],
      ),
    );
  }

  // ─── Comments section ───────────────────────────────────────────────────────

  Widget _buildCommentsSection() {
    final auth = context.watch<AuthService>();

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.chat_bubble_outline, color: AppColors.primary, size: 18),
              const SizedBox(width: 8),
              Text(
                'Comments (${_comments.length})',
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w700,
                  fontSize: 15,
                  color: AppColors.onSurface,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),

          // Comment list
          if (_commentsLoading)
            Center(
              child: Padding(
                padding: EdgeInsets.symmetric(vertical: 16),
                child: CircularProgressIndicator(color: AppColors.primary, strokeWidth: 2),
              ),
            )
          else if (_commentsError != null)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Row(
                children: [
                  Icon(Icons.wifi_off, size: 14, color: AppColors.outline),
                  const SizedBox(width: 8),
                  Text(
                    _commentsError!,
                    style: TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant),
                  ),
                  const Spacer(),
                  GestureDetector(
                    onTap: _loadComments,
                    child: Text(
                      'Retry',
                      style: TextStyle(fontSize: 12, color: AppColors.primary, fontWeight: FontWeight.w600),
                    ),
                  ),
                ],
              ),
            )
          else if (_comments.isEmpty)
            Padding(
              padding: EdgeInsets.symmetric(vertical: 8),
              child: Text(
                'No comments yet. Be the first to comment!',
                style: TextStyle(fontSize: 13, color: AppColors.onSurfaceVariant),
              ),
            )
          else
            ..._comments.map(_buildCommentItem),

          // Input field (only for authenticated users)
          if (auth.isAuthenticated) ...[
            const SizedBox(height: 14),
            const Divider(height: 1),
            const SizedBox(height: 14),
            Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Expanded(
                  child: TextField(
                    controller: _commentController,
                    maxLines: 3,
                    minLines: 1,
                    style: TextStyle(fontSize: 13, color: AppColors.onSurface),
                    decoration: InputDecoration(
                      hintText: 'Add a comment…',
                      hintStyle: TextStyle(
                        fontSize: 13,
                        color: AppColors.onSurfaceVariant,
                      ),
                      filled: true,
                      fillColor: AppColors.surfaceContainerHigh,
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 14,
                        vertical: 10,
                      ),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(14),
                        borderSide: BorderSide.none,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                GestureDetector(
                  onTap: _commentSubmitting ? null : _submitComment,
                  child: Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: AppColors.primary,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: _commentSubmitting
                        ? Center(
                            child: SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: AppColors.onPrimary,
                              ),
                            ),
                          )
                        : Icon(Icons.send_rounded, color: AppColors.onPrimary, size: 18),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildCommentItem(_CommentData comment) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerHigh,
              shape: BoxShape.circle,
            ),
            child: Icon(Icons.person_outline, size: 16, color: AppColors.secondary),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      comment.username,
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: AppColors.onSurface,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      comment.timeAgo,
                      style: TextStyle(
                        fontSize: 11,
                        color: AppColors.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 3),
                Text(
                  comment.text,
                  style: TextStyle(
                    fontSize: 13,
                    color: AppColors.onSurfaceVariant,
                    height: 1.4,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ─── Follow button ──────────────────────────────────────────────────────────

  Widget _buildFollowButton() {
    final isFollowing = _isFollowing ?? false;
    final loading = _followBusy;
    // Following → outlined "Following" affordance to signal it's an unfollow
    // toggle. Not-following (or unknown) → filled primary CTA.
    final filled = !isFollowing;
    final bg = filled ? AppColors.primary : AppColors.surfaceContainerLowest;
    final fg = filled ? AppColors.onPrimary : AppColors.primary;
    return GestureDetector(
      onTap: loading ? null : _toggleFollow,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        width: double.infinity,
        padding: const EdgeInsets.symmetric(vertical: 16),
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(16),
          border: filled
              ? null
              : Border.all(color: AppColors.primary, width: 2),
          boxShadow: filled
              ? [
                  BoxShadow(
                    color: AppColors.primary.withValues(alpha: 0.22),
                    blurRadius: 18,
                    offset: const Offset(0, 6),
                  ),
                ]
              : null,
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (loading)
              SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: fg,
                ),
              )
            else
              Icon(
                isFollowing
                    ? Icons.notifications_active
                    : Icons.notifications_active_outlined,
                color: fg,
                size: 20,
              ),
            const SizedBox(width: 10),
            Text(
              isFollowing ? 'Following' : 'Follow Updates',
              style: TextStyle(
                fontFamily: 'Plus Jakarta Sans',
                fontWeight: FontWeight.w700,
                fontSize: 16,
                color: fg,
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ─── Bottom nav ─────────────────────────────────────────────────────────────

  Widget _buildBottomNav(BuildContext context) {
    return Hero(
      tag: 'app_bottom_nav',
      flightShuttleBuilder: (ctx, anim, dir, from, toCtx) =>
          (toCtx.widget as Hero).child,
      child: Material(
        type: MaterialType.transparency,
        child: _buildNavContent(context),
      ),
    );
  }

  Widget _buildNavContent(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
      decoration: BoxDecoration(
        color: AppColors.surface.withValues(alpha: 0.88),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.06),
            blurRadius: 32,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _navItem(Icons.map, Icons.map_outlined, 'Home', false,
              () => Navigator.popUntil(context, (r) => r.isFirst)),
          _navItem(Icons.assignment, Icons.assignment_outlined, 'Reports', true, () {}),
          _navItem(Icons.person, Icons.person_outline, 'Profile', false,
              () => Navigator.pushAndRemoveUntil(
                    context,
                    MaterialPageRoute(builder: (_) => const MainShell(initialTab: 2)),
                    (r) => false,
                  )),
        ],
      ),
    );
  }

  Widget _navItem(
    IconData activeIcon,
    IconData inactiveIcon,
    String label,
    bool active,
    VoidCallback onTap,
  ) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 8),
        decoration: BoxDecoration(
          color: active ? AppColors.primary : Colors.transparent,
          borderRadius: BorderRadius.circular(999),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              active ? activeIcon : inactiveIcon,
              color: active ? AppColors.onPrimarySolid : AppColors.secondary,
              size: 22,
            ),
            const SizedBox(height: 3),
            Text(
              label,
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.8,
                color: active ? AppColors.onPrimarySolid : AppColors.secondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Comment data model ────────────────────────────────────────────────────────

class _CommentData {
  final int id;
  final String username;
  final String text;
  final String createdAt;

  const _CommentData({
    required this.id,
    required this.username,
    required this.text,
    required this.createdAt,
  });

  factory _CommentData.fromJson(Map<String, dynamic> json) {
    // Author may arrive as a nested map, a bare numeric id, or be missing
    // entirely depending on backend version — accept any of those.
    String resolveUsername() {
      final raw = json['author'];
      if (raw is Map) {
        final name = raw['name'];
        if (name is String && name.trim().isNotEmpty) return name;
        final id = raw['id'];
        if (id != null) return 'User #$id';
      } else if (raw is num) {
        return 'User #${raw.toInt()}';
      }
      return 'User';
    }

    return _CommentData(
      id: (json['id'] as num?)?.toInt() ?? 0,
      username: resolveUsername(),
      text: (json['content'] as String?) ?? '',
      createdAt: (json['createdAt'] as String?) ?? '',
    );
  }

  String get timeAgo {
    try {
      final dt = DateTime.parse(
        createdAt.endsWith('Z') ? createdAt : '${createdAt}Z',
      ).toLocal();
      final diff = DateTime.now().difference(dt);
      if (diff.inMinutes < 1) return 'just now';
      if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
      if (diff.inHours < 24) return '${diff.inHours}h ago';
      if (diff.inDays < 30) return '${diff.inDays}d ago';
      return '${dt.day}/${dt.month}/${dt.year}';
    } catch (_) {
      return '';
    }
  }
}

// ─── Painters ──────────────────────────────────────────────────────────────────

class _ImagePlaceholderPainter extends CustomPainter {
  final Color color;
  _ImagePlaceholderPainter({required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(
      Rect.fromLTWH(0, 0, size.width, size.height),
      Paint()..color = color.withOpacity(0.08),
    );
    final line = Paint()
      ..color = color.withOpacity(0.06)
      ..strokeWidth = 1;
    for (double y = 0; y < size.height; y += 24) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), line);
    }
    canvas.drawRect(
      Rect.fromLTWH(0, 0, size.width, size.height),
      Paint()
        ..shader = LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [color.withOpacity(0.12), color.withOpacity(0.04)],
        ).createShader(Rect.fromLTWH(0, 0, size.width, size.height)),
    );
  }

  @override
  bool shouldRepaint(covariant _ImagePlaceholderPainter old) =>
      old.color != color;
}

// ─── Fullscreen media page ────────────────────────────────────────────────────

class _FullscreenMediaPage extends StatelessWidget {
  final String? imageUrl;
  final VideoPlayerController? videoController;

  const _FullscreenMediaPage({this.imageUrl, this.videoController});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          Center(
            child: imageUrl != null
                ? InteractiveViewer(
                    minScale: 1,
                    maxScale: 4,
                    child: Image.network(
                      imageUrl!,
                      fit: BoxFit.contain,
                      errorBuilder: (_, __, ___) => const Icon(
                        Icons.broken_image_outlined,
                        color: Colors.white54,
                        size: 64,
                      ),
                    ),
                  )
                : videoController != null
                    ? AspectRatio(
                        aspectRatio: videoController!.value.aspectRatio,
                        child: VideoPlayer(videoController!),
                      )
                    : const SizedBox.shrink(),
          ),
          // Close button
          SafeArea(
            child: Align(
              alignment: Alignment.topLeft,
              child: Padding(
                padding: const EdgeInsets.all(8),
                child: IconButton(
                  icon: const Icon(Icons.close, color: Colors.white, size: 28),
                  onPressed: () => Navigator.pop(context),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
