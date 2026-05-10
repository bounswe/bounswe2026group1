import 'package:flutter/material.dart';

import '../models/report_model.dart';
import '../theme/app_colors.dart';

/// Compact list row used by the feed and any other list of reports. Mirrors
/// the web feed card: media thumbnail, status pill, type/environment chips,
/// title + description, and the agree/disagree counts on the bottom row.
class ReportCard extends StatelessWidget {
  final ReportModel report;
  final VoidCallback? onTap;

  /// Optional pre-computed distance (km) shown as a pill on the thumbnail.
  /// Provided by the feed when the user has supplied a search location.
  final double? distanceKm;

  const ReportCard({
    super.key,
    required this.report,
    this.onTap,
    this.distanceKm,
  });

  @override
  Widget build(BuildContext context) {
    final hasMedia = report.mediaUrls.isNotEmpty;
    return Material(
      color: AppColors.cardSurface,
      borderRadius: BorderRadius.circular(16),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Container(
          decoration: BoxDecoration(
            border: Border.all(color: AppColors.outlineVariant, width: 1),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _Thumbnail(
                report: report,
                hasMedia: hasMedia,
                distanceKm: distanceKm,
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _Chips(report: report),
                    const SizedBox(height: 8),
                    Text(
                      report.headline,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontFamily: 'Plus Jakarta Sans',
                        fontWeight: FontWeight.w700,
                        fontSize: 15,
                        height: 1.25,
                        color: AppColors.onSurface,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      report.description.trim().isEmpty
                          ? 'No description'
                          : report.description,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 12,
                        height: 1.35,
                        fontStyle: report.description.trim().isEmpty
                            ? FontStyle.italic
                            : FontStyle.normal,
                        color: AppColors.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 8),
                    _Footer(report: report),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Thumbnail extends StatelessWidget {
  final ReportModel report;
  final bool hasMedia;
  final double? distanceKm;

  const _Thumbnail({
    required this.report,
    required this.hasMedia,
    required this.distanceKm,
  });

  @override
  Widget build(BuildContext context) {
    return AspectRatio(
      aspectRatio: 5 / 3,
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (hasMedia)
            Image.network(
              report.mediaUrls.first,
              fit: BoxFit.cover,
              errorBuilder: (_, __, ___) => _placeholder(),
              loadingBuilder: (ctx, child, progress) {
                if (progress == null) return child;
                return Container(
                  color: AppColors.surfaceContainer,
                  alignment: Alignment.center,
                  child: SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: AppColors.primary,
                    ),
                  ),
                );
              },
            )
          else
            _placeholder(),
          // Top-left: distance pill (if supplied)
          if (distanceKm != null)
            Positioned(
              top: 8,
              left: 8,
              child: _Pill(
                icon: Icons.near_me,
                label: _formatDistance(distanceKm!),
                background: AppColors.accentPurple,
                foreground: Colors.white,
              ),
            ),
          // Top-right: status pill
          Positioned(
            top: 8,
            right: 8,
            child: _Pill(
              label: report.status.label,
              background: report.status.color,
              foreground: Colors.white,
            ),
          ),
        ],
      ),
    );
  }

  Widget _placeholder() {
    return Container(
      color: AppColors.surfaceContainer,
      alignment: Alignment.center,
      child: Icon(
        report.displayIcon,
        size: 44,
        color: AppColors.onSurfaceVariant.withValues(alpha: 0.6),
      ),
    );
  }

  static String _formatDistance(double km) {
    if (km < 0.1) return '${(km * 1000).round()} m';
    if (km < 10) return '${km.toStringAsFixed(1)} km';
    return '${km.round()} km';
  }
}

class _Chips extends StatelessWidget {
  final ReportModel report;
  const _Chips({required this.report});

  @override
  Widget build(BuildContext context) {
    final typeColor = report.reportType.isPositive
        ? AppColors.success
        : AppColors.warning;
    return Wrap(
      spacing: 6,
      runSpacing: 4,
      children: [
        _MiniChip(
          icon: report.reportType.icon,
          label: report.reportType.label,
          color: typeColor,
        ),
        _MiniChip(
          icon: report.environment.icon,
          label: report.environment.label,
          color: AppColors.accentBlue,
        ),
      ],
    );
  }
}

class _Footer extends StatelessWidget {
  final ReportModel report;
  const _Footer({required this.report});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(
          Icons.schedule,
          size: 12,
          color: AppColors.onSurfaceVariant,
        ),
        const SizedBox(width: 4),
        Text(
          report.timeAgo,
          style: TextStyle(
            fontSize: 11,
            color: AppColors.onSurfaceVariant,
          ),
        ),
        const Spacer(),
        Icon(Icons.thumb_up, size: 13, color: AppColors.primary),
        const SizedBox(width: 3),
        Text(
          '${report.agrees}',
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            color: AppColors.onSurface,
          ),
        ),
        const SizedBox(width: 10),
        Icon(Icons.thumb_down, size: 13, color: AppColors.error),
        const SizedBox(width: 3),
        Text(
          '${report.disagrees}',
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            color: AppColors.onSurface,
          ),
        ),
      ],
    );
  }
}

class _Pill extends StatelessWidget {
  final IconData? icon;
  final String label;
  final Color background;
  final Color foreground;

  const _Pill({
    this.icon,
    required this.label,
    required this.background,
    required this.foreground,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 12, color: foreground),
            const SizedBox(width: 4),
          ],
          Text(
            label,
            style: TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.w700,
              letterSpacing: 0.4,
              color: foreground,
            ),
          ),
        ],
      ),
    );
  }
}

class _MiniChip extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;

  const _MiniChip({
    required this.icon,
    required this.label,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.4), width: 1),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 12, color: color),
          const SizedBox(width: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}
