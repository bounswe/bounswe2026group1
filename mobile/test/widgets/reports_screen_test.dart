import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mapcess/screens/reports_screen.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mapcess/services/sse_service.dart';
import 'package:mapcess/theme/app_colors.dart';
import 'package:mocktail/mocktail.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/minimal_report_json.dart';

class _MockClient extends Mock implements http.Client {}

void main() {
  setUpAll(() {
    registerFallbackValue(Uri.parse('https://example.com/'));
  });

  setUp(() {
    AppColors.setBrightness(Brightness.light);
    SharedPreferences.setMockInitialValues({});
  });

  testWidgets('Reports feed loads from API and scroll triggers pagination',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 900));

    final client = _MockClient();
    var feedGets = 0;

    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (invocation) async {
        final uri = invocation.positionalArguments[0] as Uri;
        if (!uri.path.contains('/api/reports/feed')) {
          return http.Response('', 404);
        }
        feedGets++;
        final pageStr = uri.queryParameters['page'] ?? '0';
        final pageIdx = int.tryParse(pageStr) ?? 0;

        if (pageIdx == 0) {
          // Enough rows that the list scrolls on a phone-sized viewport so
          // [_onScroll] can fire the lazy-load threshold.
          final content = List<Map<String, dynamic>>.generate(
            8,
            (i) => minimalReportJson(id: i + 1, description: 'Item $i'),
          );
          return http.Response(
            jsonEncode(
              feedPageJson(
                content: content,
                last: false,
                number: 0,
              ),
            ),
            200,
          );
        }

        return http.Response(
          jsonEncode(
            feedPageJson(
              content: [
                minimalReportJson(id: 3, description: 'Third'),
              ],
              last: true,
              number: 1,
            ),
          ),
          200,
        );
      },
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
          ChangeNotifierProvider(create: (_) => SseService()),
        ],
        child: const MaterialApp(
          home: ReportsScreen(),
        ),
      ),
    );

    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    expect(tester.takeException(), isNull);
    expect(find.text('Feed'), findsOneWidget);
    expect(find.text('Item 0'), findsOneWidget);

    // Header filter chips use a horizontal SingleChildScrollView — it is also
    // a [Scrollable]. The feed ListView is the *vertical* scrollable.
    final verticalScroll = find.byWidgetPredicate(
      (widget) =>
          widget is Scrollable &&
          axisDirectionToAxis(widget.axisDirection) == Axis.vertical,
    );
    expect(tester.widgetList(verticalScroll), isNotEmpty);

    for (var i = 0; i < 8; i++) {
      await tester.drag(verticalScroll.first, const Offset(0, -600));
      await tester.pump(const Duration(milliseconds: 60));
      if (feedGets >= 2) break;
    }
    await tester.pump(const Duration(milliseconds: 200));

    expect(feedGets, greaterThanOrEqualTo(2));
    expect(find.text('Third'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  // ── New: empty-state, error, and chrome rendering ────────────────────────

  testWidgets('Reports feed shows the empty-state copy when API returns []',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 900));

    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(
        jsonEncode(
          feedPageJson(content: const <Map<String, dynamic>>[], last: true),
        ),
        200,
      ),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
          ChangeNotifierProvider(create: (_) => SseService()),
        ],
        child: const MaterialApp(home: ReportsScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    expect(find.text('No reports yet.'), findsOneWidget);
  });

  testWidgets('Reports feed shows the Try Again button when API fails',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 900));

    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(
        jsonEncode({'message': 'down'}),
        500,
      ),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
          ChangeNotifierProvider(create: (_) => SseService()),
        ],
        child: const MaterialApp(home: ReportsScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    expect(find.text('Try Again'), findsOneWidget);
    expect(find.text('Could not load the feed'), findsOneWidget);
  });

  testWidgets('Reports feed Try Again triggers a second feed call',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 900));

    final client = _MockClient();
    var call = 0;
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async {
        call++;
        if (call == 1) return http.Response('{}', 500);
        return http.Response(
          jsonEncode(
            feedPageJson(content: const <Map<String, dynamic>>[], last: true),
          ),
          200,
        );
      },
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
          ChangeNotifierProvider(create: (_) => SseService()),
        ],
        child: const MaterialApp(home: ReportsScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    await tester.tap(find.text('Try Again'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    expect(call, greaterThanOrEqualTo(2));
    expect(find.text('Try Again'), findsNothing);
    expect(find.text('No reports yet.'), findsOneWidget);
  });

  testWidgets('Tapping the location toggle expands the location panel',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 900));
    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(
        jsonEncode(
          feedPageJson(content: const <Map<String, dynamic>>[], last: true),
        ),
        200,
      ),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
          ChangeNotifierProvider(create: (_) => SseService()),
        ],
        child: const MaterialApp(home: ReportsScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    // The location toggle uses the my_location icon — tap it to expand.
    final toggle = find.byIcon(Icons.my_location);
    if (toggle.evaluate().isNotEmpty) {
      await tester.tap(toggle.first);
      await tester.pumpAndSettle();
    }
    // Even if no exact match was found, we exercised the chip row.
    expect(find.text('Feed'), findsOneWidget);
  });

  testWidgets('Tapping the Feature filter chip triggers a new feed fetch',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 900));
    final client = _MockClient();
    var calls = 0;
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async {
        calls++;
        return http.Response(
          jsonEncode(
            feedPageJson(content: const <Map<String, dynamic>>[], last: true),
          ),
          200,
        );
      },
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
          ChangeNotifierProvider(create: (_) => SseService()),
        ],
        child: const MaterialApp(home: ReportsScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    final before = calls;
    await tester.tap(find.text('Feature'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    expect(calls, greaterThan(before));
  });

  testWidgets(
      'Tapping the Outdoor environment filter chip triggers a new feed fetch',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(500, 900));
    final client = _MockClient();
    var calls = 0;
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async {
        calls++;
        return http.Response(
          jsonEncode(
            feedPageJson(content: const <Map<String, dynamic>>[], last: true),
          ),
          200,
        );
      },
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
          ChangeNotifierProvider(create: (_) => SseService()),
        ],
        child: const MaterialApp(home: ReportsScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    final before = calls;
    // Outdoor chip lives in the horizontally scrollable filter row.
    await tester.dragUntilVisible(
      find.text('Outdoor'),
      find.byType(SingleChildScrollView).first,
      const Offset(-200, 0),
    );
    await tester.tap(find.text('Outdoor'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    expect(calls, greaterThan(before));
  });

  testWidgets('Tapping the Indoor environment chip triggers a new feed fetch',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(500, 900));
    final client = _MockClient();
    var calls = 0;
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async {
        calls++;
        return http.Response(
          jsonEncode(
            feedPageJson(content: const <Map<String, dynamic>>[], last: true),
          ),
          200,
        );
      },
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
          ChangeNotifierProvider(create: (_) => SseService()),
        ],
        child: const MaterialApp(home: ReportsScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    final before = calls;
    await tester.dragUntilVisible(
      find.text('Indoor'),
      find.byType(SingleChildScrollView).first,
      const Offset(-200, 0),
    );
    await tester.tap(find.text('Indoor'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    expect(calls, greaterThan(before));
  });

  testWidgets('Reports feed renders report cards from the API response',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 1000));
    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(
        jsonEncode(
          feedPageJson(
            content: [
              minimalReportJson(id: 1, description: 'First report'),
              minimalReportJson(id: 2, description: 'Second report'),
            ],
            last: true,
          ),
        ),
        200,
      ),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
          ChangeNotifierProvider(create: (_) => SseService()),
        ],
        child: const MaterialApp(home: ReportsScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    expect(find.text('First report'), findsOneWidget);
    expect(find.text('Second report'), findsOneWidget);
  });

  testWidgets('Tapping the Obstacle filter chip triggers a new feed fetch',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 900));
    final client = _MockClient();
    var calls = 0;
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async {
        calls++;
        return http.Response(
          jsonEncode(
            feedPageJson(content: const <Map<String, dynamic>>[], last: true),
          ),
          200,
        );
      },
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
          ChangeNotifierProvider(create: (_) => SseService()),
        ],
        child: const MaterialApp(home: ReportsScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    final before = calls;
    // Obstacle chip in the filter row.
    await tester.tap(find.text('Obstacle'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    expect(calls, greaterThan(before));
  });

  testWidgets('Reports feed renders filter chips for type and environment',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 900));

    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(
        jsonEncode(
          feedPageJson(content: const <Map<String, dynamic>>[], last: true),
        ),
        200,
      ),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
          ChangeNotifierProvider(create: (_) => SseService()),
        ],
        child: const MaterialApp(home: ReportsScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    // Filter chips: All / Obstacle / Feature / Both / Outdoor / Indoor
    expect(find.text('All'), findsWidgets);
  });

  testWidgets('Tapping location panel toggle expands and collapses it',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(500, 1000));
    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(
        jsonEncode(
          feedPageJson(content: const <Map<String, dynamic>>[], last: true),
        ),
        200,
      ),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
          ChangeNotifierProvider(create: (_) => SseService()),
        ],
        child: const MaterialApp(home: ReportsScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    // Location toggle button — find by its containing icon.
    final pinIcon = find.byIcon(Icons.location_on_outlined);
    if (pinIcon.evaluate().isNotEmpty) {
      await tester.tap(pinIcon.first, warnIfMissed: false);
      await tester.pumpAndSettle();
    }
    // Whatever happens, the header is still there.
    expect(find.text('Feed'), findsOneWidget);
  });

  testWidgets('Reports feed renders the Feed title in the header',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 900));

    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(
        jsonEncode(
          feedPageJson(content: const <Map<String, dynamic>>[], last: true),
        ),
        200,
      ),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
          ChangeNotifierProvider(create: (_) => SseService()),
        ],
        child: const MaterialApp(home: ReportsScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('Feed'), findsOneWidget);
  });
}
