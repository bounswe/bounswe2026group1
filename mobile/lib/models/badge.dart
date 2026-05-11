import 'package:flutter/material.dart';

/// Mirrors the backend `Badge` enum. Adding a new badge there means adding
/// an entry to [_meta] here, otherwise it falls back to the unknown-badge
/// no-op rendering.
class BadgeMeta {
  final String label;
  final IconData icon;
  final String description;
  final Color foreground;
  final Color background;
  final int tier;

  const BadgeMeta({
    required this.label,
    required this.icon,
    required this.description,
    required this.foreground,
    required this.background,
    required this.tier,
  });
}

const Map<String, BadgeMeta> _meta = {
  'TRUSTED_REPORTER': BadgeMeta(
    label: 'Trusted Reporter',
    icon: Icons.verified,
    description: '10 verified reports',
    foreground: Color(0xFFB45309),
    background: Color(0x33FCD34D),
    tier: 1,
  ),
  'EXPERT_MAPPER': BadgeMeta(
    label: 'Expert Mapper',
    icon: Icons.workspace_premium,
    description: '50 verified reports',
    foreground: Color(0xFF6D28D9),
    background: Color(0x33C4B5FD),
    tier: 2,
  ),
  'TOP_10': BadgeMeta(
    label: 'Top 10',
    icon: Icons.emoji_events,
    description: 'Currently in the top 10 leaderboard',
    foreground: Color(0xFF166534),
    background: Color(0x3386EFAC),
    tier: 3,
  ),
};

BadgeMeta? badgeMeta(String? badge) => badge == null ? null : _meta[badge];

/// Highest-tier badge among a list of enum names. Used wherever the UI
/// surfaces a single "primary" badge (profile chip, report-author chip).
String? pickHighestTierBadge(List<String> badges) {
  if (badges.isEmpty) return null;
  String? best;
  int bestTier = -1;
  for (final b in badges) {
    final tier = _meta[b]?.tier ?? -1;
    if (tier > bestTier) {
      bestTier = tier;
      best = b;
    }
  }
  return best;
}
