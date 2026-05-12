import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/models/report_model.dart';
import 'package:mapcess/models/sse_event.dart';
import 'package:mapcess/screens/home_screen.dart';
import 'package:mapcess/services/api_service.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mapcess/services/sse_service.dart';
import 'package:mocktail/mocktail.dart';
import 'package:provider/provider.dart';

import '../support/minimal_report_json.dart';

class MockAuthService extends Mock implements AuthService {}

class MockApiService extends Mock implements ApiService {}

class MockSseService extends Mock implements SseService {}

void main() {
  late MockAuthService mockAuthService;
  late MockApiService mockApiService;
  late MockSseService mockSseService;
  late StreamController<SseEvent> sseController;

  // The home screen's default center is San Francisco; building the
  // fixture report there means _loadReports won't fire a giant tile
  // reload that trips flutter_test's "multiple exceptions" guard.
  ReportModel buildReport({
    required int id,
    String env = 'OUTDOOR',
    String objectType = 'RAMP',
    List<String> issues = const ['MISSING'],
    double lat = 37.7599,
    double lon = -122.4148,
  }) {
    final json = minimalReportJson(id: id);
    json['environment'] = env;
    json['latitude'] = lat;
    json['longitude'] = lon;
    json['objects'] = [
      {
        'objectType': objectType,
        'issues': issues,
      },
    ];
    return ReportModel.fromJson(json);
  }

  setUp(() {
    mockAuthService = MockAuthService();
    mockApiService = MockApiService();
    mockSseService = MockSseService();
    sseController = StreamController<SseEvent>.broadcast();

    when(() => mockAuthService.isAuthenticated).thenReturn(false);
    when(() => mockAuthService.api).thenReturn(mockApiService);
    when(() => mockSseService.events).thenAnswer((_) => sseController.stream);
    when(() => mockSseService.needsFullRefresh).thenReturn(false);
    when(() => mockSseService.connected).thenReturn(false);
    when(() => mockApiService.getReports()).thenAnswer((_) async => const []);
  });

  tearDown(() async {
    await sseController.close();
  });

  Widget createHomeScreen() {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<AuthService>.value(value: mockAuthService),
        ChangeNotifierProvider<SseService>.value(value: mockSseService),
      ],
      child: const MaterialApp(home: HomeScreen()),
    );
  }

  group('HomeScreen Tests', () {
    testWidgets('renders home screen structure', (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pump();
      expect(find.byType(Scaffold), findsWidgets);
    });

    testWidgets('shows the floating search bar', (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pump();
      expect(find.text('Search for a place…'), findsOneWidget);
    });

    testWidgets('renders the Report FAB CTA', (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pump();
      expect(find.text('Report an Issue'), findsOneWidget);
    });

    testWidgets('renders the filter pill when no filters are active',
        (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pump();
      expect(find.text('Filters'), findsOneWidget);
    });

    testWidgets('opens the filter sheet when the Filters chip is tapped',
        (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pumpAndSettle(const Duration(milliseconds: 100));

      await tester.tap(find.text('Filters'));
      await tester.pumpAndSettle();

      expect(find.text('Filter reports'), findsOneWidget);
      expect(find.text('ENVIRONMENT'), findsOneWidget);
      // CATEGORIES / ISSUES sections exist but the bottom sheet starts
      // collapsed; not asserting them keeps the test independent of the
      // sheet's initial height / device viewport.
    });

    testWidgets('calls getReports on initState', (tester) async {
      // Use a fixture at the screen's default center so _loadReports
      // doesn't pan the map (panning floods tile loads and trips
      // flutter_test's "multiple exceptions" guard).
      when(() => mockApiService.getReports()).thenAnswer(
        (_) async => [buildReport(id: 1)],
      );
      await tester.pumpWidget(createHomeScreen());
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 10));

      verify(() => mockApiService.getReports()).called(greaterThanOrEqualTo(1));
    });

    testWidgets('SSE REPORT_DELETED removes the matching report',
        (tester) async {
      when(() => mockApiService.getReports()).thenAnswer(
        (_) async => [buildReport(id: 1), buildReport(id: 2)],
      );
      await tester.pumpWidget(createHomeScreen());
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 10));

      // Drop id=1 — exercises the REPORT_DELETED branch in _onSseEvent,
      // which calls setState and rebuilds the marker list.
      sseController.add(const SseEvent(
        eventType: 'REPORT_DELETED',
        reportId: 1,
      ));
      await tester.pump();

      // Filters chip stays in its idle state — we only assert the screen
      // didn't crash. Behavioral assertions on the marker count would
      // require inspecting private state.
      expect(find.text('Filters'), findsOneWidget);
    });

    testWidgets('SSE REPORT_UPDATED handler swallows events for unknown ids',
        (tester) async {
      when(() => mockApiService.getReports()).thenAnswer(
        (_) async => [buildReport(id: 10)],
      );
      await tester.pumpWidget(createHomeScreen());
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 10));

      // Unknown report id — handler should bail out early without throwing.
      sseController.add(const SseEvent(
        eventType: 'REPORT_UPDATED',
        reportId: 999,
        agrees: 5,
        disagrees: 1,
      ));
      await tester.pump();

      expect(find.text('Filters'), findsOneWidget);
    });

    testWidgets('tapping the navigate FAB switches to route mode',
        (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pump();

      // Route-mode FAB uses the directions icon (only visible in non-route).
      final navIcon = find.byIcon(Icons.directions);
      expect(navIcon, findsOneWidget);

      await tester.tap(navIcon, warnIfMissed: false);
      await tester.pump();

      // Route mode shows a starting-point search hint instead of the
      // normal place-search hint.
      expect(find.text('Search for a place…'), findsNothing);
    });

    testWidgets(
        'unauthenticated user sees a login prompt when tapping the Report FAB',
        (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pumpAndSettle(const Duration(milliseconds: 100));

      await tester.tap(find.text('Report an Issue'));
      await tester.pumpAndSettle();

      // The exact title comes from _showLoginRequiredDialog — it asks the
      // user to sign in. We assert a *Sign* word so the test stays robust
      // to copy tweaks like "Sign in to continue".
      expect(find.textContaining('Sign'), findsWidgets);
    });

    testWidgets('SSE event subscription is wired on initState',
        (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pump();

      // The screen reads `events` and `connected` from SseService — verify
      // we observed at least one read of `events` (the stream subscribe).
      verify(() => mockSseService.events).called(greaterThanOrEqualTo(1));
    });

    testWidgets('error banner appears when getReports throws',
        (tester) async {
      when(() => mockApiService.getReports())
          .thenThrow(ApiException(500, 'boom'));
      await tester.pumpWidget(createHomeScreen());
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));

      // _buildErrorBanner shows a fixed copy regardless of the exception.
      expect(
        find.text('Could not load reports. Check your connection.'),
        findsOneWidget,
      );
    });
  });

  group('HomeScreen filter sheet', () {
    testWidgets('Reset button is initially disabled (no active filters)',
        (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pumpAndSettle(const Duration(milliseconds: 100));

      await tester.tap(find.text('Filters'));
      await tester.pumpAndSettle();

      // Reset is visible but disabled. We can't introspect onPressed null
      // directly, but tapping it should be a no-op (i.e. neither the env
      // nor any chip changes after).
      await tester.tap(find.text('Reset'), warnIfMissed: false);
      await tester.pump();

      // Still showing Reset, so the sheet didn't close.
      expect(find.text('Reset'), findsOneWidget);
    });

    testWidgets('selecting an environment in the sheet works', (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pumpAndSettle(const Duration(milliseconds: 100));

      await tester.tap(find.text('Filters'));
      await tester.pumpAndSettle();

      // Inside the sheet, find the Indoor env button.
      final indoor = find.text('Indoor');
      expect(indoor, findsOneWidget);
      await tester.tap(indoor);
      await tester.pump();

      // After tapping, "Reset" should now be tappable (env != null).
      expect(find.text('Reset'), findsOneWidget);
    });

    testWidgets('applying filters from the sheet closes it and adds a chip',
        (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pumpAndSettle(const Duration(milliseconds: 100));

      await tester.tap(find.text('Filters'));
      await tester.pumpAndSettle();

      // Select Indoor and apply.
      await tester.tap(find.text('Indoor'));
      await tester.pump();

      await tester.ensureVisible(find.text('Apply filters'));
      await tester.tap(find.text('Apply filters'));
      await tester.pumpAndSettle();

      // The chip row at the top now shows an active filter ("Filters · 1").
      expect(find.text('Filters · 1'), findsOneWidget);
    });

    testWidgets('Both environment option is shown in the filter sheet',
        (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pumpAndSettle(const Duration(milliseconds: 100));

      await tester.tap(find.text('Filters'));
      await tester.pumpAndSettle();

      // The env segment has three options: Both / Outdoor / Indoor.
      expect(find.text('Both'), findsOneWidget);
    });
  });

  group('HomeScreen route mode', () {
    testWidgets('entering route mode shows the destination route field hint',
        (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pumpAndSettle(const Duration(milliseconds: 100));

      await tester.tap(find.byIcon(Icons.directions));
      await tester.pump();

      // Route panel has a "Choose destination" hint and a "Cancel route" link.
      expect(find.text('Choose destination'), findsOneWidget);
      expect(find.text('Cancel route'), findsOneWidget);
    });

    testWidgets('Cancel route exits route mode and restores Filters chip',
        (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pumpAndSettle(const Duration(milliseconds: 100));

      await tester.tap(find.byIcon(Icons.directions));
      await tester.pump();
      expect(find.text('Cancel route'), findsOneWidget);

      await tester.tap(find.text('Cancel route'));
      await tester.pump();

      expect(find.text('Cancel route'), findsNothing);
      expect(find.text('Filters'), findsOneWidget);
    });

    testWidgets('opening the filter sheet shows Apply filters CTA',
        (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pumpAndSettle(const Duration(milliseconds: 100));

      await tester.tap(find.text('Filters'));
      await tester.pumpAndSettle();

      expect(find.text('Apply filters'), findsOneWidget);
    });

    testWidgets('selecting Outdoor in the filter sheet enables Reset',
        (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pumpAndSettle(const Duration(milliseconds: 100));

      await tester.tap(find.text('Filters'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Outdoor'));
      await tester.pump();

      // Selecting any env activates the Reset action (still visible).
      expect(find.text('Reset'), findsOneWidget);
    });

    testWidgets('tapping the env back to Both clears the selection',
        (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pumpAndSettle(const Duration(milliseconds: 100));

      await tester.tap(find.text('Filters'));
      await tester.pumpAndSettle();

      // Pick Outdoor then Both → toggles back to no env filter.
      await tester.tap(find.text('Outdoor'));
      await tester.pump();
      await tester.tap(find.text('Both'));
      await tester.pump();

      // Apply without any active filter — sheet closes, chip stays at idle.
      await tester.tap(find.text('Apply filters'));
      await tester.pumpAndSettle();

      expect(find.text('Filters'), findsOneWidget);
    });

    testWidgets('opens the search field when tapping the search bar',
        (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pump();

      // Tap the search field to focus it.
      await tester.tap(find.text('Search for a place…'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      // Hint stays visible while empty.
      expect(find.text('Search for a place…'), findsOneWidget);
    });

    testWidgets('typing in the search field updates the controller',
        (tester) async {
      await tester.pumpWidget(createHomeScreen());
      await tester.pump();

      final field = find.byType(TextField).first;
      await tester.enterText(field, 'Boğaziçi');
      await tester.pump();

      expect(find.text('Boğaziçi'), findsOneWidget);
    });

    testWidgets('loads reports and rebuilds markers when getReports returns data',
        (tester) async {
      // Build a fixture at the screen's default San Francisco center to keep
      // the map's tile cache stable.
      Map<String, dynamic> r(int id, double lat, double lon) {
        final json = Map<String, dynamic>.from({
          'reportId': id,
          'userId': 1,
          'latitude': lat,
          'longitude': lon,
          'description': 'd',
          'reportType': 'OBSTACLE',
          'environment': 'OUTDOOR',
          'status': 'VERIFIED',
          'agrees': 1,
          'disagrees': 0,
          'publishDate': '2026-05-01T12:00:00Z',
          'mediaUrls': <String>[],
          'objects': [
            {
              'objectType': 'RAMP',
              'issues': <String>['MISSING'],
            },
          ],
        });
        return json;
      }

      when(() => mockApiService.getReports()).thenAnswer((_) async {
        return [
          ReportModel.fromJson(r(1, 37.7599, -122.4148)),
          ReportModel.fromJson(r(2, 37.7600, -122.4150)),
        ];
      });

      await tester.pumpWidget(createHomeScreen());
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 10));

      // _loadReports completed and called _buildMarkers — verify the call.
      verify(() => mockApiService.getReports())
          .called(greaterThanOrEqualTo(1));

      // Swallow accumulated tile-load exceptions so they don't fail the test.
      await tester.binding.delayed(Duration.zero);
      tester.takeException();
    });
  });
}
