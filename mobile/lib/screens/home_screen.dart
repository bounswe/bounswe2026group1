import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../theme/app_colors.dart';
import '../models/report_model.dart';
import '../services/auth_service.dart';
import 'report_detail_screen.dart';
import 'reports_screen.dart';
import 'profile_screen.dart';
import 'make_report_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _pulseController;
  late Animation<double> _pulseScale;
  late Animation<double> _pulseOpacity;

  late Future<List<ReportModel>> _reportsFuture;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat();
    _pulseScale = Tween<double>(begin: 0.6, end: 2.0).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeOut),
    );
    _pulseOpacity = Tween<double>(begin: 0.5, end: 0.0).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeOut),
    );

    _loadReports();
  }

  void _loadReports() {
    final api = context.read<AuthService>().api;
    _reportsFuture = api.getReports();
  }

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  /// Maps lat/lon of a report to a [0..1] screen fraction.
  /// Uses the full collection bounds, clamped to 10%–90% of the viewport
  /// so markers never land on the very edge.
  static ({double top, double left}) _markerFraction(
    ReportModel r,
    List<ReportModel> all,
  ) {
    if (all.isEmpty) return (top: 0.5, left: 0.5);

    final lats = all.map((e) => e.latitude);
    final lons = all.map((e) => e.longitude);
    final minLat = lats.reduce((a, b) => a < b ? a : b);
    final maxLat = lats.reduce((a, b) => a > b ? a : b);
    final minLon = lons.reduce((a, b) => a < b ? a : b);
    final maxLon = lons.reduce((a, b) => a > b ? a : b);

    // Avoid division by zero when all points share the same coordinate.
    const pad = 0.10;
    const range = 1.0 - 2 * pad;

    final left = minLon == maxLon
        ? 0.5
        : pad + ((r.longitude - minLon) / (maxLon - minLon)) * range;

    // Higher latitude = further north = closer to top of screen (smaller top fraction).
    final top = minLat == maxLat
        ? 0.5
        : pad + (1 - ((r.latitude - minLat) / (maxLat - minLat))) * range;

    return (top: top, left: left);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      body: Stack(
        children: [
          Column(
            children: [
              _buildTopBar(),
              Expanded(
                child: FutureBuilder<List<ReportModel>>(
                  future: _reportsFuture,
                  builder: (context, snapshot) {
                    final reports = snapshot.data ?? [];
                    return LayoutBuilder(
                      builder: (context, constraints) {
                        return Stack(
                          clipBehavior: Clip.none,
                          children: [
                            // City map background
                            Positioned.fill(
                              child: CustomPaint(painter: _CityMapPainter()),
                            ),
                            // Error banner (non-blocking)
                            if (snapshot.hasError)
                              _buildErrorBanner(snapshot.error.toString()),
                            // Loading indicator while waiting
                            if (snapshot.connectionState ==
                                ConnectionState.waiting)
                              const Positioned(
                                top: 80,
                                left: 0,
                                right: 0,
                                child: Center(
                                  child: _LoadingChip(),
                                ),
                              ),
                            // Issue markers
                            ...reports.map(
                              (r) => _buildMarker(context, r, reports, constraints),
                            ),
                            // Pulsing user location (center)
                            _buildUserLocation(constraints),
                            // Floating location bar
                            _buildLocationOverlay(),
                            // Report FAB
                            _buildReportFAB(),
                          ],
                        );
                      },
                    );
                  },
                ),
              ),
            ],
          ),
          // Floating bottom nav
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: _buildBottomNav(context, 0),
          ),
        ],
      ),
    );
  }

  // ─── Top app bar ───────────────────────────────────────────────────────────

  Widget _buildTopBar() {
    return SafeArea(
      bottom: false,
      child: Container(
        color: AppColors.surface.withOpacity(0.92),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        child: Row(
          children: [
            IconButton(
              icon: const Icon(Icons.menu, color: AppColors.primary),
              onPressed: () {},
            ),
            const Expanded(
              child: Center(
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
            ),
            IconButton(
              icon: const Icon(Icons.search, color: AppColors.primary),
              onPressed: () {},
            ),
          ],
        ),
      ),
    );
  }

  // ─── Marker ────────────────────────────────────────────────────────────────

  Widget _buildMarker(
    BuildContext context,
    ReportModel report,
    List<ReportModel> all,
    BoxConstraints constraints,
  ) {
    final frac = _markerFraction(report, all);
    final top = constraints.maxHeight * frac.top - 68;
    final left = constraints.maxWidth * frac.left - 44;

    return Positioned(
      top: top,
      left: left,
      child: GestureDetector(
        onTap: () => Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => ReportDetailScreen(report: report),
          ),
        ),
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.all(4),
              decoration: BoxDecoration(
                color: Colors.white,
                shape: BoxShape.circle,
                border: Border.all(
                  color: AppColors.primary.withOpacity(0.12),
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.14),
                    blurRadius: 10,
                    offset: const Offset(0, 3),
                  ),
                ],
              ),
              child: Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: report.tag.color,
                  shape: BoxShape.circle,
                ),
                child: Icon(report.tag.icon, color: Colors.white, size: 18),
              ),
            ),
            const SizedBox(height: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.93),
                borderRadius: BorderRadius.circular(10),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.1),
                    blurRadius: 8,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: Text(
                report.tag.label,
                style: const TextStyle(
                  fontSize: 9,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1.1,
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ─── Error banner ──────────────────────────────────────────────────────────

  Widget _buildErrorBanner(String error) {
    return Positioned(
      top: 80,
      left: 20,
      right: 20,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: const Color(0xFFF95630).withOpacity(0.92),
          borderRadius: BorderRadius.circular(14),
        ),
        child: Row(
          children: [
            const Icon(Icons.wifi_off, color: Colors.white, size: 18),
            const SizedBox(width: 10),
            const Expanded(
              child: Text(
                'Could not load reports. Check your connection.',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
            GestureDetector(
              onTap: () => setState(_loadReports),
              child: const Icon(Icons.refresh, color: Colors.white, size: 18),
            ),
          ],
        ),
      ),
    );
  }

  // ─── Pulsing user location ─────────────────────────────────────────────────

  Widget _buildUserLocation(BoxConstraints constraints) {
    return Positioned(
      top: constraints.maxHeight * 0.5 - 24,
      left: constraints.maxWidth * 0.5 - 24,
      child: AnimatedBuilder(
        animation: _pulseController,
        builder: (context, child) {
          return SizedBox(
            width: 48,
            height: 48,
            child: Stack(
              alignment: Alignment.center,
              children: [
                Transform.scale(
                  scale: _pulseScale.value,
                  child: Container(
                    width: 48,
                    height: 48,
                    decoration: BoxDecoration(
                      color: AppColors.primary.withOpacity(
                        _pulseOpacity.value * 0.35,
                      ),
                      shape: BoxShape.circle,
                    ),
                  ),
                ),
                Container(
                  width: 20,
                  height: 20,
                  decoration: BoxDecoration(
                    color: AppColors.primary,
                    shape: BoxShape.circle,
                    border: Border.all(color: Colors.white, width: 3),
                    boxShadow: [
                      BoxShadow(
                        color: AppColors.primary.withOpacity(0.4),
                        blurRadius: 8,
                        offset: const Offset(0, 2),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  // ─── Location overlay ──────────────────────────────────────────────────────

  Widget _buildLocationOverlay() {
    return Positioned(
      top: 18,
      left: 18,
      right: 18,
      child: Row(
        children: [
          Expanded(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.92),
                borderRadius: BorderRadius.circular(16),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.1),
                    blurRadius: 20,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: const Row(
                children: [
                  Icon(Icons.location_on, color: AppColors.primary, size: 20),
                  SizedBox(width: 10),
                  Text(
                    'Mission District, San Francisco',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(width: 12),
          GestureDetector(
            onTap: () {},
            child: Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AppColors.primary,
                borderRadius: BorderRadius.circular(16),
                boxShadow: [
                  BoxShadow(
                    color: AppColors.primary.withOpacity(0.32),
                    blurRadius: 14,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: const Icon(Icons.tune, color: Colors.white, size: 22),
            ),
          ),
        ],
      ),
    );
  }

  // ─── Report FAB ────────────────────────────────────────────────────────────

  Widget _buildReportFAB() {
    return Positioned(
      bottom: 96,
      left: 20,
      right: 20,
      child: GestureDetector(
        onTap: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => const MakeReportScreen()),
        ),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 18),
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
                blurRadius: 32,
                offset: const Offset(0, 12),
              ),
            ],
          ),
          child: const Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.add_circle, color: AppColors.onPrimary, size: 26),
              SizedBox(width: 10),
              Text(
                'Report an Issue',
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w700,
                  fontSize: 17,
                  color: AppColors.onPrimary,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ─── Bottom nav ────────────────────────────────────────────────────────────

  Widget _buildBottomNav(BuildContext context, int activeIndex) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
      decoration: BoxDecoration(
        color: AppColors.surface.withOpacity(0.88),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.06),
            blurRadius: 32,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _buildNavItem(icon: Icons.map, label: 'Home', active: true, onTap: () {}),
          _buildNavItem(
            icon: Icons.assignment_outlined,
            label: 'Reports',
            active: false,
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const ReportsScreen()),
            ),
          ),
          _buildNavItem(
            icon: Icons.person_outline,
            label: 'Profile',
            active: false,
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const ProfileScreen()),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildNavItem({
    required IconData icon,
    required String label,
    required bool active,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 8),
        decoration: BoxDecoration(
          color: active ? AppColors.primary : Colors.transparent,
          borderRadius: BorderRadius.circular(999),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              color: active ? Colors.white : AppColors.secondary,
              size: 22,
            ),
            const SizedBox(height: 3),
            Text(
              label,
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.8,
                color: active ? Colors.white : AppColors.secondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Loading chip ──────────────────────────────────────────────────────────────

class _LoadingChip extends StatelessWidget {
  const _LoadingChip();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.92),
        borderRadius: BorderRadius.circular(999),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.08),
            blurRadius: 12,
          ),
        ],
      ),
      child: const Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: 14,
            height: 14,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              color: AppColors.primary,
            ),
          ),
          SizedBox(width: 10),
          Text(
            'Loading reports…',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

// ─── City-grid map background ──────────────────────────────────────────────────

class _CityMapPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(
      Rect.fromLTWH(0, 0, size.width, size.height),
      Paint()..color = const Color(0xFFECEDEE),
    );

    final block = Paint()..color = const Color(0xFFDFE1E2);
    final street = Paint()..color = const Color(0xFFF8F9FA);
    final park = Paint()..color = const Color(0xFFD0EBD0);

    const minor = 56.0;
    const streetW = 7.0;
    const majorSpacing = 168.0;
    const majorW = 13.0;

    for (double x = 0; x < size.width; x += minor) {
      for (double y = 0; y < size.height; y += minor) {
        canvas.drawRect(
          Rect.fromLTWH(
            x + streetW / 2,
            y + streetW / 2,
            minor - streetW,
            minor - streetW,
          ),
          block,
        );
      }
    }

    const parks = [
      Rect.fromLTWH(56, 112, 96, 48),
      Rect.fromLTWH(224, 28, 88, 48),
      Rect.fromLTWH(168, 224, 104, 48),
    ];
    for (final r in parks) {
      canvas.drawRect(r, park);
    }

    for (double y = 0; y < size.height; y += minor) {
      canvas.drawRect(Rect.fromLTWH(0, y, size.width, streetW), street);
    }
    for (double x = 0; x < size.width; x += minor) {
      canvas.drawRect(Rect.fromLTWH(x, 0, streetW, size.height), street);
    }
    for (double y = 0; y < size.height; y += majorSpacing) {
      canvas.drawRect(Rect.fromLTWH(0, y, size.width, majorW), street);
    }
    for (double x = 0; x < size.width; x += majorSpacing) {
      canvas.drawRect(Rect.fromLTWH(x, 0, majorW, size.height), street);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
