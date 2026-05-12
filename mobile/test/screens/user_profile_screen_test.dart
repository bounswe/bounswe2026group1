import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/screens/user_profile_screen.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mapcess/services/api_service.dart';
import 'package:mapcess/models/report_model.dart';
import 'package:mocktail/mocktail.dart';
import 'package:provider/provider.dart';

class MockAuthService extends Mock implements AuthService {}
class MockApiService extends Mock implements ApiService {}

void main() {
  late MockAuthService mockAuthService;
  late MockApiService mockApiService;

  setUp(() {
    mockAuthService = MockAuthService();
    mockApiService = MockApiService();

    when(() => mockAuthService.api).thenReturn(mockApiService);
    when(() => mockApiService.getReportsByUser(any()))
        .thenAnswer((_) async => const <ReportModel>[]);
  });

  Widget createScreen({int userId = 42, String? initialName}) =>
      ChangeNotifierProvider<AuthService>.value(
        value: mockAuthService,
        child: MaterialApp(
          home: UserProfileScreen(userId: userId, initialName: initialName),
        ),
      );

  group('UserProfileScreen', () {
    testWidgets('shows initialName in header while loading', (tester) async {
      // getUserById never completes — use a dart:async Completer so no timer leaks.
      final completer = Completer<Map<String, dynamic>?>();
      when(() => mockApiService.getUserById(any()))
          .thenAnswer((_) => completer.future);

      await tester.pumpWidget(createScreen(initialName: 'Charlie'));
      await tester.pump();

      expect(find.text('Charlie'), findsOneWidget);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);

      // Complete the future to avoid pending async work on teardown.
      completer.complete(null);
      await tester.pumpAndSettle();
    });

    testWidgets('shows user name from API response', (tester) async {
      when(() => mockApiService.getUserById(42)).thenAnswer(
        (_) async => {'id': 42, 'name': 'Diana Prince', 'points': 250},
      );

      await tester.pumpWidget(createScreen());
      await tester.pumpAndSettle();

      expect(find.text('Diana Prince'), findsWidgets);
    });

    testWidgets('shows error message when getUserById fails', (tester) async {
      when(() => mockApiService.getUserById(any()))
          .thenThrow(ApiException(404, 'User not found'));

      await tester.pumpWidget(createScreen());
      await tester.pumpAndSettle();

      expect(find.textContaining('User not found'), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);
    });

    testWidgets('shows Profile title when no name available', (tester) async {
      when(() => mockApiService.getUserById(any())).thenAnswer((_) async => null);

      await tester.pumpWidget(createScreen());
      await tester.pumpAndSettle();

      expect(find.text('Profile'), findsOneWidget);
    });

    testWidgets('shows REPORTS stat card after successful load', (tester) async {
      when(() => mockApiService.getUserById(42)).thenAnswer(
        (_) async => {'id': 42, 'name': 'Eve', 'points': 0},
      );

      await tester.pumpWidget(createScreen());
      await tester.pumpAndSettle();

      // The stat grid always renders a REPORTS card for any loaded profile.
      expect(find.text('REPORTS'), findsOneWidget);
    });
  });
}

