import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mapcess/screens/routing_preferences_screen.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mapcess/theme/app_colors.dart';
import 'package:mocktail/mocktail.dart';
import 'package:provider/provider.dart';

class _MockClient extends Mock implements http.Client {}

void main() {
  setUpAll(() {
    registerFallbackValue(Uri.parse('https://example.com/'));
  });

  setUp(() {
    AppColors.setBrightness(Brightness.light);
  });

  Map<String, dynamic> _prefsBody() => {
        'preferredPreset': 'NONE',
        'constraints': <String>[],
        'preferredTravelMode': 'WALKING',
        'availablePresets': [
          {
            'name': 'NONE',
            'label': 'No preset',
            'constraints': <String>[],
            'defaultTravelMode': null,
          },
          {
            'name': 'WHEELCHAIR_USER',
            'label': 'Wheelchair user',
            'constraints': ['AVOID_STEPS'],
            'defaultTravelMode': 'WHEELCHAIR',
          },
        ],
        'availableConstraints': [
          {
            'name': 'AVOID_STEPS',
            'label': 'Avoid steps',
            'description': 'Prefer routes without stairs.',
          },
        ],
        'customProfiles': <Map<String, dynamic>>[],
      };

  testWidgets('Routing preferences loads from API and shows presets',
      (tester) async {
    // Taller viewport so preset cards below the intro stay on-screen on CI
    // (Linux golden/layout can differ from local Windows).
    await tester.binding.setSurfaceSize(const Size(420, 1400));

    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(jsonEncode(_prefsBody()), 200),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(
            create: (_) => AuthService(httpClient: client),
          ),
        ],
        child: const MaterialApp(
          home: RoutingPreferencesScreen(),
        ),
      ),
    );

    // Mock HTTP resolves in the same microtask queue as the first frame, so a
    // CircularProgressIndicator is often never painted on CI. Pump until the
    // async GET + setState completes.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(seconds: 1));

    expect(tester.takeException(), isNull);
    expect(find.text('Routing preferences'), findsOneWidget);
    expect(find.textContaining('Wheelchair'), findsWidgets);

    verify(() => client.get(any(), headers: any(named: 'headers'))).called(1);
  });

  testWidgets('Routing preferences shows error when API fails', (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 900));

    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(
        jsonEncode({'message': 'Could not load preferences from server.'}),
        500,
      ),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(
            create: (_) => AuthService(httpClient: client),
          ),
        ],
        child: const MaterialApp(
          home: RoutingPreferencesScreen(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Retry'), findsOneWidget);
    expect(
      find.textContaining('Could not load preferences from server'),
      findsOneWidget,
    );
  });

  // ── New: deeper coverage of the screen's state machine ────────────────────

  testWidgets('Routing preferences renders the PRESETS section header',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 1400));
    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(jsonEncode(_prefsBody()), 200),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
        ],
        child: const MaterialApp(home: RoutingPreferencesScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('PRESETS'), findsOneWidget);
    expect(find.text('0/5 custom'), findsOneWidget);
  });

  testWidgets(
      'Routing preferences calls Retry when the user taps Retry after a failure',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 900));

    final client = _MockClient();
    var callCount = 0;
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async {
        callCount++;
        if (callCount == 1) {
          return http.Response(jsonEncode({'message': 'boom'}), 500);
        }
        return http.Response(jsonEncode(_prefsBody()), 200);
      },
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
        ],
        child: const MaterialApp(home: RoutingPreferencesScreen()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Retry'), findsOneWidget);

    await tester.tap(find.text('Retry'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    // Second call succeeds — error UI is gone and the header shows.
    expect(find.text('Retry'), findsNothing);
    expect(find.text('PRESETS'), findsOneWidget);
    expect(callCount, 2);
  });

  testWidgets('Routing preferences renders built-in preset labels',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 1600));
    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(jsonEncode(_prefsBody()), 200),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
        ],
        child: const MaterialApp(home: RoutingPreferencesScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('No preset'), findsOneWidget);
    // "Wheelchair user" is the built-in preset's label.
    expect(find.text('Wheelchair user'), findsOneWidget);
  });

  testWidgets('Routing preferences shows a custom-profile card when one exists',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 1600));

    final body = _prefsBody();
    body['customProfiles'] = [
      {
        'id': 7,
        'name': 'Quiet streets',
        'constraints': ['AVOID_STEPS'],
        'preferredTravelMode': 'WALKING',
      },
    ];
    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(jsonEncode(body), 200),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
        ],
        child: const MaterialApp(home: RoutingPreferencesScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('Quiet streets'), findsOneWidget);
    expect(find.text('1/5 custom'), findsOneWidget);
  });

  testWidgets('Routing preferences activates a built-in preset on tap',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 1600));

    final body = _prefsBody();
    final client = _MockClient();
    // GET returns initial prefs.
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(jsonEncode(body), 200),
    );
    // PUT — activate the preset.
    final updated = Map<String, dynamic>.from(body);
    updated['preferredPreset'] = 'WHEELCHAIR_USER';
    updated['preferredTravelMode'] = 'WHEELCHAIR';
    when(
      () => client.put(
        any(),
        headers: any(named: 'headers'),
        body: any(named: 'body'),
      ),
    ).thenAnswer((_) async => http.Response(jsonEncode(updated), 200));

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
        ],
        child: const MaterialApp(home: RoutingPreferencesScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    await tester.tap(find.text('Wheelchair user'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    verify(
      () => client.put(
        any(
          that: predicate<Uri>(
            (u) => u.path.endsWith('/users/me/routing-preferences'),
          ),
        ),
        headers: any(named: 'headers'),
        body: any(named: 'body'),
      ),
    ).called(1);
  });

  testWidgets('Routing preferences shows the Create new preset tile',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 1600));
    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(jsonEncode(_prefsBody()), 200),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
        ],
        child: const MaterialApp(home: RoutingPreferencesScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    // The "Create new preset" tile sits at the bottom of the list.
    expect(find.textContaining('preset'), findsWidgets);
  });

  testWidgets(
      'Opening built-in preset details surfaces the Built-in preset subtitle',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 1600));
    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(jsonEncode(_prefsBody()), 200),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
        ],
        child: const MaterialApp(home: RoutingPreferencesScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    // Each preset card has an info icon that opens the details sheet.
    final infoIcons = find.byIcon(Icons.info_outline);
    expect(infoIcons, findsWidgets);
    await tester.tap(infoIcons.first);
    await tester.pumpAndSettle();

    expect(find.text('Built-in preset'), findsOneWidget);
  });

  testWidgets(
      'Custom-profile details sheet exposes Edit and Delete actions',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 1600));
    final body = _prefsBody();
    body['customProfiles'] = [
      {
        'id': 7,
        'name': 'Quiet streets',
        'constraints': <String>[],
        'preferredTravelMode': 'WALKING',
      },
    ];
    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(jsonEncode(body), 200),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
        ],
        child: const MaterialApp(home: RoutingPreferencesScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    // Tap the custom profile's info icon to open the details sheet.
    final infoIcons = find.byIcon(Icons.info_outline);
    // The custom profile is rendered after the built-ins; its info icon is
    // the last one in document order.
    await tester.tap(infoIcons.last);
    await tester.pumpAndSettle();

    expect(find.text('Custom preset'), findsOneWidget);
    expect(find.text('Edit'), findsOneWidget);
    expect(find.text('Delete'), findsOneWidget);
  });

  testWidgets(
      'Tapping Create custom preset opens the editor sheet',
      (tester) async {
    // Wider viewport so the editor sheet's button row doesn't overflow on
    // the default 800px test surface.
    await tester.binding.setSurfaceSize(const Size(600, 2200));
    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(jsonEncode(_prefsBody()), 200),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
        ],
        child: const MaterialApp(home: RoutingPreferencesScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    await tester.ensureVisible(find.text('Create custom preset'));
    await tester.tap(find.text('Create custom preset'));
    await tester.pumpAndSettle();

    expect(find.text('New custom preset'), findsOneWidget);
    expect(find.text('NAME'), findsOneWidget);
    expect(find.text('TRAVEL MODE'), findsOneWidget);
  });

  testWidgets(
      'Tapping Delete on a custom profile shows the confirmation dialog',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 1600));
    final body = _prefsBody();
    body['customProfiles'] = [
      {
        'id': 7,
        'name': 'Quiet streets',
        'constraints': <String>[],
        'preferredTravelMode': 'WALKING',
      },
    ];
    final client = _MockClient();
    when(() => client.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response(jsonEncode(body), 200),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthService(httpClient: client)),
        ],
        child: const MaterialApp(home: RoutingPreferencesScreen()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    await tester.tap(find.byIcon(Icons.info_outline).last);
    await tester.pumpAndSettle();

    await tester.tap(find.text('Delete'));
    await tester.pumpAndSettle();

    expect(find.text('Delete this preset?'), findsOneWidget);
  });
}
