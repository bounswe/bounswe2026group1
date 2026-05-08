import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:provider/provider.dart';
import 'package:video_player/video_player.dart';
import '../theme/app_colors.dart';
import '../models/report_model.dart';
import '../models/sse_event.dart';
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

  Future<void> _openEdit() async {
    final updated = await Navigator.push<ReportModel>(
      context,
      MaterialPageRoute(
        builder: (_) => EditReportScreen(report: report),
        fullscreenDialog: true,
      ),
    );
    if (!mounted || updated == null) return;
    setState(() => _report = updated);
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
      final raw = await context.read<AuthService>().api.getComments(report.reportId);
      if (!mounted) return;
      setState(() {
        _comments = raw.map(_CommentData.fromJson).toList();
        _commentsLoading = false;
      });
    } catch (_) {
      if (mounted) setState(() {
        _commentsLoading = false;
        _commentsError = 'Could not load comments.';
      });
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
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Failed to post comment.')),
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
            const SizedBox(height: 10),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.straighten,
                    size: 14, color: AppColors.onSurfaceVariant),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    obj.measurements!,
                    style: TextStyle(
                      fontSize: 12,
                      color: AppColors.onSurfaceVariant,
                      height: 1.4,
                    ),
                  ),
                ),
              ],
            ),
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
    return GestureDetector(
      onTap: () {},
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(vertical: 16),
        decoration: BoxDecoration(
          color: AppColors.primary,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: AppColors.primary.withValues(alpha: 0.22),
              blurRadius: 18,
              offset: const Offset(0, 6),
            ),
          ],
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.notifications_active_outlined, color: AppColors.onPrimary, size: 20),
            SizedBox(width: 10),
            Text(
              'Follow Updates',
              style: TextStyle(
                fontFamily: 'Plus Jakarta Sans',
                fontWeight: FontWeight.w700,
                fontSize: 16,
                color: AppColors.onPrimary,
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
    final author = json['author'] as Map<String, dynamic>?;
    return _CommentData(
      id: (json['id'] as num?)?.toInt() ?? 0,
      username: author?['name'] as String? ??
          'User #${author?['id'] ?? '?'}',
      text: json['content'] as String? ?? '',
      createdAt: json['createdAt'] as String? ?? '',
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
