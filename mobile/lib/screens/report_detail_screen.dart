import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../theme/app_colors.dart';
import '../models/report_model.dart';
import '../services/auth_service.dart';

class ReportDetailScreen extends StatefulWidget {
  final ReportModel report;

  const ReportDetailScreen({super.key, required this.report});

  @override
  State<ReportDetailScreen> createState() => _ReportDetailScreenState();
}

class _ReportDetailScreenState extends State<ReportDetailScreen> {
  ReportModel get report => widget.report;

  String? _fetchedUsername;

  @override
  void initState() {
    super.initState();
    // If the model already carries a username (mock data / enriched response),
    // no need to fetch. Otherwise, look it up.
    if (report.username == null) {
      _loadUsername();
    }
  }

  Future<void> _loadUsername() async {
    final name = await context.read<AuthService>().api.getUserName(report.userId);
    if (mounted && name != null) {
      setState(() => _fetchedUsername = name);
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
                      const SizedBox(height: 24),
                      _buildCommunityConsensus(),
                      const SizedBox(height: 24),
                      _buildMetadataRow(),
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
            child: _buildBottomSection(context),
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
        color: AppColors.surface.withOpacity(0.92),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        child: Row(
          children: [
            IconButton(
              icon: const Icon(Icons.arrow_back, color: AppColors.primary),
              onPressed: () => Navigator.pop(context),
            ),
            const Expanded(
              child: Text(
                'Mapcess',
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w800,
                  fontSize: 24,
                  color: AppColors.primary,
                  letterSpacing: -0.5,
                ),
              ),
            ),
            IconButton(
              icon: const Icon(Icons.search, color: AppColors.onSurfaceVariant),
              onPressed: () {},
            ),
          ],
        ),
      ),
    );
  }

  // ─── Hero image / placeholder ───────────────────────────────────────────────

  Widget _buildHeroSection() {
    return Stack(
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(20),
          child: SizedBox(
            width: double.infinity,
            height: 240,
            child: report.mediaUrls.isNotEmpty
                ? Image.network(
                    report.mediaUrls.first,
                    fit: BoxFit.cover,
                    errorBuilder: (context, error, stack) => _heroPlaceholder(),
                  )
                : _heroPlaceholder(),
          ),
        ),
        // Status badge
        Positioned(
          top: 14,
          right: 14,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
            decoration: BoxDecoration(
              color: report.status.color.withOpacity(0.15),
              borderRadius: BorderRadius.circular(999),
              border: Border.all(color: report.status.color.withOpacity(0.4)),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 7,
                  height: 7,
                  decoration: BoxDecoration(
                    color: report.status.color,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 6),
                Text(
                  report.status.label,
                  style: TextStyle(
                    fontFamily: 'Plus Jakarta Sans',
                    fontWeight: FontWeight.w700,
                    fontSize: 13,
                    color: report.status.color,
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
          painter: _ImagePlaceholderPainter(color: report.tag.color),
        ),
        Center(
          child: Icon(
            report.tag.icon,
            size: 80,
            color: report.tag.color.withOpacity(0.25),
          ),
        ),
      ],
    );
  }

  // ─── Title & reporter ───────────────────────────────────────────────────────

  Widget _buildTitleSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '${report.tag.label} – Report #${report.reportId}',
          style: const TextStyle(
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
                color: report.tag.color.withOpacity(0.12),
                shape: BoxShape.circle,
              ),
              child: Icon(
                Icons.person_outline,
                color: report.tag.color,
                size: 20,
              ),
            ),
            const SizedBox(width: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _displayUsername,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: AppColors.onSurface,
                  ),
                ),
                Text(
                  'Reported ${report.timeAgo}',
                  style: const TextStyle(
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
        // Description card
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
                    const Icon(
                      Icons.description_outlined,
                      color: AppColors.primary,
                      size: 18,
                    ),
                    const SizedBox(width: 8),
                    const Text(
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
                  style: const TextStyle(
                    fontSize: 13,
                    color: AppColors.onSurfaceVariant,
                    height: 1.55,
                  ),
                ),
                const SizedBox(height: 12),
                // Tag chip
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 5,
                  ),
                  decoration: BoxDecoration(
                    color: const Color(0xFFCFE6F2),
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: Text(
                    report.tag.label,
                    style: const TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFF40555F),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(width: 12),
        // Location card
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
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        CustomPaint(painter: _MiniMapPainter()),
                        const Center(
                          child: Icon(
                            Icons.location_on,
                            color: AppColors.primary,
                            size: 32,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                Text(
                  '${report.latitude.toStringAsFixed(4)}, ${report.longitude.toStringAsFixed(4)}',
                  style: const TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                    color: AppColors.onSurface,
                    letterSpacing: 0.3,
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 3),
                const Text(
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

  Widget _buildCommunityConsensus() {
    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: const Color(0xFFF0F1F1),
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
                  const Text(
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
                    '${report.totalVotes} ${report.totalVotes == 1 ? 'person has' : 'people have'} voted.',
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
              // Live vote counts
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  _voteCount(Icons.thumb_up, report.agrees, AppColors.primary),
                  const SizedBox(height: 4),
                  _voteCount(Icons.thumb_down, report.disagrees, AppColors.outline),
                ],
              ),
            ],
          ),
          const SizedBox(height: 16),
          // Agree / Disagree buttons (display only – no vote endpoint yet)
          Row(
            children: [
              Expanded(
                child: _voteButton(
                  icon: Icons.thumb_up_rounded,
                  label: 'Agree',
                  filled: true,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _voteButton(
                  icon: Icons.thumb_down_outlined,
                  label: 'Disagree',
                  filled: false,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          // Progress bar
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'VALIDATION PROGRESS',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.0,
                  color: AppColors.onSurfaceVariant,
                ),
              ),
              Text(
                '${report.consensusPercent}% Agree',
                style: const TextStyle(
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
                      (report.consensusPercent / 100).clamp(0.0, 1.0),
                  height: 10,
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [Color(0xFF9DF197), AppColors.primary],
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
    required bool filled,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 12),
      decoration: BoxDecoration(
        color: filled ? AppColors.primary : AppColors.surfaceContainerHigh,
        borderRadius: BorderRadius.circular(999),
        boxShadow: filled
            ? [
                BoxShadow(
                  color: AppColors.primary.withOpacity(0.28),
                  blurRadius: 12,
                  offset: const Offset(0, 4),
                ),
              ]
            : null,
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            icon,
            color: filled ? AppColors.onPrimary : AppColors.onSurface,
            size: 16,
          ),
          const SizedBox(width: 8),
          Text(
            label,
            style: TextStyle(
              fontWeight: FontWeight.w700,
              color: filled ? AppColors.onPrimary : AppColors.onSurface,
              fontSize: 14,
            ),
          ),
        ],
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
          const Text(
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
          _infoRow(Icons.category_outlined, 'Category', report.tag.label),
          _infoRow(Icons.circle_outlined, 'Status', report.status.label),
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
            style: const TextStyle(
              fontSize: 12,
              color: AppColors.onSurfaceVariant,
            ),
          ),
          const Spacer(),
          Text(
            value,
            style: const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: AppColors.onSurface,
            ),
          ),
        ],
      ),
    );
  }

  // ─── Bottom section (Follow + nav) ──────────────────────────────────────────

  Widget _buildBottomSection(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 0),
      decoration: BoxDecoration(
        color: AppColors.surface.withOpacity(0.92),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.06),
            blurRadius: 24,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          GestureDetector(
            onTap: () {},
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(vertical: 16),
              decoration: BoxDecoration(
                color: AppColors.primary,
                borderRadius: BorderRadius.circular(16),
                boxShadow: [
                  BoxShadow(
                    color: AppColors.primary.withOpacity(0.22),
                    blurRadius: 18,
                    offset: const Offset(0, 6),
                  ),
                ],
              ),
              child: const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    Icons.notifications_active_outlined,
                    color: AppColors.onPrimary,
                    size: 20,
                  ),
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
          ),
          const SizedBox(height: 12),
          _buildBottomNav(context),
        ],
      ),
    );
  }

  Widget _buildBottomNav(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 20),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _navItem(
            Icons.map_outlined,
            'Home',
            false,
            () => Navigator.popUntil(context, (r) => r.isFirst),
          ),
          _navItem(Icons.assignment, 'Reports', true, () {}),
          _navItem(Icons.person_outline, 'Profile', false, () {}),
        ],
      ),
    );
  }

  Widget _navItem(
    IconData icon,
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
          color: active ? const Color(0xFFDCF5DC) : Colors.transparent,
          borderRadius: BorderRadius.circular(999),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              color: active ? AppColors.primary : AppColors.secondary,
              size: 22,
            ),
            const SizedBox(height: 3),
            Text(
              label,
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.8,
                color: active ? AppColors.primary : AppColors.secondary,
              ),
            ),
          ],
        ),
      ),
    );
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

class _MiniMapPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(
      Rect.fromLTWH(0, 0, size.width, size.height),
      Paint()..color = const Color(0xFFECEDEE),
    );
    final block = Paint()..color = const Color(0xFFDFE1E2);
    final street = Paint()..color = Colors.white;
    const cell = 18.0;
    const sw = 3.0;
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
