import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

import '../theme/app_colors.dart';

/// Lightweight in-app cropper. The user pans/zooms [source] inside a square
/// viewport overlaid with a circular mask; on confirm we capture the
/// viewport via [RepaintBoundary.toImage] and write a PNG to the system
/// temp directory.
///
/// Pure Flutter — no `image_cropper` plugin / native config needed.
class AvatarCropScreen extends StatefulWidget {
  final File source;

  const AvatarCropScreen({super.key, required this.source});

  @override
  State<AvatarCropScreen> createState() => _AvatarCropScreenState();
}

class _AvatarCropScreenState extends State<AvatarCropScreen> {
  final GlobalKey _boundaryKey = GlobalKey();
  final TransformationController _ctrl = TransformationController();
  bool _saving = false;

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      final boundary = _boundaryKey.currentContext?.findRenderObject()
          as RenderRepaintBoundary?;
      if (boundary == null) {
        throw StateError('Cropper boundary not mounted yet.');
      }
      // 3.0 ratio gives plenty of resolution for an avatar without ballooning
      // the upload size.
      final image = await boundary.toImage(pixelRatio: 3.0);
      final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
      if (bytes == null) {
        throw StateError('Could not encode cropped image.');
      }
      final dir = await Directory.systemTemp.createTemp('avatar_');
      final out = File('${dir.path}/avatar.png');
      await out.writeAsBytes(bytes.buffer.asUint8List());
      if (!mounted) return;
      Navigator.pop(context, out);
    } catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not crop image: $e')),
      );
    }
  }

  void _reset() {
    setState(() => _ctrl.value = Matrix4.identity());
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      // Soft dark backdrop instead of pure black — keeps focus on the photo
      // without the heavy theatre-mode feel.
      backgroundColor: const Color(0xFF101212),
      body: SafeArea(
        child: Column(
          children: [
            _buildTopBar(),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Center(
                  child: LayoutBuilder(
                    builder: (ctx, constraints) {
                      // Use the available square — works on phones (width-
                      // bound) and tablets (height-bound).
                      final side = constraints.biggest.shortestSide
                          .clamp(0.0, 520.0);
                      return SizedBox(
                        width: side,
                        height: side,
                        child: Stack(
                          children: [
                            // Capture target — the user-transformed image.
                            ClipRRect(
                              borderRadius: BorderRadius.circular(20),
                              child: RepaintBoundary(
                                key: _boundaryKey,
                                child: ColoredBox(
                                  color: const Color(0xFF1B1F1E),
                                  child: InteractiveViewer(
                                    transformationController: _ctrl,
                                    minScale: 0.5,
                                    maxScale: 5,
                                    clipBehavior: Clip.hardEdge,
                                    child: Image.file(
                                      widget.source,
                                      fit: BoxFit.contain,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                            // Veil + circle reveal (decorative — the saved
                            // image is the full square; the displayed
                            // avatar is `ClipOval`'d everywhere it's shown).
                            Positioned.fill(
                              child: IgnorePointer(
                                child: CustomPaint(
                                  painter: _CircleMaskPainter(
                                    veilColor: Colors.black
                                        .withValues(alpha: 0.45),
                                    ringColor: AppColors.primary
                                        .withValues(alpha: 0.95),
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
                ),
              ),
            ),
            _buildHintCard(),
            _buildBottomBar(),
          ],
        ),
      ),
    );
  }

  Widget _buildTopBar() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
      child: Row(
        children: [
          IconButton(
            icon: const Icon(Icons.close, color: Colors.white),
            onPressed: _saving ? null : () => Navigator.pop(context),
            tooltip: 'Cancel',
          ),
          const SizedBox(width: 4),
          const Text(
            'Crop avatar',
            style: TextStyle(
              fontFamily: 'Plus Jakarta Sans',
              fontWeight: FontWeight.w800,
              fontSize: 18,
              color: Colors.white,
            ),
          ),
          const Spacer(),
          TextButton.icon(
            onPressed: _saving ? null : _reset,
            icon: const Icon(Icons.restart_alt,
                size: 18, color: Colors.white70),
            label: const Text(
              'Reset',
              style: TextStyle(
                color: Colors.white70,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHintCard() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.06),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            Icon(Icons.touch_app_outlined,
                size: 16, color: Colors.white.withValues(alpha: 0.8)),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                'Drag to reposition · pinch to zoom',
                style: TextStyle(
                  fontSize: 12,
                  color: Colors.white.withValues(alpha: 0.85),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBottomBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 20),
      child: Row(
        children: [
          Expanded(
            child: OutlinedButton(
              onPressed: _saving ? null : () => Navigator.pop(context),
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 14),
                side: BorderSide(color: Colors.white.withValues(alpha: 0.25)),
                shape: const StadiumBorder(),
                foregroundColor: Colors.white,
              ),
              child: const Text(
                'Cancel',
                style: TextStyle(fontWeight: FontWeight.w700),
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            flex: 2,
            child: FilledButton.icon(
              onPressed: _saving ? null : _save,
              icon: _saving
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Icon(Icons.check_rounded, size: 20),
              label: Text(
                _saving ? 'Saving…' : 'Use photo',
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
              style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 14),
                backgroundColor: AppColors.primary,
                foregroundColor: AppColors.onPrimary,
                shape: const StadiumBorder(),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Paints a translucent veil with a transparent circle in the centre so
/// the user can see exactly what survives the crop. A subtle accent ring
/// outlines the keep-area for clarity.
class _CircleMaskPainter extends CustomPainter {
  final Color veilColor;
  final Color ringColor;

  _CircleMaskPainter({required this.veilColor, required this.ringColor});

  @override
  void paint(Canvas canvas, Size size) {
    final radius = size.shortestSide / 2;
    final centre = size.center(Offset.zero);

    final outer = Path()
      ..addRect(Rect.fromLTWH(0, 0, size.width, size.height));
    final hole = Path()..addOval(Rect.fromCircle(center: centre, radius: radius));
    final mask = Path.combine(PathOperation.difference, outer, hole);

    canvas.drawPath(mask, Paint()..color = veilColor);
    canvas.drawCircle(
      centre,
      radius - 1,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2
        ..color = ringColor,
    );
  }

  @override
  bool shouldRepaint(_CircleMaskPainter oldDelegate) =>
      oldDelegate.veilColor != veilColor || oldDelegate.ringColor != ringColor;
}
