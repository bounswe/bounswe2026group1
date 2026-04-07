import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:mapcess/main.dart';
import 'package:mapcess/screens/login_screen.dart';
import 'package:mapcess/screens/register_screen.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mapcess/services/api_service.dart';
import 'package:mapcess/services/sse_service.dart';

// ─── Helpers ─────────────────────────────────────────────────────────────────

Widget _withAuth(Widget screen) {
  return MultiProvider(
    providers: [
      ChangeNotifierProvider(create: (_) => AuthService()),
      Provider(create: (_) => SseService()),
    ],
    child: MaterialApp(home: screen),
  );
}

// ─── Tests ───────────────────────────────────────────────────────────────────

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  // ── Smoke test ──────────────────────────────────────────────────────────────

  testWidgets('Mapcess smoke test', (WidgetTester tester) async {
    await tester.binding.setSurfaceSize(const Size(400, 900));
    await tester.pumpWidget(MapcessApp(auth: AuthService(), sse: SseService()));
  });

  // ── ApiException.userMessage ────────────────────────────────────────────────

  test('ApiException: 401 returns invalid credentials message', () {
    const e = ApiException(401, '');
    expect(e.userMessage, 'Invalid email or password.');
  });

  test('ApiException: 409 returns duplicate email message', () {
    const e = ApiException(409, '');
    expect(e.userMessage, 'Email already registered.');
  });

  test('ApiException: backend message takes precedence over status code', () {
    const e = ApiException(400, 'Password too weak.');
    expect(e.userMessage, 'Password too weak.');
  });

  test('ApiException: 500 returns server error message', () {
    const e = ApiException(500, '');
    expect(e.userMessage, 'Server error. Please try again later.');
  });

  // ── Login screen ────────────────────────────────────────────────────────────

  testWidgets('Login: shows error when fields are empty', (tester) async {
    await tester.binding.setSurfaceSize(const Size(400, 900));
    await tester.pumpWidget(_withAuth(const LoginScreen()));

    // Tap the Sign In button without entering any credentials
    await tester.tap(find.text('Sign In').first);
    await tester.pump();

    expect(
      find.text('Please enter your email and password.'),
      findsOneWidget,
    );
  });

  // ── Register screen ─────────────────────────────────────────────────────────

  testWidgets('Register: shows error when fields are empty', (tester) async {
    await tester.binding.setSurfaceSize(const Size(400, 1100));
    await tester.pumpWidget(_withAuth(const RegisterScreen()));

    await tester.tap(find.text('Create Account'));
    await tester.pump();

    expect(find.text('Please fill in all fields.'), findsOneWidget);
  });

  testWidgets('Register: shows error when terms not accepted', (tester) async {
    await tester.binding.setSurfaceSize(const Size(400, 1100));
    await tester.pumpWidget(_withAuth(const RegisterScreen()));

    // Fill in all three fields
    final fields = find.byType(TextField);
    await tester.enterText(fields.at(0), 'John Doe');
    await tester.enterText(fields.at(1), 'john@example.com');
    await tester.enterText(fields.at(2), 'Password1!');

    // Submit without ticking the terms checkbox
    await tester.tap(find.text('Create Account'));
    await tester.pump();

    expect(
      find.text('Please accept the Terms of Service to continue.'),
      findsOneWidget,
    );
  });
}