import 'package:flutter/material.dart';
import '../models/badge.dart';

/// Badge chip rendered next to a username. `compact = true` collapses to
/// just the icon (used inline next to the report-author name); the default
/// renders an icon + label chip used on profile pages.
class BadgeChip extends StatelessWidget {
  final String badge;
  final bool compact;

  const BadgeChip({super.key, required this.badge, this.compact = false});

  @override
  Widget build(BuildContext context) {
    final meta = badgeMeta(badge);
    if (meta == null) return const SizedBox.shrink();

    if (compact) {
      return Tooltip(
        message: '${meta.label} — ${meta.description}',
        child: Container(
          width: 22,
          height: 22,
          decoration: BoxDecoration(
            color: meta.background,
            shape: BoxShape.circle,
          ),
          alignment: Alignment.center,
          child: Icon(meta.icon, color: meta.foreground, size: 14),
        ),
      );
    }

    return Tooltip(
      message: meta.description,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: meta.background,
          borderRadius: BorderRadius.circular(999),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(meta.icon, color: meta.foreground, size: 14),
            const SizedBox(width: 6),
            Text(
              meta.label,
              style: TextStyle(
                color: meta.foreground,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Strip of all badges held by a user. Renders nothing when the list is
/// empty so the caller doesn't need a guard.
class BadgeList extends StatelessWidget {
  final List<String> badges;

  const BadgeList({super.key, required this.badges});

  @override
  Widget build(BuildContext context) {
    if (badges.isEmpty) return const SizedBox.shrink();
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: badges.map((b) => BadgeChip(badge: b)).toList(),
    );
  }
}
