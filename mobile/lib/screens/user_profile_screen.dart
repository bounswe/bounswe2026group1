import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/report_model.dart';
import '../services/api_service.dart';
import '../services/auth_service.dart';
import '../theme/app_colors.dart';
import 'report_detail_screen.dart';

/// Read-only public profile shown when a user taps someone else's avatar
/// (report author, commenter, …). Loads the target's profile and recent
/// reports via the existing public endpoints — no edit affordances.
class UserProfileScreen extends StatefulWidget {
  final int userId;

  /// Optional pre-known display name to show while the network round-trip
  /// to [getUserById] is still in flight; avoids the title bar flickering
  /// from "Profile" to the real name.
  final String? initialName;

  /// Optional pre-known avatar so the hero doesn't pop in.
  final String? initialAvatarUrl;

  const UserProfileScreen({
    super.key,
    required this.userId,
    this.initialName,
    this.initialAvatarUrl,
  });

  @override
  State<UserProfileScreen> createState() => _UserProfileScreenState();
}

class _UserProfileScreenState extends State<UserProfileScreen> {
  Map<String, dynamic>? _userInfo;
  List<ReportModel> _reports = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = context.read<AuthService>().api;
      final user = await api.getUserById(widget.userId);
      List<ReportModel> reports = const [];
      try {
        reports = await api.getReportsByUser(widget.userId);
      } catch (_) {
        // Reports list is best-effort — a 404 / private profile shouldn't
        // wipe out the rest of the page.
      }
      if (!mounted) return;
      setState(() {
        _userInfo = user;
        _reports = reports;
        _loading = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.userMessage;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _error = 'Could not load profile.';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final name = (_userInfo?['name'] ?? _userInfo?['fullName']) as String? ??
        widget.initialName ??
        'Profile';
    return Scaffold(
      backgroundColor: AppColors.surfaceTint,
      body: SafeArea(
        child: Column(
          children: [
            _buildTopBar(name),
            Expanded(child: _buildBody()),
          ],
        ),
      ),
    );
  }

  Widget _buildTopBar(String name) {
    return Container(
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
                name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w800,
                  fontSize: 18,
                  color: AppColors.primary,
                ),
              ),
            ),
          ),
          // Spacer so the title sits visually centred.
          const SizedBox(width: 48),
        ],
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return Center(child: CircularProgressIndicator(color: AppColors.primary));
    }
    if (_error != null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.wifi_off, color: AppColors.outlineVariant, size: 40),
            const SizedBox(height: 12),
            Text(_error!, style: TextStyle(color: AppColors.onSurfaceVariant)),
            const SizedBox(height: 16),
            GestureDetector(
              onTap: _load,
              child: Text(
                'Retry',
                style: TextStyle(
                  color: AppColors.primary,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
        ),
      );
    }

    final name = (_userInfo?['name'] ?? _userInfo?['fullName'] ?? 'User')
        as String;
    final bio = (_userInfo?['bio'] ?? '') as String;
    final avatarUrl =
        _userInfo?['avatarUrl'] as String? ?? widget.initialAvatarUrl;
    final stats = _userInfo?['contributionStats'] as Map<String, dynamic>?;
    final reportsSubmitted =
        stats?['reportsSubmitted'] ?? _reports.length;
    final routesPlanned = stats?['routesPlanned'] ?? 0;

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildHero(name: name, bio: bio, avatarUrl: avatarUrl),
          const SizedBox(height: 18),
          Row(
            children: [
              Expanded(
                child: _buildStatCard(
                  icon: Icons.assignment_outlined,
                  label: 'REPORTS',
                  value: '$reportsSubmitted',
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _buildStatCard(
                  icon: Icons.route_outlined,
                  label: 'ROUTES',
                  value: '$routesPlanned',
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          if (_reports.isNotEmpty) ...[
            Text(
              'Recent reports',
              style: TextStyle(
                fontFamily: 'Plus Jakarta Sans',
                fontWeight: FontWeight.w800,
                fontSize: 16,
                color: AppColors.onSurface,
              ),
            ),
            const SizedBox(height: 10),
            for (final r in _reports.take(8)) _buildReportTile(r),
          ],
        ],
      ),
    );
  }

  Widget _buildHero({
    required String name,
    required String bio,
    String? avatarUrl,
  }) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(20, 24, 20, 24),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            AppColors.primary.withValues(alpha: 0.10),
            AppColors.successContainer.withValues(alpha: 0.45),
          ],
        ),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
            color: AppColors.primary.withValues(alpha: 0.18)),
      ),
      child: Column(
        children: [
          _buildAvatar(avatarUrl),
          const SizedBox(height: 14),
          Text(
            name,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontFamily: 'Plus Jakarta Sans',
              fontWeight: FontWeight.w800,
              fontSize: 22,
              color: AppColors.onSurface,
            ),
          ),
          if (bio.isNotEmpty) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.symmetric(
                  horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: AppColors.cardSurface.withValues(alpha: 0.85),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Text(
                bio,
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 13,
                  color: AppColors.onSurface,
                  height: 1.4,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildAvatar(String? avatarUrl) {
    return Container(
      width: 96,
      height: 96,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: AppColors.surfaceContainerHigh,
        border: Border.all(color: AppColors.cardSurface, width: 3),
        boxShadow: [
          BoxShadow(
            color: AppColors.shadow,
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: ClipOval(
        child: (avatarUrl != null && avatarUrl.isNotEmpty)
            ? Image.network(
                avatarUrl,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => _avatarFallback(),
              )
            : _avatarFallback(),
      ),
    );
  }

  Widget _avatarFallback() => Center(
        child: Icon(Icons.person, size: 44, color: AppColors.secondary),
      );

  Widget _buildStatCard({
    required IconData icon,
    required String label,
    required String value,
  }) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 16),
      decoration: BoxDecoration(
        color: AppColors.cardSurface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: AppColors.outlineVariant.withValues(alpha: 0.4),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 28,
                height: 28,
                decoration: BoxDecoration(
                  color: AppColors.primary.withValues(alpha: 0.12),
                  shape: BoxShape.circle,
                ),
                alignment: Alignment.center,
                child: Icon(icon, size: 14, color: AppColors.primary),
              ),
              const SizedBox(width: 8),
              Text(
                label,
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.2,
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            value,
            style: TextStyle(
              fontFamily: 'Plus Jakarta Sans',
              fontWeight: FontWeight.w800,
              fontSize: 28,
              height: 1,
              color: AppColors.onSurface,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildReportTile(ReportModel report) {
    final isFixed = report.status == ReportStatus.fixed;
    final isVerified = report.status == ReportStatus.verified;
    final pillBg = isFixed
        ? AppColors.infoContainer
        : isVerified
            ? AppColors.successContainer
            : AppColors.surfaceContainer;
    final pillFg = isFixed
        ? AppColors.info
        : isVerified
            ? AppColors.success
            : AppColors.onSurfaceVariant;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Material(
        color: AppColors.cardSurface,
        borderRadius: BorderRadius.circular(14),
        child: InkWell(
          borderRadius: BorderRadius.circular(14),
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => ReportDetailScreen(report: report),
            ),
          ),
          child: Padding(
            padding:
                const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            child: Row(
              children: [
                Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    color: report.displayColor.withValues(alpha: 0.14),
                    shape: BoxShape.circle,
                  ),
                  alignment: Alignment.center,
                  child: Icon(report.displayIcon,
                      color: report.displayColor, size: 18),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        report.headline,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontWeight: FontWeight.w700,
                          fontSize: 14,
                          color: AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '${report.timeAgo} · ${report.status.label}',
                        style: TextStyle(
                          fontSize: 11,
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: pillBg,
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: Text(
                    report.status.label.toUpperCase(),
                    style: TextStyle(
                      fontSize: 9,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 0.6,
                      color: pillFg,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
