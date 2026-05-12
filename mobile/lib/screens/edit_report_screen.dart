import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:image_picker/image_picker.dart';
import 'package:latlong2/latlong.dart';
import 'package:provider/provider.dart';
import 'package:video_compress/video_compress.dart';

import '../models/report_model.dart';
import '../services/api_service.dart';
import '../services/auth_service.dart';
import '../theme/app_colors.dart';
import '../widgets/objects_section.dart';
import 'report_location_picker_screen.dart';

/// Result returned from [EditReportScreen]. The detail screen below uses
/// it to either swap in the new model (save) or pop itself out of the way
/// (delete) — keeping both pops on a single, serial Navigator chain so no
/// pop animations overlap.
sealed class EditReportOutcome {
  const EditReportOutcome();
}

class EditReportUpdated extends EditReportOutcome {
  final ReportModel report;
  const EditReportUpdated(this.report);
}

class EditReportDeleted extends EditReportOutcome {
  const EditReportDeleted();
}

/// Author-only edit form. Pre-populates from [report] and saves via
/// PUT /api/reports/{id}. Pops with an [EditReportOutcome] describing what
/// happened — `null` means the user backed out without changes.
class EditReportScreen extends StatefulWidget {
  final ReportModel report;

  const EditReportScreen({super.key, required this.report});

  @override
  State<EditReportScreen> createState() => _EditReportScreenState();
}

class _EditReportScreenState extends State<EditReportScreen> {
  // Server caps the report at 5 total media items. Enforce locally so the
  // user sees "limit reached" feedback before hitting submit.
  static const int _maxMediaItems = 5;

  late final TextEditingController _descController;
  late final MapController _mapController;

  late ReportEnvironment _environment;
  late LatLng _pin;
  late final List<ObjectDraft> _objects;

  /// Existing media as reported by the backend, paired (id, url).
  late final List<_ExistingMedia> _existingMedia;
  // Backend ids the user has toggled off; cleared if they toggle back on
  // before saving (the toggle is purely local until _save).
  final Set<int> _removedMediaIds = {};
  // Locally-picked files queued for upload after the report PUT succeeds.
  final List<_NewMedia> _newMedia = [];
  final ImagePicker _picker = ImagePicker();
  bool _converting = false;

  bool _saving = false;
  bool _deleting = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final r = widget.report;
    _descController = TextEditingController(text: r.description);
    _environment = r.environment;
    _pin = LatLng(r.latitude, r.longitude);
    _mapController = MapController();
    // Hydrate one draft per saved object — JSON measurements are decoded
    // back into the per-spec map by ObjectDraft.fromReportObject.
    _objects = [
      for (int i = 0; i < r.objects.length; i++)
        ObjectDraft.fromReportObject(r.objects[i], id: 'edit_obj_$i'),
    ];
    // Pair mediaIds with mediaUrls. The lists are parallel server-side; if
    // they ever drift (older responses) we simply skip the trailing entries
    // rather than crash — the user can always re-upload.
    final ids = r.mediaIds;
    final urls = r.mediaUrls;
    _existingMedia = [
      for (int i = 0; i < ids.length && i < urls.length; i++)
        _ExistingMedia(id: ids[i], url: urls[i]),
    ];
  }

  @override
  void dispose() {
    _descController.dispose();
    _mapController.dispose();
    super.dispose();
  }

  bool get _isFeature => widget.report.reportType == ReportType.feature;

  /// Mirrors the make-report flow: when the env flips, drop the type+
  /// issues+measurements on cards whose ObjectType doesn't support the
  /// new environment so the form can't submit invalid combos.
  void _onEnvironmentChanged(ReportEnvironment next) {
    setState(() {
      _environment = next;
      for (final o in _objects) {
        final type = o.objectType;
        if (type != null && !type.supports(next)) {
          o.objectType = null;
          o.issues.clear();
          o.measurements.clear();
        }
      }
    });
  }

  Future<void> _save() async {
    final desc = _descController.text.trim();
    if (desc.length < 10) {
      setState(() => _error = 'Description must be at least 10 characters.');
      return;
    }
    if (_objects.isEmpty) {
      setState(
          () => _error = 'Add at least one object to describe the report.');
      return;
    }
    for (final o in _objects) {
      if (o.objectType == null) {
        setState(() => _error = 'Select a type for every object card.');
        return;
      }
      if (!_isFeature && o.issues.isEmpty) {
        setState(() => _error =
            'Pick at least one issue for the "${o.objectType!.label}" object.');
        return;
      }
    }

    setState(() {
      _saving = true;
      _error = null;
    });

    try {
      final auth = context.read<AuthService>();
      final reportObjects = _objects.map((o) {
        // Same JSON-encoded measurements shape the create form writes — keeps
        // the backend's free-text field consistent across clients.
        final filled = <String, num>{};
        for (final e in o.measurements.entries) {
          final v = num.tryParse(e.value.trim());
          if (v != null) filled[e.key] = v;
        }
        return ReportObject(
          objectType: o.objectType!,
          issues: o.issues.toList(),
          measurements: filled.isEmpty ? null : jsonEncode(filled),
        );
      }).toList();

      ReportModel updated = await auth.api.updateReport(
        reportId: widget.report.reportId,
        description: desc,
        environment: _environment,
        latitude: _pin.latitude,
        longitude: _pin.longitude,
        objects: reportObjects,
        mediaIdsToRemove:
            _removedMediaIds.isEmpty ? null : _removedMediaIds.toList(),
      );

      // Upload any newly-picked files after the report update succeeds —
      // if this throws, the detach already happened, but the worst case is
      // a partial change the user can retry, not a corrupt report.
      final readyFiles = [
        for (final m in _newMedia)
          if (!m.converting) m.file,
      ];
      if (readyFiles.isNotEmpty) {
        try {
          await auth.api.uploadMediaFiles(updated.reportId, readyFiles);
          // Refresh so the popped model reflects the freshly-attached media.
          updated = await auth.api.getReport(updated.reportId);
        } catch (e) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text('Report updated, but media upload failed: $e'),
                duration: const Duration(seconds: 6),
              ),
            );
          }
        }
      }

      if (!mounted) return;
      Navigator.pop(context, EditReportUpdated(updated));
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _saving = false;
          _error = e.userMessage;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _saving = false;
          _error = 'Could not save changes. Try again.';
        });
      }
    }
  }

  Future<void> _confirmAndDelete() async {
    if (_deleting || _saving) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surfaceContainerLowest,
        shape:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        icon: Icon(
          Icons.delete_forever_outlined,
          color: AppColors.error,
          size: 32,
        ),
        title: Text(
          'Delete this report?',
          style: TextStyle(
            fontFamily: 'Plus Jakarta Sans',
            fontWeight: FontWeight.w800,
            fontSize: 18,
            color: AppColors.onSurface,
          ),
        ),
        content: Text(
          'This permanently removes the report along with its objects, '
          'media, and comments. This action can\'t be undone.',
          style: TextStyle(
            fontSize: 13,
            color: AppColors.onSurfaceVariant,
            height: 1.45,
          ),
        ),
        actionsPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(
              'Cancel',
              style: TextStyle(
                color: AppColors.onSurfaceVariant,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(
              backgroundColor: AppColors.error,
              foregroundColor: Colors.white,
            ),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() {
      _deleting = true;
      _error = null;
    });

    try {
      await context.read<AuthService>().api.deleteReport(widget.report.reportId);
      if (!mounted) return;
      // Pop only this screen — the detail screen below reads the
      // EditReportDeleted result and pops itself, keeping the two pops
      // serial instead of stacking two simultaneous exit animations.
      Navigator.pop(context, const EditReportDeleted());
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _deleting = false;
        _error = 'Could not delete — ${e.userMessage}';
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _deleting = false;
        _error = 'Could not delete report. Try again.';
      });
    }
  }

  int get _totalMediaCount =>
      _existingMedia.where((m) => !_removedMediaIds.contains(m.id)).length +
      _newMedia.length;

  bool get _canAddMoreMedia => _totalMediaCount < _maxMediaItems;

  void _toastMediaLimitReached() {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('You can attach up to $_maxMediaItems files per report.'),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }

  Future<void> _pickImage(ImageSource source) async {
    if (!_canAddMoreMedia) {
      _toastMediaLimitReached();
      return;
    }
    try {
      final picked = await _picker.pickImage(
        source: source,
        imageQuality: 80,
        maxWidth: 1080,
      );
      if (picked == null) return;
      setState(() {
        _newMedia.add(_NewMedia(file: File(picked.path), isVideo: false));
      });
    } catch (_) {}
  }

  Future<void> _pickVideo(ImageSource source) async {
    if (!_canAddMoreMedia) {
      _toastMediaLimitReached();
      return;
    }
    try {
      final picked = await _picker.pickVideo(source: source);
      if (picked == null) return;

      // Same convert-in-place pattern as the make-report screen: hold the
      // slot in the gallery while VideoCompress runs so the layout doesn't
      // jump when the file swaps to the compressed copy.
      final pending = _NewMedia(
        file: File(picked.path),
        isVideo: true,
        converting: true,
      );
      setState(() {
        _newMedia.add(pending);
        _converting = true;
      });

      try {
        final info = await VideoCompress.compressVideo(
          picked.path,
          quality: VideoQuality.MediumQuality,
          deleteOrigin: false,
          includeAudio: true,
        );
        if (!mounted) return;
        setState(() {
          if (info?.file != null) pending.file = info!.file!;
          pending.converting = false;
        });
      } catch (_) {
        if (mounted) setState(() => pending.converting = false);
      } finally {
        if (mounted) {
          setState(() {
            _converting = _newMedia.any((m) => m.converting);
          });
        }
      }
    } catch (_) {
      if (mounted) setState(() => _converting = false);
    }
  }

  /// Pushes the same full-screen picker the create flow uses (search +
  /// tap-to-drop), so the user has a single mental model for choosing a
  /// report's pin no matter whether they're creating or editing.
  Future<void> _openFullScreenPicker() async {
    final result = await Navigator.of(context).push<LatLng>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => ReportLocationPickerScreen(initial: _pin),
      ),
    );
    if (result != null && mounted) {
      setState(() => _pin = result);
      _mapController.move(result, 16);
    }
  }

  void _showAddMediaSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => Container(
        margin: const EdgeInsets.fromLTRB(16, 0, 16, 32),
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(24),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(height: 12),
            Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: AppColors.outlineVariant,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(height: 16),
            _sheetTile(
              icon: Icons.camera_alt_outlined,
              label: 'Take Photo',
              onTap: () {
                Navigator.pop(context);
                _pickImage(ImageSource.camera);
              },
            ),
            _sheetTile(
              icon: Icons.photo_library_outlined,
              label: 'Choose Photo from Gallery',
              onTap: () {
                Navigator.pop(context);
                _pickImage(ImageSource.gallery);
              },
            ),
            _sheetTile(
              icon: Icons.videocam_outlined,
              label: 'Record Video',
              onTap: () {
                Navigator.pop(context);
                _pickVideo(ImageSource.camera);
              },
            ),
            _sheetTile(
              icon: Icons.video_library_outlined,
              label: 'Choose Video from Gallery',
              onTap: () {
                Navigator.pop(context);
                _pickVideo(ImageSource.gallery);
              },
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  Widget _sheetTile({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return ListTile(
      leading: Icon(icon, color: AppColors.primary),
      title: Text(
        label,
        style: TextStyle(
          color: AppColors.onSurface,
          fontWeight: FontWeight.w600,
          fontSize: 14,
        ),
      ),
      onTap: onTap,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      body: Column(
        children: [
          _buildTopBar(),
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildLocationCard(),
                  const SizedBox(height: 20),
                  _buildDescriptionField(),
                  const SizedBox(height: 20),
                  _buildMediaSection(),
                  const SizedBox(height: 20),
                  _buildEnvironmentSelector(),
                  const SizedBox(height: 20),
                  ObjectsSection(
                    objects: _objects,
                    reportType: widget.report.reportType,
                    environment: _environment,
                    onChanged: () => setState(() {}),
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 16),
                    _buildErrorBanner(),
                  ],
                  const SizedBox(height: 28),
                  _buildDeleteButton(),
                  const SizedBox(height: 120),
                ],
              ),
            ),
          ),
        ],
      ),
      bottomSheet: _buildBottomBar(),
    );
  }

  Widget _buildMediaSection() {
    final visibleExisting = [
      for (final m in _existingMedia) m,
    ];
    final hasAny = visibleExisting.isNotEmpty || _newMedia.isNotEmpty;
    return _section(
      label: 'MEDIA',
      hint: '$_totalMediaCount/$_maxMediaItems · tap × to mark for removal',
      child: SizedBox(
        height: 132,
        child: ListView.separated(
          scrollDirection: Axis.horizontal,
          itemCount:
              visibleExisting.length + _newMedia.length + (_canAddMoreMedia || !hasAny ? 1 : 0),
          separatorBuilder: (_, __) => const SizedBox(width: 10),
          itemBuilder: (_, i) {
            if (i < visibleExisting.length) {
              final media = visibleExisting[i];
              final markedForRemoval = _removedMediaIds.contains(media.id);
              return _buildExistingMediaTile(media, markedForRemoval);
            }
            final newIndex = i - visibleExisting.length;
            if (newIndex < _newMedia.length) {
              return _buildNewMediaTile(_newMedia[newIndex], newIndex);
            }
            return _buildAddMoreTile();
          },
        ),
      ),
    );
  }

  Widget _buildExistingMediaTile(_ExistingMedia media, bool removed) {
    return SizedBox(
      width: 132,
      height: 132,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: Stack(
          fit: StackFit.expand,
          children: [
            ColorFiltered(
              colorFilter: removed
                  ? ColorFilter.mode(
                      Colors.black.withValues(alpha: 0.55),
                      BlendMode.darken,
                    )
                  : const ColorFilter.mode(
                      Colors.transparent, BlendMode.dst),
              child: _isVideoUrl(media.url)
                  ? Container(
                      color: Colors.black87,
                      alignment: Alignment.center,
                      child: const Icon(
                        Icons.play_circle_fill,
                        color: Colors.white,
                        size: 36,
                      ),
                    )
                  : Image.network(
                      media.url,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => Container(
                        color: AppColors.surfaceContainerHigh,
                        alignment: Alignment.center,
                        child: Icon(Icons.broken_image,
                            color: AppColors.outlineVariant),
                      ),
                    ),
            ),
            if (removed)
              const Center(
                child: Text(
                  'Will be\nremoved',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w700,
                    fontSize: 12,
                  ),
                ),
              ),
            Positioned(
              right: 6,
              top: 6,
              child: GestureDetector(
                onTap: () => setState(() {
                  if (removed) {
                    _removedMediaIds.remove(media.id);
                  } else {
                    _removedMediaIds.add(media.id);
                  }
                }),
                child: Container(
                  width: 28,
                  height: 28,
                  decoration: BoxDecoration(
                    color: removed ? AppColors.primary : Colors.white,
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    removed ? Icons.undo : Icons.close,
                    color: removed ? Colors.white : AppColors.errorStrong,
                    size: 16,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNewMediaTile(_NewMedia media, int index) {
    return SizedBox(
      width: 132,
      height: 132,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (media.isVideo)
              Container(
                color: Colors.black87,
                child: Center(
                  child: media.converting
                      ? const SizedBox(
                          width: 22,
                          height: 22,
                          child: CircularProgressIndicator(
                            color: Colors.white,
                            strokeWidth: 2.5,
                          ),
                        )
                      : const Icon(
                          Icons.play_circle_fill,
                          color: Colors.white,
                          size: 36,
                        ),
                ),
              )
            else
              Image.file(media.file, fit: BoxFit.cover),
            Positioned(
              left: 8,
              top: 8,
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.primary,
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Text(
                  'NEW',
                  style: TextStyle(
                    fontSize: 9,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 0.6,
                    color: AppColors.onPrimary,
                  ),
                ),
              ),
            ),
            Positioned(
              right: 6,
              top: 6,
              child: GestureDetector(
                onTap: media.converting
                    ? null
                    : () => setState(() => _newMedia.removeAt(index)),
                child: Container(
                  width: 28,
                  height: 28,
                  decoration: const BoxDecoration(
                    color: Colors.white,
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    Icons.close,
                    color: AppColors.errorStrong,
                    size: 16,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAddMoreTile() {
    return GestureDetector(
      onTap: _converting ? null : _showAddMediaSheet,
      child: Container(
        width: 132,
        height: 132,
        decoration: BoxDecoration(
          color: AppColors.surfaceContainer,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.outlineVariant),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.add_a_photo_outlined,
                color: AppColors.primary, size: 26),
            const SizedBox(height: 6),
            Text(
              _totalMediaCount == 0 ? 'Add media' : 'Add more',
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w600,
                color: AppColors.onSurface,
              ),
            ),
          ],
        ),
      ),
    );
  }

  bool _isVideoUrl(String url) {
    final path = (Uri.tryParse(url)?.path ?? url).toLowerCase();
    return path.endsWith('.mp4') ||
        path.endsWith('.mov') ||
        path.endsWith('.webm') ||
        path.endsWith('.m4v');
  }

  Widget _buildTopBar() {
    return SafeArea(
      bottom: false,
      child: Container(
        color: AppColors.surface.withValues(alpha: 0.92),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        child: Row(
          children: [
            IconButton(
              icon: Icon(Icons.close, color: AppColors.primary),
              onPressed: _saving ? null : () => Navigator.pop(context),
            ),
            const SizedBox(width: 4),
            Text(
              'Edit Report',
              style: TextStyle(
                fontFamily: 'Plus Jakarta Sans',
                fontWeight: FontWeight.w800,
                fontSize: 22,
                color: AppColors.primary,
                letterSpacing: -0.5,
              ),
            ),
            const Spacer(),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainer,
                borderRadius: BorderRadius.circular(999),
              ),
              child: Text(
                '#${widget.report.reportId}',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: AppColors.secondary,
                ),
              ),
            ),
            const SizedBox(width: 8),
          ],
        ),
      ),
    );
  }

  Widget _buildLocationCard() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(20),
      ),
      clipBehavior: Clip.hardEdge,
      child: Column(
        children: [
          SizedBox(
            height: 200,
            width: double.infinity,
            child: Stack(
              children: [
                FlutterMap(
                  mapController: _mapController,
                  options: MapOptions(
                    initialCenter: _pin,
                    initialZoom: 16,
                    onTap: (_, latLng) => setState(() => _pin = latLng),
                  ),
                  children: [
                    TileLayer(
                      urlTemplate:
                          'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                      userAgentPackageName: 'com.bounswe2026group1.mapcess',
                    ),
                    MarkerLayer(
                      markers: [
                        Marker(
                          point: _pin,
                          child: Icon(Icons.location_on,
                              color: AppColors.primary, size: 36),
                        ),
                      ],
                    ),
                  ],
                ),
                // Zoom/expand button — opens the shared fullscreen picker so
                // the user can fine-tune the pin on a bigger canvas (and
                // search by address) without leaving the edit flow.
                Positioned(
                  top: 10,
                  right: 10,
                  child: GestureDetector(
                    onTap: _openFullScreenPicker,
                    child: Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppColors.cardSurface,
                        borderRadius: BorderRadius.circular(10),
                        boxShadow: [
                          BoxShadow(
                            color: AppColors.shadow,
                            blurRadius: 8,
                            offset: const Offset(0, 2),
                          ),
                        ],
                      ),
                      child: Icon(
                        Icons.fullscreen,
                        color: AppColors.primary,
                        size: 22,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(14),
            child: Row(
              children: [
                Icon(Icons.place_outlined,
                    size: 16, color: AppColors.onSurfaceVariant),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    '${_pin.latitude.toStringAsFixed(5)}, ${_pin.longitude.toStringAsFixed(5)}',
                    style: TextStyle(
                      fontSize: 12,
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                ),
                Text(
                  'Tap map to move',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    color: AppColors.primary,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDescriptionField() {
    return _section(
      label: 'DESCRIPTION',
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.surfaceContainerHigh),
        ),
        child: TextField(
          controller: _descController,
          maxLines: 4,
          style: TextStyle(fontSize: 14, color: AppColors.onSurface),
          decoration: InputDecoration(
            hintText: 'Describe the issue clearly…',
            hintStyle: TextStyle(color: AppColors.outlineVariant, fontSize: 13),
            border: InputBorder.none,
            contentPadding:
                const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          ),
        ),
      ),
    );
  }

  Widget _buildEnvironmentSelector() {
    const items = [
      _EnvOption(
        env: ReportEnvironment.outdoor,
        icon: Icons.wb_sunny_outlined,
        label: 'Outdoor',
      ),
      _EnvOption(
        env: ReportEnvironment.indoor,
        icon: Icons.home_outlined,
        label: 'Indoor',
      ),
    ];
    return _section(
      label: 'ENVIRONMENT',
      child: Row(
        children: [
          for (int i = 0; i < items.length; i++) ...[
            if (i > 0) const SizedBox(width: 8),
            Expanded(
              child: _envButton(
                option: items[i],
                selected: _environment == items[i].env,
                onTap: () => _onEnvironmentChanged(items[i].env),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _envButton({
    required _EnvOption option,
    required bool selected,
    required VoidCallback onTap,
  }) {
    final fg = selected ? AppColors.primary : AppColors.onSurfaceVariant;
    final borderColor =
        selected ? AppColors.primary : AppColors.outlineVariant;
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(
          color: selected
              ? AppColors.primary.withValues(alpha: 0.07)
              : AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: borderColor, width: 2),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(option.icon, size: 16, color: fg),
            const SizedBox(width: 8),
            Text(
              option.label,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w700,
                color: fg,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildErrorBanner() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.errorContainer,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.errorContainerBorder),
      ),
      child: Row(
        children: [
          Icon(Icons.error_outline,
              color: AppColors.onErrorContainer, size: 18),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              _error!,
              style: TextStyle(
                fontSize: 13,
                color: AppColors.onErrorContainer,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDeleteButton() {
    final disabled = _saving || _deleting;
    return Opacity(
      opacity: disabled && _saving ? 0.6 : 1,
      child: GestureDetector(
        onTap: disabled ? null : _confirmAndDelete,
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(vertical: 14),
          decoration: BoxDecoration(
            color: AppColors.errorContainer,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppColors.errorContainerBorder),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (_deleting)
                SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: AppColors.error,
                  ),
                )
              else
                Icon(Icons.delete_outline,
                    color: AppColors.error, size: 20),
              const SizedBox(width: 10),
              Text(
                _deleting ? 'Deleting…' : 'Delete Report',
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w700,
                  fontSize: 14,
                  color: AppColors.error,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildBottomBar() {
    return SafeArea(
      top: false,
      child: _buildBottomBarContent(),
    );
  }

  Widget _buildBottomBarContent() {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 16),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest.withValues(alpha: 0.95),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(32)),
        boxShadow: [
          BoxShadow(
            color: AppColors.shadow,
            blurRadius: 30,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: GestureDetector(
        onTap: _saving ? null : _save,
        child: Container(
          height: 56,
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [AppColors.primary, AppColors.primaryDim],
            ),
            borderRadius: BorderRadius.circular(999),
            boxShadow: [
              BoxShadow(
                color: AppColors.primary.withValues(alpha: 0.3),
                blurRadius: 20,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: _saving
              ? Center(
                  child: SizedBox(
                    width: 22,
                    height: 22,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: AppColors.onPrimary,
                    ),
                  ),
                )
              : Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      'Save Changes',
                      style: TextStyle(
                        fontFamily: 'Plus Jakarta Sans',
                        fontWeight: FontWeight.w700,
                        fontSize: 16,
                        color: AppColors.onPrimary,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Icon(Icons.check_rounded,
                        color: AppColors.onPrimary, size: 20),
                  ],
                ),
        ),
      ),
    );
  }

  Widget _section({
    required String label,
    required Widget child,
    String? hint,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text(
              label,
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w700,
                letterSpacing: 1.4,
                color: AppColors.secondary,
              ),
            ),
            if (hint != null) ...[
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  hint,
                  style: TextStyle(
                    fontSize: 11,
                    color: AppColors.outlineVariant,
                  ),
                ),
              ),
            ],
          ],
        ),
        const SizedBox(height: 10),
        child,
      ],
    );
  }
}

class _EnvOption {
  final ReportEnvironment env;
  final IconData icon;
  final String label;
  const _EnvOption({
    required this.env,
    required this.icon,
    required this.label,
  });
}

/// One of the media records the report already has on the server — its
/// stable id is what we hand to `mediaIdsToRemove` when the user toggles
/// it off.
class _ExistingMedia {
  final int id;
  final String url;
  const _ExistingMedia({required this.id, required this.url});
}

/// A locally-picked file queued for upload after the report update PUT
/// succeeds. `file` is mutable so the video pipeline can swap in the
/// compressed copy without us losing its slot in the gallery.
class _NewMedia {
  File file;
  final bool isVideo;
  bool converting;
  _NewMedia({
    required this.file,
    required this.isVideo,
    this.converting = false,
  });
}
