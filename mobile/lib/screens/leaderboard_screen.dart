import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/leaderboard_entry.dart';
import '../services/api_service.dart';
import '../services/auth_service.dart';
import '../theme/app_colors.dart';
import 'user_profile_screen.dart';

/// Public leaderboard — top-N entries plus the caller's rank when
/// authenticated. Mirrors the web `/leaderboard` page: olympic-style ranks
/// (tied scores share a position) and a gold/silver/bronze tone for the
/// top three.
class LeaderboardScreen extends StatefulWidget {
  const LeaderboardScreen({super.key});

  @override
  State<LeaderboardScreen> createState() => _LeaderboardScreenState();
}

class _LeaderboardScreenState extends State<LeaderboardScreen> {
  List<LeaderboardEntry>? _entries;
  RankInfo? _callerRank;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final api = context.read<AuthService>().api;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final payload = await api.getLeaderboard();
      if (!mounted) return;
      final rawEntries = payload['entries'] as List<dynamic>? ?? const [];
      final rawCaller = payload['callerRank'] as Map<String, dynamic>?;
      setState(() {
        _entries = rawEntries
            .map((e) => LeaderboardEntry.fromJson(e as Map<String, dynamic>))
            .toList();
        _callerRank = rawCaller != null ? RankInfo.fromJson(rawCaller) : null;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e is ApiException ? e.userMessage : e.toString();
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final isAuthenticated = context.read<AuthService>().isAuthenticated;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Leaderboard'),
      ),
      body: RefreshIndicator(
        onRefresh: _load,
        child: _buildBody(isAuthenticated),
      ),
    );
  }

  Widget _buildBody(bool isAuthenticated) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return _ErrorView(message: _error!, onRetry: _load);
    }
    final entries = _entries ?? const <LeaderboardEntry>[];

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      children: [
        Text(
          'Top contributors ranked by points. Tied scores share a rank.',
          style: TextStyle(
            fontSize: 13,
            color: AppColors.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 16),
        if (_callerRank != null) _YourRankBanner(info: _callerRank!),
        if (_callerRank != null) const SizedBox(height: 12),
        if (_callerRank == null && !isAuthenticated)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Text(
              'Log in to see your own rank on this page.',
              style: TextStyle(
                fontSize: 12,
                color: AppColors.onSurfaceVariant,
              ),
            ),
          ),
        if (entries.isEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 32),
            child: Center(
              child: Text(
                'No ranked users yet. Submit a report to get on the board.',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 14,
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ),
          )
        else
          ...entries.map(_buildRow),
      ],
    );
  }

  Widget _buildRow(LeaderboardEntry entry) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: InkWell(
        onTap: () {
          Navigator.of(context).push(MaterialPageRoute(
            builder: (_) => UserProfileScreen(
              userId: entry.userId,
              initialName: entry.name,
              initialAvatarUrl: entry.avatarUrl,
            ),
          ));
        },
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(
            color: AppColors.cardSurface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: AppColors.outlineVariant.withValues(alpha: 0.4),
            ),
          ),
          child: Row(
            children: [
              _RankCell(rank: entry.rank),
              const SizedBox(width: 12),
              _Avatar(url: entry.avatarUrl),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  entry.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontWeight: FontWeight.w600,
                    color: AppColors.onSurface,
                  ),
                ),
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    'POINTS',
                    style: TextStyle(
                      fontSize: 9,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 1.2,
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                  Text(
                    '${entry.points}',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: AppColors.onSurface,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RankCell extends StatelessWidget {
  final int rank;

  const _RankCell({required this.rank});

  @override
  Widget build(BuildContext context) {
    // Olympic-style podium tones for the top three; everyone else stays
    // neutral so the eye still picks up the medalists.
    final (bg, fg) = switch (rank) {
      1 => (const Color(0xFFFDE68A), const Color(0xFF92400E)), // gold
      2 => (const Color(0xFFE5E7EB), const Color(0xFF374151)), // silver
      3 => (const Color(0xFFFED7AA), const Color(0xFF9A3412)), // bronze
      _ => (
          AppColors.outlineVariant.withValues(alpha: 0.25),
          AppColors.onSurfaceVariant
        ),
    };
    return Container(
      width: 44,
      height: 36,
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(10),
      ),
      alignment: Alignment.center,
      child: Text(
        '#$rank',
        style: TextStyle(
          fontWeight: FontWeight.w700,
          fontSize: 13,
          color: fg,
        ),
      ),
    );
  }
}

class _Avatar extends StatelessWidget {
  final String? url;

  const _Avatar({required this.url});

  @override
  Widget build(BuildContext context) {
    if (url != null && url!.isNotEmpty) {
      return CircleAvatar(
        radius: 18,
        backgroundImage: NetworkImage(url!),
      );
    }
    return CircleAvatar(
      radius: 18,
      backgroundColor: AppColors.outlineVariant.withValues(alpha: 0.3),
      child: Icon(Icons.person, size: 22, color: AppColors.onSurfaceVariant),
    );
  }
}

class _YourRankBanner extends StatelessWidget {
  final RankInfo info;

  const _YourRankBanner({required this.info});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
      decoration: BoxDecoration(
        color: AppColors.primary.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: AppColors.primary.withValues(alpha: 0.24),
        ),
      ),
      child: Row(
        children: [
          Icon(Icons.person_pin, color: AppColors.primary, size: 24),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'YOUR RANK',
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 1.2,
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
                Text(
                  '#${info.rank}',
                  style: TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                    color: AppColors.onSurface,
                  ),
                ),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                'POINTS',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.2,
                  color: AppColors.onSurfaceVariant,
                ),
              ),
              Text(
                '${info.points}',
                style: TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                  color: AppColors.onSurface,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;

  const _ErrorView({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.cloud_off, size: 48, color: AppColors.onSurfaceVariant),
            const SizedBox(height: 12),
            Text(
              'Failed to load the leaderboard',
              style: TextStyle(
                fontWeight: FontWeight.w700,
                color: AppColors.onSurface,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              message,
              textAlign: TextAlign.center,
              style: TextStyle(color: AppColors.onSurfaceVariant),
            ),
            const SizedBox(height: 16),
            FilledButton(onPressed: onRetry, child: const Text('Try again')),
          ],
        ),
      ),
    );
  }
}
