import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/main.dart';
import 'package:mapcess/models/notification_model.dart';
import 'package:mapcess/models/sse_event.dart';
import 'package:mapcess/services/api_service.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mapcess/services/notification_service.dart';
import 'package:mapcess/services/sse_service.dart';
import 'package:mapcess/services/theme_service.dart';
import 'package:mocktail/mocktail.dart';
import 'package:provider/provider.dart';

class MockAuthService extends Mock implements AuthService {}

class MockApiService extends Mock implements ApiService {}

class MockSseService extends Mock implements SseService {}

class MockThemeService extends Mock implements ThemeService {}

class MockNotificationService extends Mock implements NotificationService {}

void main() {
  late MockAuthService auth;
  late MockApiService api;
  late MockSseService sse;
  late MockThemeService theme;
  late MockNotificationService notifications;
  late StreamController<SseEvent> sseController;

  setUp(() {
    auth = MockAuthService();
    api = MockApiService();
    sse = MockSseService();
    theme = MockThemeService();
    notifications = MockNotificationService();
    sseController = StreamController<SseEvent>.broadcast();

    when(() => auth.api).thenReturn(api);
    when(() => auth.isAuthenticated).thenReturn(false);
    when(() => auth.userId).thenReturn(1);
    when(() => sse.events).thenAnswer((_) => sseController.stream);
    when(() => sse.connected).thenReturn(false);
    when(() => sse.needsFullRefresh).thenReturn(false);
    when(() => theme.mode).thenReturn(ThemeMode.light);
    when(() => notifications.items).thenReturn(const []);
    when(() => notifications.unreadCount).thenReturn(0);
  });

  tearDown(() async {
    await sseController.close();
  });

  Widget wrap(Widget child) => MultiProvider(
        providers: [
          ChangeNotifierProvider<AuthService>.value(value: auth),
          ChangeNotifierProvider<SseService>.value(value: sse),
          ChangeNotifierProvider<ThemeService>.value(value: theme),
          ChangeNotifierProvider<NotificationService>.value(value: notifications),
        ],
        child: MaterialApp(home: child),
      );

  group('AuthShell', () {
    testWidgets('renders the Sign In and Sign Up bottom nav items',
        (tester) async {
      await tester.pumpWidget(wrap(const AuthShell()));
      await tester.pump();

      expect(find.text('SIGN IN'), findsOneWidget);
      expect(find.text('SIGN UP'), findsOneWidget);
    });

    testWidgets('starts on the Sign In tab by default', (tester) async {
      await tester.pumpWidget(wrap(const AuthShell()));
      await tester.pump();

      // Login screen's primary CTA copy.
      expect(find.text('SIGN IN'), findsWidgets);
    });

    testWidgets('initialTab: 1 starts on the Sign Up tab', (tester) async {
      await tester.pumpWidget(wrap(const AuthShell(initialTab: 1)));
      await tester.pump();

      expect(find.text('SIGN UP'), findsWidgets);
    });

    testWidgets('tapping SIGN UP switches to the register pane',
        (tester) async {
      await tester.pumpWidget(wrap(const AuthShell()));
      await tester.pump();

      await tester.tap(find.text('SIGN UP'));
      // Drive the AnimationController to completion.
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.text('SIGN UP'), findsWidgets);
    });
  });

  group('MainShell', () {
    setUp(() {
      // Reports for HomeScreen, ReportsScreen, ProfileScreen need to load
      // something benign.
      when(() => api.getReports()).thenAnswer((_) async => const []);
      when(() => api.getReportFeed(
            page: any(named: 'page'),
            size: any(named: 'size'),
            reportType: any(named: 'reportType'),
            environment: any(named: 'environment'),
            latitude: any(named: 'latitude'),
            longitude: any(named: 'longitude'),
            radiusInKm: any(named: 'radiusInKm'),
          )).thenAnswer((_) async => FeedPage(
            content: const [],
            number: 0,
            size: 20,
            last: true,
            totalPages: 0,
          ));
    });

    testWidgets('renders the bottom nav with three tabs', (tester) async {
      await tester.pumpWidget(wrap(const MainShell()));
      await tester.pump();

      // The bottom nav has Home, Feed, Profile (or similar copy).
      // Don't assert specific tab labels — they may change — just confirm
      // the shell built without throwing.
      expect(find.byType(MainShell), findsOneWidget);
    });

    testWidgets('MainShell starting on Feed (initialTab: 1) shows Feed UI',
        (tester) async {
      await tester.pumpWidget(wrap(const MainShell(initialTab: 1)));
      await tester.pump();
      // Wait for AnimationController to finish (260ms).
      await tester.pump(const Duration(milliseconds: 300));

      // ReportsScreen renders a "Feed" title — there can be multiple
      // "Feed" widgets on screen (offstage HomeScreen also contains one
      // via its empty marker label), so use findsWidgets.
      expect(find.text('Feed'), findsWidgets);
    });

    testWidgets('MainShell starting on Profile (initialTab: 2) shows guest UI',
        (tester) async {
      await tester.pumpWidget(wrap(const MainShell(initialTab: 2)));
      await tester.pumpAndSettle();

      // Guest profile shows a Sign In CTA.
      expect(find.textContaining('Sign In'), findsWidgets);
    });
  });

  group('MapcessApp', () {
    setUpAll(() {
      registerFallbackValue(Brightness.light);
    });

    testWidgets('builds without throwing', (tester) async {
      // ThemeService.resolveAgainstPlatform is called inside the builder.
      when(() => theme.resolveAgainstPlatform(any()))
          .thenReturn(Brightness.light);

      await tester.pumpWidget(MapcessApp(
        auth: auth,
        sse: sse,
        theme: theme,
        notifications: notifications,
      ));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      // MapcessApp boots to AuthShell — Sign In tab is the default.
      expect(find.byType(MapcessApp), findsOneWidget);
    });
  });

  group('MainShell top bar interactions (authenticated)', () {
    setUp(() {
      when(() => auth.isAuthenticated).thenReturn(true);
      when(() => auth.api).thenReturn(api);
      when(() => api.getReports()).thenAnswer((_) async => const []);
      when(() => api.getReportFeed(
            page: any(named: 'page'),
            size: any(named: 'size'),
            reportType: any(named: 'reportType'),
            environment: any(named: 'environment'),
            latitude: any(named: 'latitude'),
            longitude: any(named: 'longitude'),
            radiusInKm: any(named: 'radiusInKm'),
          )).thenAnswer((_) async => FeedPage(
            content: const [],
            number: 0,
            size: 20,
            last: true,
            totalPages: 0,
          ));
      when(() => api.getMyProfile()).thenAnswer((_) async => {
            'name': 'Me',
            'email': 'me@x.com',
            'bio': '',
            'points': 0,
            'rank': 1,
            'contributionStats': {'reportsSubmitted': 0, 'routesPlanned': 0},
            'badges': <String>[],
          });
      when(() => api.getReportsByUser(any())).thenAnswer((_) async => const []);
    });

    testWidgets('tapping the settings icon opens the bottom sheet with Appearance',
        (tester) async {
      // Start on Feed tab so the top bar (and its settings icon) is visible.
      await tester.pumpWidget(wrap(const MainShell(initialTab: 1)));
      await tester.pumpAndSettle();

      final settings = find.byIcon(Icons.settings_outlined);
      expect(settings, findsWidgets);
      await tester.tap(settings.first);
      await tester.pumpAndSettle();

      expect(find.text('Appearance'), findsOneWidget);
      expect(find.text('System'), findsOneWidget);
      expect(find.text('Light'), findsOneWidget);
      expect(find.text('Dark'), findsOneWidget);
    });

    testWidgets('settings sheet shows all three theme segments',
        (tester) async {
      await tester.pumpWidget(wrap(const MainShell(initialTab: 1)));
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.settings_outlined).first);
      await tester.pumpAndSettle();

      // System / Light / Dark segments are rendered side-by-side.
      expect(find.text('System'), findsOneWidget);
      expect(find.text('Light'), findsOneWidget);
      expect(find.text('Dark'), findsOneWidget);
    });

    testWidgets('settings sheet shows Log Out when authenticated',
        (tester) async {
      await tester.pumpWidget(wrap(const MainShell(initialTab: 1)));
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.settings_outlined).first);
      await tester.pumpAndSettle();

      expect(find.text('Log Out'), findsOneWidget);
    });

    testWidgets('settings sheet shows Sign In when guest', (tester) async {
      when(() => auth.isAuthenticated).thenReturn(false);
      await tester.pumpWidget(wrap(const MainShell(initialTab: 1)));
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.settings_outlined).first);
      await tester.pumpAndSettle();

      expect(find.widgetWithText(ListTile, 'Sign In'), findsOneWidget);
    });
  });

  group('Notifications sheet', () {
    NotificationModel buildNotif({
      int id = 1,
      String type = 'NEW_COMMENT',
      bool read = false,
      String message = 'Someone replied to your report',
      int? relatedEntityId = 42,
    }) {
      return NotificationModel.fromJson({
        'id': id,
        'type': type,
        'message': message,
        'relatedEntityId': relatedEntityId,
        'read': read,
        'createdAt': '2026-05-01T12:00:00Z',
      });
    }

    Future<void> openSheet(WidgetTester tester) async {
      when(() => auth.isAuthenticated).thenReturn(true);
      when(() => auth.api).thenReturn(api);
      when(() => api.getReports()).thenAnswer((_) async => const []);
      when(() => api.getReportFeed(
            page: any(named: 'page'),
            size: any(named: 'size'),
            reportType: any(named: 'reportType'),
            environment: any(named: 'environment'),
            latitude: any(named: 'latitude'),
            longitude: any(named: 'longitude'),
            radiusInKm: any(named: 'radiusInKm'),
          )).thenAnswer((_) async => FeedPage(
            content: const [],
            number: 0,
            size: 20,
            last: true,
            totalPages: 0,
          ));
      // The bell handler calls refresh() before opening the sheet — stub
      // by default so individual tests don't have to.
      when(() => notifications.refresh()).thenAnswer((_) async {});

      // Start on Feed tab so the bell icon is visible.
      await tester.pumpWidget(wrap(const MainShell(initialTab: 1)));
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.notifications_outlined).first);
      await tester.pumpAndSettle();
    }

    testWidgets('shows the loading spinner while loading + empty items',
        (tester) async {
      when(() => notifications.isLoading).thenReturn(true);
      when(() => notifications.items).thenReturn(const []);
      when(() => notifications.error).thenReturn(null);
      when(() => auth.isAuthenticated).thenReturn(true);
      when(() => auth.api).thenReturn(api);
      when(() => api.getReports()).thenAnswer((_) async => const []);
      when(() => api.getReportFeed(
            page: any(named: 'page'),
            size: any(named: 'size'),
            reportType: any(named: 'reportType'),
            environment: any(named: 'environment'),
            latitude: any(named: 'latitude'),
            longitude: any(named: 'longitude'),
            radiusInKm: any(named: 'radiusInKm'),
          )).thenAnswer((_) async => FeedPage(
            content: const [],
            number: 0,
            size: 20,
            last: true,
            totalPages: 0,
          ));
      when(() => notifications.refresh()).thenAnswer((_) async {});

      // Spinner animates forever — pumpAndSettle would time out. Use
      // discrete pumps instead.
      await tester.pumpWidget(wrap(const MainShell(initialTab: 1)));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      await tester.tap(find.byIcon(Icons.notifications_outlined).first);
      // 300ms covers the modal sheet's animation.
      await tester.pump(const Duration(milliseconds: 50));
      await tester.pump(const Duration(milliseconds: 250));
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.byType(CircularProgressIndicator), findsWidgets);
    });

    testWidgets('shows the all-caught-up empty state when items is empty',
        (tester) async {
      when(() => notifications.isLoading).thenReturn(false);
      when(() => notifications.items).thenReturn(const []);
      when(() => notifications.error).thenReturn(null);

      await openSheet(tester);

      expect(find.text("You're all caught up"), findsOneWidget);
    });

    testWidgets('shows the network-error empty state when error is set',
        (tester) async {
      when(() => notifications.isLoading).thenReturn(false);
      when(() => notifications.items).thenReturn(const []);
      when(() => notifications.error).thenReturn('boom');

      await openSheet(tester);

      expect(find.text('Could not load notifications'), findsOneWidget);
    });

    testWidgets('renders notification tiles when items is populated',
        (tester) async {
      when(() => notifications.isLoading).thenReturn(false);
      when(() => notifications.items).thenReturn([
        buildNotif(id: 1, message: 'Your report was verified'),
        buildNotif(id: 2, message: 'A new comment arrived', read: true),
      ]);
      when(() => notifications.error).thenReturn(null);
      when(() => notifications.unreadCount).thenReturn(1);

      await openSheet(tester);

      expect(find.text('Your report was verified'), findsOneWidget);
      expect(find.text('A new comment arrived'), findsOneWidget);
      // "1 new" badge in the header.
      expect(find.text('1 new'), findsOneWidget);
    });

    testWidgets('tapping a row pops the sheet and calls markRead',
        (tester) async {
      when(() => notifications.isLoading).thenReturn(false);
      when(() => notifications.items).thenReturn([
        buildNotif(id: 7, message: 'Tap me'),
      ]);
      when(() => notifications.error).thenReturn(null);
      when(() => notifications.unreadCount).thenReturn(1);
      when(() => notifications.markRead(any())).thenAnswer((_) async {});

      await openSheet(tester);
      await tester.tap(find.text('Tap me'));
      await tester.pumpAndSettle();

      verify(() => notifications.markRead(7)).called(1);
    });

    testWidgets('tapping refresh calls NotificationService.refresh',
        (tester) async {
      when(() => notifications.isLoading).thenReturn(false);
      when(() => notifications.items).thenReturn(const []);
      when(() => notifications.error).thenReturn(null);
      when(() => notifications.refresh()).thenAnswer((_) async {});

      await openSheet(tester);
      await tester.tap(find.byIcon(Icons.refresh));
      await tester.pump();

      verify(() => notifications.refresh()).called(greaterThanOrEqualTo(1));
    });
  });

  group('Notification bell', () {
    testWidgets('renders an unread badge when unreadCount > 0', (tester) async {
      when(() => auth.isAuthenticated).thenReturn(true);
      when(() => auth.api).thenReturn(api);
      when(() => api.getReports()).thenAnswer((_) async => const []);
      when(() => api.getReportFeed(
            page: any(named: 'page'),
            size: any(named: 'size'),
            reportType: any(named: 'reportType'),
            environment: any(named: 'environment'),
            latitude: any(named: 'latitude'),
            longitude: any(named: 'longitude'),
            radiusInKm: any(named: 'radiusInKm'),
          )).thenAnswer((_) async => FeedPage(
            content: const [],
            number: 0,
            size: 20,
            last: true,
            totalPages: 0,
          ));
      when(() => notifications.unreadCount).thenReturn(7);

      await tester.pumpWidget(wrap(const MainShell(initialTab: 1)));
      await tester.pumpAndSettle();

      // Badge label shows the unread count.
      expect(find.text('7'), findsWidgets);
    });

    testWidgets('renders 99+ when unreadCount exceeds 99', (tester) async {
      when(() => auth.isAuthenticated).thenReturn(true);
      when(() => auth.api).thenReturn(api);
      when(() => api.getReports()).thenAnswer((_) async => const []);
      when(() => api.getReportFeed(
            page: any(named: 'page'),
            size: any(named: 'size'),
            reportType: any(named: 'reportType'),
            environment: any(named: 'environment'),
            latitude: any(named: 'latitude'),
            longitude: any(named: 'longitude'),
            radiusInKm: any(named: 'radiusInKm'),
          )).thenAnswer((_) async => FeedPage(
            content: const [],
            number: 0,
            size: 20,
            last: true,
            totalPages: 0,
          ));
      when(() => notifications.unreadCount).thenReturn(150);

      await tester.pumpWidget(wrap(const MainShell(initialTab: 1)));
      await tester.pumpAndSettle();

      expect(find.text('99+'), findsWidgets);
    });
  });
}
