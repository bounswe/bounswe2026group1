import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'models/notification_model.dart';
import 'screens/login_screen.dart';
import 'screens/register_screen.dart';
import 'screens/home_screen.dart';
import 'screens/report_detail_screen.dart';
import 'screens/reports_screen.dart';
import 'screens/profile_screen.dart';
import 'screens/user_search_screen.dart';
import 'services/auth_service.dart';
import 'services/notification_service.dart';
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
  final notifications = NotificationService(auth);
  runApp(MapcessApp(
    auth: auth,
    sse: sse,
    theme: theme,
    notifications: notifications,
  ));
}

class MapcessApp extends StatelessWidget {
  final AuthService auth;
  final SseService sse;
  final ThemeService theme;
  final NotificationService notifications;

  const MapcessApp({
    super.key,
    required this.auth,
    required this.sse,
    required this.theme,
    required this.notifications,
  });

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: auth),
        ChangeNotifierProvider.value(value: sse),
        ChangeNotifierProvider.value(value: theme),
        ChangeNotifierProvider.value(value: notifications),
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

  void _openUserSearch(BuildContext context) {
    // The /api/users/search endpoint requires a bearer token — guests would
    // just hit a 401, so short-circuit with the same login-required prompt
    // used by the report FAB instead of opening a doomed screen.
    final auth = context.read<AuthService>();
    if (!auth.isAuthenticated) {
      _showLoginRequiredDialog(context);
      return;
    }
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => const UserSearchScreen()),
    );
  }

  void _showLoginRequiredDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Text(
          'Sign In Required',
          style: TextStyle(
            fontFamily: 'Plus Jakarta Sans',
            fontWeight: FontWeight.w700,
            color: AppColors.onSurface,
          ),
        ),
        content: Text(
          'You need to log in to use this feature.',
          style: TextStyle(color: AppColors.onSurfaceVariant),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text('Cancel',
                style: TextStyle(color: AppColors.outline)),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
              Navigator.pushAndRemoveUntil(
                context,
                MaterialPageRoute(builder: (_) => const AuthShell()),
                (r) => false,
              );
            },
            child: Text(
              'Sign In',
              style: TextStyle(
                  color: AppColors.primary, fontWeight: FontWeight.w700),
            ),
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

  void _showNotificationsSheet(BuildContext context) {
    // Re-fetch on open so the sheet always reflects what the server has.
    context.read<NotificationService>().refresh();
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (_) => _NotificationsSheet(
        onOpenReport: (reportId) => _openReportFromNotification(context, reportId),
      ),
    );
  }

  Future<void> _openReportFromNotification(
    BuildContext sourceContext,
    int reportId,
  ) async {
    // The sheet pops itself before this runs. Resolve the report through
    // the api so we can push the existing ReportDetailScreen, which expects
    // a full ReportModel rather than just an id.
    final auth = sourceContext.read<AuthService>();
    try {
      final report = await auth.api.getReport(reportId);
      if (!sourceContext.mounted) return;
      Navigator.of(sourceContext).push(
        MaterialPageRoute(
          builder: (_) => ReportDetailScreen(report: report),
        ),
      );
    } catch (_) {
      if (!sourceContext.mounted) return;
      ScaffoldMessenger.of(sourceContext).showSnackBar(
        const SnackBar(content: Text('Could not load that report.')),
      );
    }
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
          _NotificationBellButton(
            onTap: () => _showNotificationsSheet(context),
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
            icon: Icon(Icons.search, color: AppColors.onSurface),
            tooltip: 'Search users',
            onPressed: () => _openUserSearch(context),
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

// ── Notification bell + sheet ───────────────────────────────────────────────

class _NotificationBellButton extends StatelessWidget {
  final VoidCallback onTap;
  const _NotificationBellButton({required this.onTap});

  @override
  Widget build(BuildContext context) {
    final unread = context.watch<NotificationService>().unreadCount;
    return Stack(
      clipBehavior: Clip.none,
      children: [
        IconButton(
          icon: Icon(Icons.notifications_outlined, color: AppColors.onSurface),
          onPressed: onTap,
          tooltip: 'Notifications',
        ),
        if (unread > 0)
          Positioned(
            right: 6,
            top: 6,
            child: IgnorePointer(
              child: Container(
                constraints: const BoxConstraints(minWidth: 18, minHeight: 18),
                padding: const EdgeInsets.symmetric(horizontal: 5),
                decoration: BoxDecoration(
                  color: AppColors.error,
                  borderRadius: BorderRadius.circular(999),
                  border: Border.all(color: AppColors.surfaceTint, width: 2),
                ),
                alignment: Alignment.center,
                child: Text(
                  unread > 99 ? '99+' : '$unread',
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                    height: 1.1,
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
}

/// Modal sheet listing the user's notifications. Tapping a row marks it
/// read and routes back through `onOpenReport` so the parent can navigate
/// to the report detail with proper context.
class _NotificationsSheet extends StatelessWidget {
  final void Function(int reportId) onOpenReport;
  const _NotificationsSheet({required this.onOpenReport});

  @override
  Widget build(BuildContext context) {
    return DraggableScrollableSheet(
      initialChildSize: 0.7,
      minChildSize: 0.4,
      maxChildSize: 0.95,
      expand: false,
      builder: (_, scrollController) {
        return Consumer<NotificationService>(
          builder: (context, service, _) {
            return Container(
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLowest,
                borderRadius: const BorderRadius.vertical(
                  top: Radius.circular(20),
                ),
              ),
              child: Column(
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
                  Padding(
                    padding:
                        const EdgeInsets.fromLTRB(20, 14, 12, 10),
                    child: Row(
                      children: [
                        Text(
                          'Notifications',
                          style: TextStyle(
                            fontFamily: 'Plus Jakarta Sans',
                            fontWeight: FontWeight.w800,
                            fontSize: 20,
                            color: AppColors.onSurface,
                          ),
                        ),
                        if (service.unreadCount > 0) ...[
                          const SizedBox(width: 8),
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: AppColors.primary,
                              borderRadius: BorderRadius.circular(999),
                            ),
                            child: Text(
                              '${service.unreadCount} new',
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.w700,
                                color: AppColors.onPrimarySolid,
                              ),
                            ),
                          ),
                        ],
                        const Spacer(),
                        IconButton(
                          tooltip: 'Refresh',
                          icon: Icon(Icons.refresh,
                              color: AppColors.onSurfaceVariant),
                          onPressed:
                              service.isLoading ? null : service.refresh,
                        ),
                      ],
                    ),
                  ),
                  Divider(
                    height: 1,
                    thickness: 1,
                    color: AppColors.surfaceContainerHigh,
                  ),
                  Expanded(
                    child: _buildBody(context, service, scrollController),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  Widget _buildBody(BuildContext context, NotificationService service,
      ScrollController controller) {
    if (service.isLoading && service.items.isEmpty) {
      return Center(
        child: CircularProgressIndicator(color: AppColors.primary),
      );
    }
    if (service.error != null && service.items.isEmpty) {
      return _emptyState(
        icon: Icons.wifi_off,
        title: 'Could not load notifications',
        subtitle: 'Pull to retry, or check your connection.',
      );
    }
    if (service.items.isEmpty) {
      return _emptyState(
        icon: Icons.notifications_none,
        title: 'You\'re all caught up',
        subtitle: 'New activity on reports you follow shows up here.',
      );
    }
    return RefreshIndicator(
      color: AppColors.primary,
      onRefresh: service.refresh,
      child: ListView.separated(
        controller: controller,
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.symmetric(vertical: 4),
        itemCount: service.items.length,
        separatorBuilder: (_, __) => Divider(
          height: 1,
          indent: 64,
          color: AppColors.surfaceContainerHigh.withValues(alpha: 0.6),
        ),
        itemBuilder: (_, i) {
          final n = service.items[i];
          return _notificationTile(context, service, n);
        },
      ),
    );
  }

  Widget _notificationTile(
    BuildContext context,
    NotificationService service,
    NotificationModel n,
  ) {
    final accent = !n.read ? AppColors.primary : AppColors.onSurfaceVariant;
    return InkWell(
      onTap: () {
        Navigator.of(context).pop();
        // Fire and forget — read marker shouldn't block navigation.
        if (!n.read) service.markRead(n.id);
        final id = n.relatedEntityId;
        if (id != null) onOpenReport(id);
      },
      child: Container(
        color: n.read ? null : AppColors.primary.withValues(alpha: 0.04),
        padding: const EdgeInsets.fromLTRB(20, 14, 20, 14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: accent.withValues(alpha: 0.12),
                shape: BoxShape.circle,
              ),
              alignment: Alignment.center,
              child: Icon(n.type.icon, size: 18, color: accent),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text(
                        n.type.label,
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 0.4,
                          color: accent,
                        ),
                      ),
                      const Spacer(),
                      Text(
                        n.timeAgo,
                        style: TextStyle(
                          fontSize: 11,
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    n.message,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight:
                          n.read ? FontWeight.w500 : FontWeight.w700,
                      color: AppColors.onSurface,
                      height: 1.35,
                    ),
                  ),
                ],
              ),
            ),
            if (!n.read)
              Padding(
                padding: const EdgeInsets.only(left: 8, top: 4),
                child: Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: AppColors.primary,
                    shape: BoxShape.circle,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _emptyState({
    required IconData icon,
    required String title,
    required String subtitle,
  }) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 48, color: AppColors.onSurfaceVariant),
            const SizedBox(height: 12),
            Text(
              title,
              style: TextStyle(
                fontFamily: 'Plus Jakarta Sans',
                fontWeight: FontWeight.w700,
                fontSize: 16,
                color: AppColors.onSurface,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              subtitle,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 12,
                color: AppColors.onSurfaceVariant,
                height: 1.4,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
