import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'screens/login_screen.dart';
import 'screens/home_screen.dart';
import 'screens/reports_screen.dart';
import 'screens/profile_screen.dart';
import 'services/auth_service.dart';
import 'theme/app_colors.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final auth = AuthService();
  await auth.init();
  runApp(MapcessApp(auth: auth));
}

class MapcessApp extends StatelessWidget {
  final AuthService auth;

  const MapcessApp({super.key, required this.auth});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider.value(
      value: auth,
      child: MaterialApp(
        title: 'Mapcess',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF176a21)),
          fontFamily: 'Inter',
          useMaterial3: true,
        ),
        home: const LoginScreen(),
      ),
    );
  }
}

// ── Main shell ─────────────────────────────────────────────────────────────────
// Wraps the three main tabs with a stable bottom nav and slide animations.
// Higher-index transitions (Home→Reports, Home→Profile, Reports→Profile) slide
// the new page in from the left; lower-index transitions slide from the right.

class MainShell extends StatefulWidget {
  const MainShell({super.key});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell>
    with SingleTickerProviderStateMixin {
  int _current = 0;
  int _previous = 0;
  bool _animating = false;
  double _slideOffset = 0.0;
  late final AnimationController _ctrl;
  late final Animation<double> _anim;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 260),
    );
    _anim = CurvedAnimation(parent: _ctrl, curve: Curves.easeInOut);
    _ctrl.value = 1.0;
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  void _switchTab(int newIdx) {
    if (newIdx == _current) return;
    _slideOffset = newIdx > _current ? 1.0 : -1.0;
    setState(() {
      _previous = _current;
      _current = newIdx;
      _animating = true;
    });
    _ctrl.forward(from: 0).then((_) {
      if (mounted) setState(() => _animating = false);
    });
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      HomeScreen(onTabSwitch: _switchTab),
      ReportsScreen(onTabSwitch: _switchTab),
      ProfileScreen(onTabSwitch: _switchTab),
    ];

    // Reports & Profile screens sit under the overlay top bar, so their
    // SafeArea must account for the bar's content height (68 px) in addition
    // to the system status-bar inset.
    const double topBarContentHeight = 68.0;
    final mq = MediaQuery.of(context);

    // Wrap non-home pages so their SafeArea starts below the top bar.
    Widget wrapPage(int i) {
      if (i == 0) return pages[i];
      return MediaQuery(
        data: mq.copyWith(
          padding: mq.padding.copyWith(
            top: mq.padding.top + topBarContentHeight,
          ),
        ),
        child: pages[i],
      );
    }

    return Scaffold(
      body: Stack(
        children: [
          // ── Sliding content (full height) ──────────────────────────────
          AnimatedBuilder(
            animation: _anim,
            builder: (ctx, _) {
              final w = MediaQuery.of(ctx).size.width;
              return Stack(
                children: [
                  for (int i = 0; i < pages.length; i++)
                    if (_animating && i == _previous)
                      Transform.translate(
                        offset: Offset(-_slideOffset * w * _anim.value, 0),
                        child: wrapPage(i),
                      )
                    else if (i == _current)
                      Transform.translate(
                        offset: Offset(
                          _animating
                              ? _slideOffset * w * (1.0 - _anim.value)
                              : 0,
                          0,
                        ),
                        child: wrapPage(i),
                      )
                    else
                      Offstage(child: wrapPage(i)),
                ],
              );
            },
          ),
          // ── Top bar: fades in/out, never shifts layout ──────────────────
          Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: AnimatedOpacity(
              opacity: _current != 0 ? 1.0 : 0.0,
              duration: const Duration(milliseconds: 220),
              child: SafeArea(
                bottom: false,
                child: _buildSharedTopBar(),
              ),
            ),
          ),
          // ── Stable bottom nav ───────────────────────────────────────────
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: _buildBottomNav(),
          ),
        ],
      ),
    );
  }

  Widget _buildSharedTopBar() {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFFF4F7F4),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha:0.06),
            blurRadius: 6,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 10),
      child: Row(
        children: [
          IconButton(
            icon: const Icon(Icons.menu, color: AppColors.onSurface),
            onPressed: () {},
          ),
          const Expanded(
            child: Center(
              child: Text(
                'Mapcess',
                style: TextStyle(
                  fontFamily: 'Plus Jakarta Sans',
                  fontWeight: FontWeight.w800,
                  fontSize: 20,
                  color: AppColors.primary,
                ),
              ),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.settings_outlined, color: AppColors.onSurface),
            onPressed: () {},
          ),
        ],
      ),
    );
  }

  Widget _buildBottomNav() {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
      decoration: BoxDecoration(
        color: AppColors.surface.withValues(alpha:0.88),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha:0.06),
            blurRadius: 32,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _navItem(Icons.map, Icons.map_outlined, 'Home', 0),
          _navItem(Icons.assignment, Icons.assignment_outlined, 'Reports', 1),
          _navItem(Icons.person, Icons.person_outline, 'Profile', 2),
        ],
      ),
    );
  }

  Widget _navItem(
    IconData activeIcon,
    IconData inactiveIcon,
    String label,
    int idx,
  ) {
    final active = _current == idx;
    return GestureDetector(
      onTap: () => _switchTab(idx),
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
              active ? activeIcon : inactiveIcon,
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
