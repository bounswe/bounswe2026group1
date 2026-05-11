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

    await tester.pump();
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    // Avoid pumpAndSettle: RefreshIndicator / physics can leave pending work on
    // some platforms; explicit pumps wait for the async GET.
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
}
