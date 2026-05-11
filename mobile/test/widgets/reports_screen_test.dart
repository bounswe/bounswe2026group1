import 'dart:convert';

import 'package:flutter/material.dart';
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

    await tester.pumpAndSettle(const Duration(seconds: 2));

    expect(tester.takeException(), isNull);
    expect(find.text('Feed'), findsOneWidget);
    expect(find.text('Item 0'), findsOneWidget);

    final scrollable = find.byType(Scrollable);
    expect(scrollable, findsWidgets);

    await tester.drag(scrollable.first, const Offset(0, -700));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    await tester.pumpAndSettle(const Duration(seconds: 2));

    expect(feedGets, greaterThanOrEqualTo(2));
    expect(find.text('Third'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
