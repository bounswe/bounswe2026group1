import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'screens/login_screen.dart';
import 'screens/register_screen.dart';
import 'screens/home_screen.dart';
import 'screens/reports_screen.dart';
import 'screens/profile_screen.dart';
import 'services/auth_service.dart';
import 'services/sse_service.dart';
import 'services/theme_service.dart';
import 'theme/app_colors.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final auth = AuthService();
  await auth.init();
  final theme = ThemeService();
  await theme.init();
  // Seed AppColors before the first frame so any widget reading colors
  // synchronously during build sees the correct mode.
  AppColors.setBrightness(theme.resolveAgainstPlatform(
    WidgetsBinding.instance.platformDispatcher.platformBrightness,
  ));
  final sse = SseService();
  sse.connect();
  runApp(MapcessApp(auth: auth, sse: sse, theme: theme));
}

class MapcessApp extends StatelessWidget {
  final AuthService auth;
  final SseService sse;
  final ThemeService theme;

  const MapcessApp({
    super.key,
    required this.auth,
    required this.sse,
    required this.theme,
  });

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: auth),
        ChangeNotifierProvider.value(value: sse),
        ChangeNotifierProvider.value(value: theme),
      ],
      child: Consumer<ThemeService>(
        builder: (context, themeService, _) {
          return MaterialApp(
            title: 'Mapcess',
            debugShowCheckedModeBanner: false,
            themeMode: themeService.mode,
            theme: _buildTheme(Brightness.light),
            darkTheme: _buildTheme(Brightness.dark),
            builder: (ctx, child) {
              // Resolve brightness from ThemeService directly — Theme.of(ctx)
              // returns interpolated values during MaterialApp's
              // AnimatedTheme transition, which would briefly pin AppColors
              // to the old brightness and render reversed colors.
              AppColors.setBrightness(themeService.resolveAgainstPlatform(
                MediaQuery.platformBrightnessOf(ctx),
              ));
              return child!;
            },
            home: const AuthShell(),
          );
        },
      ),
    );
  }

  ThemeData _buildTheme(Brightness brightness) {
    return ThemeData(
      brightness: brightness,
      colorScheme: ColorScheme.fromSeed(
        seedColor: AppColors.primary,
        brightness: brightness,
      ),
      fontFamily: 'Inter',
      useMaterial3: true,
      scaffoldBackgroundColor:
          brightness == Brightness.dark ? const Color(0xFF111413) : null,
    );
  }
}

// ── Main shell ─────────────────────────────────────────────────────────────────
// Wraps the three main tabs with a stable bottom nav and slide animations.
// Higher-index transitions (Home→Reports, Home→Profile, Reports→Profile) slide
// the new page in from the left; lower-index transitions slide from the right.

class MainShell extends StatefulWidget {
  final int initialTab;
  const MainShell({super.key, this.initialTab = 0});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell>
    with SingleTickerProviderStateMixin {
  late int _current;
  int _previous = 0;
  bool _animating = false;
  double _slideOffset = 0.0;
  late final AnimationController _ctrl;
  late final Animation<double> _anim;

  @override
  void initState() {
    super.initState();
    _current = widget.initialTab;
    _previous = widget.initialTab;
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
    FocusManager.instance.primaryFocus?.unfocus();
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
    // Subscribe to theme changes so the persistent shell (top bar + bottom
    // nav) repaints with new AppColors values immediately when the user
    // flips light/dark.
    context.watch<ThemeService>();

    final pages = [
      HomeScreen(onTabSwitch: _switchTab),
      ReportsScreen(onTabSwitch: _switchTab),
      ProfileScreen(onTabSwitch: _switchTab),
    ];

    // Reports & Profile screens sit under the overlay top bar and above the
    // bottom nav, so their SafeArea must account for both bar heights in
    // addition to system insets.
    const double topBarContentHeight = 68.0;
    const double bottomNavHeight = 88.0;
    final mq = MediaQuery.of(context);

    // Wrap non-home pages so their SafeArea starts below the top bar and
    // ends above the bottom nav bar.
    Widget wrapPage(int i) {
      if (i == 0) return pages[i];
      return MediaQuery(
        data: mq.copyWith(
          padding: mq.padding.copyWith(
            top: mq.padding.top + topBarContentHeight,
            bottom: mq.padding.bottom + bottomNavHeight,
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
            child: IgnorePointer(
              ignoring: _current == 0,
              child: AnimatedOpacity(
                opacity: _current != 0 ? 1.0 : 0.0,
                duration: const Duration(milliseconds: 220),
                child: SafeArea(
                  bottom: false,
                  child: _buildSharedTopBar(),
                ),
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

  void _showSettingsMenu(BuildContext context) {
    final auth = context.read<AuthService>();
    showModalBottomSheet(
      context: context,
      // Sheet bg is painted by the inner Consumer so it tracks theme changes.
      backgroundColor: Colors.transparent,
      builder: (_) => Consumer<ThemeService>(
        builder: (context, _, __) {
          return Container(
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLowest,
              borderRadius: const BorderRadius.vertical(
                top: Radius.circular(20),
              ),
            ),
            child: SafeArea(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const SizedBox(height: 8),
                  Container(
                    width: 40,
                    height: 4,
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerHigh,
                      borderRadius: BorderRadius.circular(999),
                    ),
                  ),
                  const SizedBox(height: 12),
                  const _ThemeModeTile(),
                  Divider(
                    height: 1,
                    thickness: 1,
                    color: AppColors.surfaceContainerHigh,
                  ),
                  if (auth.isAuthenticated)
                    ListTile(
                      leading: Icon(Icons.logout, color: AppColors.error),
                      title: Text(
                        'Log Out',
                        style: TextStyle(
                          color: AppColors.error,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      onTap: () async {
                        Navigator.pop(context);
                        await auth.logout();
                        if (context.mounted) {
                          Navigator.pushAndRemoveUntil(
                            context,
                            MaterialPageRoute(
                                builder: (_) => const AuthShell()),
                            (r) => false,
                          );
                        }
                      },
                    )
                  else
                    ListTile(
                      leading: Icon(Icons.login, color: AppColors.primary),
                      title: Text(
                        'Sign In',
                        style: TextStyle(
                          color: AppColors.primary,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      onTap: () {
                        Navigator.pop(context);
                        Navigator.pushAndRemoveUntil(
                          context,
                          MaterialPageRoute(
                              builder: (_) => const AuthShell()),
                          (r) => false,
                        );
                      },
                    ),
                  const SizedBox(height: 8),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildSharedTopBar() {
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
            icon: Icon(Icons.menu, color: AppColors.onSurface),
            onPressed: () {},
          ),
          Expanded(
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
            icon: Icon(Icons.settings_outlined, color: AppColors.onSurface),
            onPressed: () => _showSettingsMenu(context),
          ),
        ],
      ),
    );
  }

  Widget _buildBottomNav() {
    return Hero(
      tag: 'app_bottom_nav',
      flightShuttleBuilder: (ctx, anim, dir, from, toCtx) =>
          (toCtx.widget as Hero).child,
      child: Material(
        type: MaterialType.transparency,
        child: _buildNavContent(),
      ),
    );
  }

  Widget _buildNavContent() {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
      decoration: BoxDecoration(
        color: AppColors.surface.withValues(alpha: 0.88),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        boxShadow: [
          BoxShadow(
            color: AppColors.shadow,
            blurRadius: 32,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _navItem(Icons.map, Icons.map_outlined, 'Home', 0),
          _navItem(Icons.assignment, Icons.assignment_outlined, 'Feed', 1),
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
              color: active ? AppColors.onPrimarySolid : AppColors.secondary,
              size: 22,
            ),
            const SizedBox(height: 3),
            Text(
              label,
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.8,
                color:
                    active ? AppColors.onPrimarySolid : AppColors.secondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Auth shell ─────────────────────────────────────────────────────────────────
// Mirrors MainShell: slides between Login (0) and Register (1) with the same
// global bottom nav bar positioned over the content.

class AuthShell extends StatefulWidget {
  final int initialTab;
  const AuthShell({super.key, this.initialTab = 0});

  @override
  State<AuthShell> createState() => _AuthShellState();
}

class _AuthShellState extends State<AuthShell>
    with SingleTickerProviderStateMixin {
  late int _current;
  int _previous = 0;
  bool _animating = false;
  double _slideOffset = 0.0;
  late final AnimationController _ctrl;
  late final Animation<double> _anim;

  @override
  void initState() {
    super.initState();
    _current = widget.initialTab;
    _previous = widget.initialTab;
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
    FocusManager.instance.primaryFocus?.unfocus();
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
    // Subscribe so the auth shell's bottom nav repaints when the user flips
    // light/dark from the settings sheet.
    context.watch<ThemeService>();

    const double bottomNavHeight = 88.0;
    final mq = MediaQuery.of(context);

    final pages = [
      LoginScreen(onTabSwitch: _switchTab),
      RegisterScreen(onTabSwitch: _switchTab),
    ];

    Widget wrapPage(int i) {
      return MediaQuery(
        data: mq.copyWith(
          padding: mq.padding.copyWith(
            bottom: mq.padding.bottom + bottomNavHeight,
          ),
        ),
        child: pages[i],
      );
    }

    return Scaffold(
      backgroundColor: AppColors.surface,
      resizeToAvoidBottomInset: false,
      body: Stack(
        children: [
          // ── Sliding content ────────────────────────────────────────────
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
          // ── Global bottom nav ──────────────────────────────────────────
          Positioned(
            bottom: mq.viewInsets.bottom,
            left: 0,
            right: 0,
            child: _buildNavContent(),
          ),
        ],
      ),
    );
  }

  Widget _buildNavContent() {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
      decoration: BoxDecoration(
        color: AppColors.surface.withValues(alpha: 0.88),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        boxShadow: [
          BoxShadow(
            color: AppColors.shadow,
            blurRadius: 32,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _navItem(Icons.login, Icons.login, 'SIGN IN', 0),
          _navItem(Icons.person_add, Icons.person_add_outlined, 'SIGN UP', 1),
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
              color: active ? AppColors.onPrimarySolid : AppColors.secondary,
              size: 22,
            ),
            const SizedBox(height: 3),
            Text(
              label,
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.8,
                color:
                    active ? AppColors.onPrimarySolid : AppColors.secondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Theme mode picker ──────────────────────────────────────────────────────
// Compact segmented control for choosing System / Light / Dark from the
// settings bottom sheet.

class _ThemeModeTile extends StatelessWidget {
  const _ThemeModeTile();

  @override
  Widget build(BuildContext context) {
    // Watch (not read) so tapping a segment immediately repaints the tile
    // — the sheet is in its own route and won't rebuild via the parent.
    final theme = context.watch<ThemeService>();
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(left: 4, bottom: 8),
            child: Text(
              'Appearance',
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: AppColors.onSurfaceVariant,
                letterSpacing: 0.4,
              ),
            ),
          ),
          Container(
            padding: const EdgeInsets.all(4),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainer,
              borderRadius: BorderRadius.circular(14),
            ),
            child: Row(
              children: [
                _segment(
                    context, ThemeMode.system, Icons.brightness_auto, 'System'),
                _segment(context, ThemeMode.light, Icons.light_mode_outlined,
                    'Light'),
                _segment(
                    context, ThemeMode.dark, Icons.dark_mode_outlined, 'Dark'),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _segment(
    BuildContext context,
    ThemeMode mode,
    IconData icon,
    String label,
  ) {
    final theme = context.read<ThemeService>();
    final selected = theme.mode == mode;
    return Expanded(
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => theme.setMode(mode),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            color: selected ? AppColors.raisedSurface : Colors.transparent,
            borderRadius: BorderRadius.circular(10),
            boxShadow: selected
                ? [
                    BoxShadow(
                      color: AppColors.shadow,
                      blurRadius: 6,
                      offset: const Offset(0, 1),
                    ),
                  ]
                : null,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                icon,
                size: 20,
                color:
                    selected ? AppColors.primary : AppColors.onSurfaceVariant,
              ),
              const SizedBox(height: 4),
              Text(
                label,
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: selected
                      ? AppColors.onSurface
                      : AppColors.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
