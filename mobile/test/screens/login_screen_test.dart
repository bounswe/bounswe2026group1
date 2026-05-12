import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/screens/login_screen.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mapcess/services/api_service.dart';
import 'package:mocktail/mocktail.dart';
import 'package:provider/provider.dart';

class MockAuthService extends Mock implements AuthService {}

void main() {
  late MockAuthService mockAuthService;

  setUp(() {
    mockAuthService = MockAuthService();
  });

  Widget createLoginScreen() {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<AuthService>.value(value: mockAuthService),
      ],
      child: const MaterialApp(
        home: LoginScreen(),
      ),
    );
  }

  group('LoginScreen Tests', () {
    testWidgets('renders login form properly', (WidgetTester tester) async {
      await tester.pumpWidget(createLoginScreen());

      expect(find.text('Mapcess'), findsOneWidget);
      expect(find.text('Welcome\nback'), findsOneWidget);
      expect(find.text('Email Address'), findsOneWidget);
      expect(find.text('Password'), findsOneWidget);
      expect(find.text('Sign In'), findsOneWidget);
      expect(find.text('Continue as Guest'), findsOneWidget);
    });

    testWidgets('shows validation errors when fields are empty', (WidgetTester tester) async {
      await tester.pumpWidget(createLoginScreen());

      // Tap Sign In directly
      await tester.tap(find.text('Sign In'));
      await tester.pump();

      expect(find.text('Email is required'), findsOneWidget);
      expect(find.text('Password is required'), findsOneWidget);
      verifyNever(() => mockAuthService.login(any(), any()));
    });

    testWidgets('calls login with correct credentials', (WidgetTester tester) async {
      when(() => mockAuthService.login('test@example.com', 'password123')).thenAnswer((_) async {});

      await tester.pumpWidget(createLoginScreen());

      // Enter text
      await tester.enterText(find.byType(TextField).first, 'test@example.com');
      await tester.enterText(find.byType(TextField).last, 'password123');

      // Tap Sign In
      await tester.tap(find.text('Sign In'));

      verify(() => mockAuthService.login('test@example.com', 'password123')).called(1);
    });

    testWidgets('shows error when login fails with ApiException', (WidgetTester tester) async {
      when(() => mockAuthService.login('test@example.com', 'wrongpassword')).thenThrow(ApiException(401, 'Invalid credentials'));

      await tester.pumpWidget(createLoginScreen());

      // Enter text
      await tester.enterText(find.byType(TextField).first, 'test@example.com');
      await tester.enterText(find.byType(TextField).last, 'wrongpassword');

      // Tap Sign In
      await tester.tap(find.text('Sign In'));
      await tester.pump();

      expect(find.text('Invalid credentials'), findsOneWidget);
    });

    testWidgets('handles guest login', (WidgetTester tester) async {
      when(() => mockAuthService.loginAsGuest()).thenAnswer((_) async {});

      await tester.pumpWidget(createLoginScreen());

      // Tap Continue as Guest
      await tester.ensureVisible(find.text('Continue as Guest'));
      await tester.tap(find.text('Continue as Guest'));
      // No pump() here because navigation throws ProviderNotFoundException in tests

      verify(() => mockAuthService.loginAsGuest()).called(1);
    });
  });
}
