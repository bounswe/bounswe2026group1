import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';
import '../theme/app_colors.dart';
import '../models/report_model.dart';
import '../services/auth_service.dart';
import '../services/api_service.dart';
import 'report_success_screen.dart';

// Default location: Mission District, San Francisco
const double _defaultLat = 37.7599;
const double _defaultLon = -122.4148;
const String _defaultAddress = '245 Valencia St, Mission District';

class MakeReportScreen extends StatefulWidget {
  const MakeReportScreen({super.key});

  @override
  State<MakeReportScreen> createState() => _MakeReportScreenState();
}

class _MakeReportScreenState extends State<MakeReportScreen> {
  final _descController = TextEditingController();
  ReportTag _selectedTag = ReportTag.other;
  File? _selectedImage;
  bool _submitting = false;

  final _picker = ImagePicker();

  @override
  void dispose() {
    _descController.dispose();
    super.dispose();
  }

  // ─── Image picking ─────────────────────────────────────────────────────────

  Future<void> _pickImage(ImageSource source) async {
    try {
      final picked = await _picker.pickImage(
        source: source,
        imageQuality: 80,
        maxWidth: 1080,
      );
      if (picked != null) setState(() => _selectedImage = File(picked.path));
    } catch (_) {
      // Permission denied or unavailable — ignore silently
    }
  }

  void _showImageOptions() {
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
            _sheetOption(
              icon: Icons.camera_alt_outlined,
              label: 'Take Photo',
              onTap: () {
                Navigator.pop(context);
                _pickImage(ImageSource.camera);
              },
            ),
            _sheetOption(
              icon: Icons.photo_library_outlined,
              label: 'Choose from Gallery',
              onTap: () {
                Navigator.pop(context);
                _pickImage(ImageSource.gallery);
              },
            ),
            if (_selectedImage != null)
              _sheetOption(
                icon: Icons.delete_outline,
                label: 'Remove Photo',
                color: const Color(0xFFB02500),
                onTap: () {
                  setState(() => _selectedImage = null);
                  Navigator.pop(context);
                },
              ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  Widget _sheetOption({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
    Color? color,
  }) {
    return ListTile(
      leading: Icon(icon, color: color ?? AppColors.onSurface),
      title: Text(
        label,
        style: TextStyle(
          fontWeight: FontWeight.w600,
          color: color ?? AppColors.onSurface,
        ),
      ),
      onTap: onTap,
    );
  }

  // ─── Submit ────────────────────────────────────────────────────────────────

  Future<void> _submit() async {
    final desc = _descController.text.trim();
    if (desc.length < 10) {
      _showError('Description must be at least 10 characters.');
      return;
    }

    setState(() => _submitting = true);
    try {
      final auth = context.read<AuthService>();
      final report = await auth.api.createReport(
        userId: auth.userId,
        latitude: _defaultLat,
        longitude: _defaultLon,
        description: desc,
        tag: _selectedTag,
      );
      if (!mounted) return;
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder: (_) => ReportSuccessScreen(report: report),
        ),
      );
    } on ApiException catch (e) {
      if (mounted) _showError(e.userMessage);
    } catch (_) {
      if (mounted) _showError('Failed to submit. Please try again.');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  void _showError(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(msg),
        backgroundColor: const Color(0xFFB02500),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }

  // ─── Build ─────────────────────────────────────────────────────────────────

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
                  _buildProgressHeader(),
                  const SizedBox(height: 24),
                  _buildImageSection(),
                  const SizedBox(height: 20),
                  _buildLocationCard(),
                  const SizedBox(height: 20),
                  _buildDescriptionField(),
                  const SizedBox(height: 20),
                  _buildCategorySelector(),
                  const SizedBox(height: 20),
                  _buildImpactCard(),
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

  // ─── Top bar ───────────────────────────────────────────────────────────────

  Widget _buildTopBar() {
    return SafeArea(
      bottom: false,
      child: Container(
        color: AppColors.surface.withOpacity(0.92),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        child: Row(
          children: [
            IconButton(
              icon: const Icon(Icons.close, color: AppColors.primary),
              onPressed: () => Navigator.pop(context),
            ),
            const SizedBox(width: 4),
            const Text(
              'Mapcess',
              style: TextStyle(
                fontFamily: 'Plus Jakarta Sans',
                fontWeight: FontWeight.w800,
                fontSize: 24,
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
              child: const Text(
                'DRAFT',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.2,
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

  // ─── Progress header ───────────────────────────────────────────────────────

  Widget _buildProgressHeader() {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'NEW REPORT',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.5,
                  color: AppColors.primary,
                ),
              ),
              const SizedBox(height: 4),
              const Text(
                'Issue Details',
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w800,
                  fontSize: 28,
                  color: AppColors.onSurface,
                  letterSpacing: -0.5,
                ),
              ),
            ],
          ),
        ),
        Row(
          children: [
            _dot(true),
            const SizedBox(width: 4),
            _dot(true),
            const SizedBox(width: 4),
            _dot(false),
          ],
        ),
      ],
    );
  }

  Widget _dot(bool active) => AnimatedContainer(
    duration: const Duration(milliseconds: 200),
    height: 6,
    width: active ? 24 : 14,
    decoration: BoxDecoration(
      color: active ? AppColors.primary : AppColors.surfaceContainerHigh,
      borderRadius: BorderRadius.circular(999),
    ),
  );

  // ─── Image section ─────────────────────────────────────────────────────────

  Widget _buildImageSection() {
    return GestureDetector(
      onTap: _showImageOptions,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(24),
        child: AspectRatio(
          aspectRatio: 16 / 9,
          child: _selectedImage != null
              ? Stack(
                  fit: StackFit.expand,
                  children: [
                    Image.file(_selectedImage!, fit: BoxFit.cover),
                    // Bottom overlay
                    Positioned(
                      bottom: 0,
                      left: 0,
                      right: 0,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 12,
                        ),
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.topCenter,
                            end: Alignment.bottomCenter,
                            colors: [
                              Colors.transparent,
                              Colors.black.withOpacity(0.45),
                            ],
                          ),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 12,
                                vertical: 6,
                              ),
                              decoration: BoxDecoration(
                                color: Colors.white.withOpacity(0.85),
                                borderRadius: BorderRadius.circular(999),
                              ),
                              child: const Text(
                                'Tap to change',
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w600,
                                  color: AppColors.onSurface,
                                ),
                              ),
                            ),
                            GestureDetector(
                              onTap: () =>
                                  setState(() => _selectedImage = null),
                              child: Container(
                                width: 34,
                                height: 34,
                                decoration: BoxDecoration(
                                  color: Colors.white,
                                  shape: BoxShape.circle,
                                ),
                                child: const Icon(
                                  Icons.delete_outline,
                                  color: Color(0xFFB02500),
                                  size: 18,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                )
              : Container(
                  color: const Color(0xFFF0F1F1),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Container(
                        width: 56,
                        height: 56,
                        decoration: BoxDecoration(
                          color: Colors.white,
                          shape: BoxShape.circle,
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withOpacity(0.08),
                              blurRadius: 12,
                            ),
                          ],
                        ),
                        child: const Icon(
                          Icons.add_a_photo_outlined,
                          color: AppColors.primary,
                          size: 26,
                        ),
                      ),
                      const SizedBox(height: 12),
                      const Text(
                        'Add a Photo',
                        style: TextStyle(
                          fontFamily: 'Plus Jakarta Sans',
                          fontWeight: FontWeight.w700,
                          fontSize: 15,
                          color: AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(height: 4),
                      const Text(
                        'Camera or gallery',
                        style: TextStyle(
                          fontSize: 12,
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
        ),
      ),
    );
  }

  // ─── Location card ─────────────────────────────────────────────────────────

  Widget _buildLocationCard() {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFFF0F1F1),
        borderRadius: BorderRadius.circular(20),
      ),
      clipBehavior: Clip.hardEdge,
      child: Column(
        children: [
          // Mini map
          SizedBox(
            height: 110,
            width: double.infinity,
            child: Stack(
              fit: StackFit.expand,
              children: [
                CustomPaint(painter: _MiniMapPainter()),
                Center(
                  child: Container(
                    width: 36,
                    height: 36,
                    decoration: BoxDecoration(
                      color: AppColors.primary.withOpacity(0.15),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.location_on,
                      color: AppColors.primary,
                      size: 22,
                    ),
                  ),
                ),
              ],
            ),
          ),
          // Address row
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              children: [
                const Icon(
                  Icons.near_me_outlined,
                  size: 16,
                  color: AppColors.secondary,
                ),
                const SizedBox(width: 8),
                const Expanded(
                  child: Text(
                    _defaultAddress,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: AppColors.onSurface,
                    ),
                  ),
                ),
                Text(
                  '${_defaultLat.toStringAsFixed(4)}, ${_defaultLon.toStringAsFixed(4)}',
                  style: const TextStyle(
                    fontSize: 10,
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ─── Description ───────────────────────────────────────────────────────────

  Widget _buildDescriptionField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'DESCRIBE THE ISSUE',
          style: TextStyle(
            fontSize: 10,
            fontWeight: FontWeight.w700,
            letterSpacing: 1.4,
            color: AppColors.secondary,
          ),
        ),
        const SizedBox(height: 8),
        Container(
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(20),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.04),
                blurRadius: 10,
              ),
            ],
          ),
          child: Stack(
            children: [
              TextField(
                controller: _descController,
                maxLines: 5,
                maxLength: 1000,
                style: const TextStyle(
                  fontSize: 14,
                  color: AppColors.onSurface,
                  height: 1.5,
                ),
                decoration: const InputDecoration(
                  hintText:
                      'What\'s happening? Be as specific as possible to help our crews find it…',
                  hintStyle: TextStyle(
                    color: AppColors.outlineVariant,
                    fontSize: 14,
                  ),
                  border: InputBorder.none,
                  contentPadding: EdgeInsets.fromLTRB(18, 18, 18, 40),
                  counterText: '',
                ),
              ),
              const Positioned(
                bottom: 12,
                right: 14,
                child: Text(
                  'Min 10 characters',
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.w600,
                    color: AppColors.outlineVariant,
                    letterSpacing: 0.3,
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  // ─── Category selector ─────────────────────────────────────────────────────

  Widget _buildCategorySelector() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'SELECT CATEGORY',
          style: TextStyle(
            fontSize: 10,
            fontWeight: FontWeight.w700,
            letterSpacing: 1.4,
            color: AppColors.secondary,
          ),
        ),
        const SizedBox(height: 10),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: ReportTag.values
              .map((tag) => _buildTagChip(tag))
              .toList(),
        ),
      ],
    );
  }

  Widget _buildTagChip(ReportTag tag) {
    final selected = _selectedTag == tag;
    return GestureDetector(
      onTap: () => setState(() => _selectedTag = tag),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        decoration: BoxDecoration(
          color: selected ? tag.color : const Color(0xFFCFE6F2),
          borderRadius: BorderRadius.circular(999),
          boxShadow: selected
              ? [
                  BoxShadow(
                    color: tag.color.withOpacity(0.3),
                    blurRadius: 10,
                    offset: const Offset(0, 3),
                  ),
                ]
              : null,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              tag.icon,
              size: 16,
              color: selected ? Colors.white : const Color(0xFF40555F),
            ),
            const SizedBox(width: 6),
            Text(
              tag.label,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: selected ? Colors.white : const Color(0xFF40555F),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ─── Impact card ───────────────────────────────────────────────────────────

  Widget _buildImpactCard() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFFF0F1F1),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'IMPACT LEVEL',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.4,
                  color: AppColors.secondary,
                ),
              ),
              Text(
                _selectedTag == ReportTag.brokenElevator ||
                        _selectedTag == ReportTag.missingRamp
                    ? 'High'
                    : _selectedTag == ReportTag.construction ||
                            _selectedTag == ReportTag.wetFloor
                        ? 'Moderate'
                        : 'Low',
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: AppColors.primary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(999),
            child: Container(
              height: 8,
              color: AppColors.surfaceContainerHigh,
              child: LayoutBuilder(
                builder: (_, c) {
                  final frac = _selectedTag == ReportTag.brokenElevator ||
                          _selectedTag == ReportTag.missingRamp
                      ? 0.9
                      : _selectedTag == ReportTag.construction ||
                              _selectedTag == ReportTag.wetFloor
                          ? 0.55
                          : 0.25;
                  return Align(
                    alignment: Alignment.centerLeft,
                    child: Container(
                      width: c.maxWidth * frac,
                      height: 8,
                      decoration: const BoxDecoration(
                        gradient: LinearGradient(
                          colors: [Color(0xFF9DF197), AppColors.primary],
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
          const SizedBox(height: 10),
          const Text(
            'This helps us prioritize based on community safety and infrastructure health.',
            style: TextStyle(
              fontSize: 11,
              color: AppColors.onSurfaceVariant,
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }

  // ─── Bottom bar ────────────────────────────────────────────────────────────

  Widget _buildBottomBar() {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 36),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest.withOpacity(0.95),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(32)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.06),
            blurRadius: 30,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: Row(
        children: [
          Expanded(
            child: GestureDetector(
              onTap: _submitting ? null : _submit,
              child: Container(
                height: 56,
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [AppColors.primary, AppColors.primaryDim],
                  ),
                  borderRadius: BorderRadius.circular(999),
                  boxShadow: [
                    BoxShadow(
                      color: AppColors.primary.withOpacity(0.3),
                      blurRadius: 20,
                      offset: const Offset(0, 8),
                    ),
                  ],
                ),
                child: _submitting
                    ? const Center(
                        child: SizedBox(
                          width: 22,
                          height: 22,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: AppColors.onPrimary,
                          ),
                        ),
                      )
                    : const Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text(
                            'Post Report',
                            style: TextStyle(
                              fontFamily: 'Plus Jakarta Sans',
                              fontWeight: FontWeight.w700,
                              fontSize: 17,
                              color: AppColors.onPrimary,
                            ),
                          ),
                          SizedBox(width: 10),
                          Icon(Icons.send, color: AppColors.onPrimary, size: 20),
                        ],
                      ),
              ),
            ),
          ),
          const SizedBox(width: 12),
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerHigh,
              borderRadius: BorderRadius.circular(999),
            ),
            child: const Icon(
              Icons.bookmark_border,
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Mini map painter ──────────────────────────────────────────────────────────

class _MiniMapPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(
      Rect.fromLTWH(0, 0, size.width, size.height),
      Paint()..color = const Color(0xFFECEDEE),
    );
    final block = Paint()..color = const Color(0xFFDFE1E2);
    final street = Paint()..color = Colors.white;
    const cell = 22.0;
    const sw = 4.0;
    for (double x = 0; x < size.width; x += cell) {
      for (double y = 0; y < size.height; y += cell) {
        canvas.drawRect(
          Rect.fromLTWH(x + sw / 2, y + sw / 2, cell - sw, cell - sw),
          block,
        );
      }
    }
    for (double y = 0; y < size.height; y += cell) {
      canvas.drawRect(Rect.fromLTWH(0, y, size.width, sw), street);
    }
    for (double x = 0; x < size.width; x += cell) {
      canvas.drawRect(Rect.fromLTWH(x, 0, sw, size.height), street);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter old) => false;
}
