import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../services/api_service.dart';
import '../services/auth_service.dart';
import '../theme/app_colors.dart';

/// Lets a user submit a "this looks fixed" report against [reportId]. A photo
/// is required (JPEG/PNG); the description is optional and capped at 1000
/// chars to match the backend's column constraint.
class CreateFixRequestScreen extends StatefulWidget {
  final int reportId;

  /// Optional human-readable summary of the parent report ("Missing Ramp –
  /// Report #12") shown under the header so the submitter sees what they're
  /// reporting on.
  final String? reportTitle;

  const CreateFixRequestScreen({
    super.key,
    required this.reportId,
    this.reportTitle,
  });

  @override
  State<CreateFixRequestScreen> createState() => _CreateFixRequestScreenState();
}

class _CreateFixRequestScreenState extends State<CreateFixRequestScreen> {
  final _descController = TextEditingController();
  final _picker = ImagePicker();

  File? _photo;
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _descController.dispose();
    super.dispose();
  }

  // ── Image picking ──────────────────────────────────────────────────────────

  Future<void> _pickFromSource(ImageSource source) async {
    try {
      final picked = await _picker.pickImage(
        source: source,
        imageQuality: 85,
        maxWidth: 1920,
      );
      if (picked == null) return;
      setState(() {
        _photo = File(picked.path);
        _error = null;
      });
    } catch (_) {
      setState(() => _error = 'Could not access that source.');
    }
  }

  Future<void> _showSourcePicker() async {
    await showModalBottomSheet<void>(
      context: context,
      backgroundColor: AppColors.surfaceContainerLowest,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(height: 8),
            Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerHigh,
                borderRadius: BorderRadius.circular(999),
              ),
            ),
            ListTile(
              leading: Icon(Icons.photo_camera_outlined,
                  color: AppColors.primary),
              title: Text('Take photo',
                  style: TextStyle(color: AppColors.onSurface)),
              onTap: () {
                Navigator.pop(ctx);
                _pickFromSource(ImageSource.camera);
              },
            ),
            ListTile(
              leading: Icon(Icons.photo_library_outlined,
                  color: AppColors.primary),
              title: Text('Choose from gallery',
                  style: TextStyle(color: AppColors.onSurface)),
              onTap: () {
                Navigator.pop(ctx);
                _pickFromSource(ImageSource.gallery);
              },
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  // ── Submit ─────────────────────────────────────────────────────────────────

  Future<void> _submit() async {
    if (_submitting) return;
    if (_photo == null) {
      setState(() => _error = 'Attach a photo of the fixed area first.');
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final auth = context.read<AuthService>();
      final created = await auth.api.submitFixRequest(
        reportId: widget.reportId,
        file: _photo!,
        description: _descController.text,
      );
      if (!mounted) return;
      Navigator.pop(context, created);
    } on ApiException catch (e) {
      // 409 means there's already an OPEN fix request — give a clearer hint.
      final message = e.statusCode == 409
          ? 'Someone already submitted a fix report for this. Vote on theirs instead.'
          : e.userMessage;
      if (mounted) {
        setState(() {
          _submitting = false;
          _error = message;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _submitting = false;
          _error = 'Could not submit fix report. Try again.';
        });
      }
    }
  }

  // ── Build ──────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      body: Column(
        children: [
          _buildTopBar(),
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildIntroCard(),
                  const SizedBox(height: 20),
                  _buildPhotoField(),
                  const SizedBox(height: 20),
                  _buildDescriptionField(),
                  if (_error != null) ...[
                    const SizedBox(height: 16),
                    _buildErrorBanner(),
                  ],
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
              onPressed:
                  _submitting ? null : () => Navigator.pop(context),
            ),
            const SizedBox(width: 4),
            Text(
              'Report as Fixed',
              style: TextStyle(
                fontFamily: 'Plus Jakarta Sans',
                fontWeight: FontWeight.w800,
                fontSize: 22,
                color: AppColors.primary,
                letterSpacing: -0.5,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildIntroCard() {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.successContainer,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: AppColors.success.withValues(alpha: 0.2),
              shape: BoxShape.circle,
            ),
            alignment: Alignment.center,
            child: Icon(Icons.handyman_outlined,
                color: AppColors.success, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.reportTitle != null
                      ? 'For: ${widget.reportTitle}'
                      : 'Share a photo so the community can verify.',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    color: AppColors.onSurface,
                    height: 1.35,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Confirms as Fixed when 5+ agree and consensus reaches 60%.',
                  style: TextStyle(
                    fontSize: 11,
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

  Widget _buildPhotoField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text(
              'PHOTO',
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w700,
                letterSpacing: 1.4,
                color: AppColors.secondary,
              ),
            ),
            const SizedBox(width: 6),
            Text(
              '*',
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w700,
                color: AppColors.error,
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        GestureDetector(
          onTap: _submitting ? null : _showSourcePicker,
          child: Container(
            height: 220,
            width: double.infinity,
            decoration: BoxDecoration(
              color: AppColors.surfaceContainer,
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                color: _photo == null
                    ? AppColors.outlineVariant.withValues(alpha: 0.5)
                    : AppColors.primary.withValues(alpha: 0.4),
                width: _photo == null ? 1.5 : 2,
              ),
            ),
            clipBehavior: Clip.hardEdge,
            child: _photo == null
                ? Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Container(
                        width: 56,
                        height: 56,
                        decoration: BoxDecoration(
                          color: AppColors.cardSurface,
                          shape: BoxShape.circle,
                        ),
                        child: Icon(Icons.add_a_photo_outlined,
                            color: AppColors.primary, size: 24),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        'Add a photo',
                        style: TextStyle(
                          fontFamily: 'Plus Jakarta Sans',
                          fontWeight: FontWeight.w700,
                          fontSize: 14,
                          color: AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'JPG or PNG · max 15 MB',
                        style: TextStyle(
                          fontSize: 11,
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  )
                : Stack(
                    fit: StackFit.expand,
                    children: [
                      Image.file(_photo!, fit: BoxFit.cover),
                      Positioned(
                        bottom: 10,
                        right: 10,
                        child: GestureDetector(
                          onTap: _submitting
                              ? null
                              : () => setState(() => _photo = null),
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 10, vertical: 6),
                            decoration: BoxDecoration(
                              color: AppColors.cardSurface
                                  .withValues(alpha: 0.92),
                              borderRadius: BorderRadius.circular(999),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.refresh,
                                    size: 14, color: AppColors.primary),
                                const SizedBox(width: 4),
                                Text(
                                  'Replace',
                                  style: TextStyle(
                                    fontSize: 11,
                                    fontWeight: FontWeight.w700,
                                    color: AppColors.primary,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
          ),
        ),
      ],
    );
  }

  Widget _buildDescriptionField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text(
              'DESCRIPTION',
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w700,
                letterSpacing: 1.4,
                color: AppColors.secondary,
              ),
            ),
            const SizedBox(width: 8),
            Text(
              'Optional',
              style: TextStyle(
                fontSize: 10,
                color: AppColors.outlineVariant,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        Container(
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
                color: AppColors.outlineVariant.withValues(alpha: 0.4)),
          ),
          child: TextField(
            controller: _descController,
            maxLines: 4,
            maxLength: 1000,
            style: TextStyle(fontSize: 14, color: AppColors.onSurface),
            decoration: InputDecoration(
              hintText: 'What changed? When was it fixed?',
              hintStyle: TextStyle(
                color: AppColors.outlineVariant,
                fontSize: 13,
              ),
              border: InputBorder.none,
              contentPadding: const EdgeInsets.symmetric(
                  horizontal: 14, vertical: 12),
              counterStyle: TextStyle(
                fontSize: 11,
                color: AppColors.outlineVariant,
              ),
            ),
            onChanged: (_) => setState(() {}),
          ),
        ),
      ],
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

  Widget _buildBottomBar() {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 36),
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
        onTap: _submitting ? null : _submit,
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
          child: _submitting
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
                    Icon(Icons.send_rounded,
                        color: AppColors.onPrimary, size: 20),
                    const SizedBox(width: 10),
                    Text(
                      'Submit fix report',
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
      ),
    );
  }
}
