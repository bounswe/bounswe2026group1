import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/screens/register_screen.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mocktail/mocktail.dart';
import 'package:provider/provider.dart';

class MockAuthService extends Mock implements AuthService {}

void main() {
  late MockAuthService mockAuthService;

  setUp(() {
    mockAuthService = MockAuthService();
  });

  Widget createRegisterScreen() {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<AuthService>.value(value: mockAuthService),
      ],
      child: const MaterialApp(
        home: RegisterScreen(),
      ),
    );
  }

  group('RegisterScreen Tests', () {
    testWidgets('renders register form properly', (WidgetTester tester) async {
      await tester.pumpWidget(createRegisterScreen());

      expect(find.text('Mapcess'), findsOneWidget);
      expect(find.text('Join our\ncommunity'), findsOneWidget);
      expect(find.text('Full Name'), findsOneWidget);
      expect(find.text('Email Address'), findsOneWidget);
      expect(find.text('Password'), findsOneWidget);
      expect(find.text('Create Account'), findsOneWidget);
    });

    testWidgets('shows validation errors when fields are empty', (WidgetTester tester) async {
      await tester.pumpWidget(createRegisterScreen());

      // Scroll down to the button
      final button = find.text('Create Account');
      await tester.ensureVisible(button);

      // Tap Create Account directly
      await tester.tap(button);
      await tester.pump();

      expect(find.text('Name is required'), findsOneWidget);
      expect(find.text('Email is required'), findsOneWidget);
      expect(find.text('Password is required'), findsOneWidget);
    });

    testWidgets('shows password validation rules', (WidgetTester tester) async {
      await tester.pumpWidget(createRegisterScreen());

      final button = find.text('Create Account');
      await tester.ensureVisible(button);

      // Enter short password
      await tester.enterText(find.byType(TextField).at(2), 'short');
      await tester.tap(button);
      await tester.pump();
      expect(find.text('Min 8 characters required'), findsOneWidget);

      // No uppercase
      await tester.enterText(find.byType(TextField).at(2), 'lowercase1!');
      await tester.tap(button);
      await tester.pump();
      expect(find.text('Must contain at least 1 uppercase letter'), findsOneWidget);
      
      // No special character
      await tester.enterText(find.byType(TextField).at(2), 'Password123');
      await tester.tap(button);
      await tester.pump();
      expect(find.text('Must contain at least 1 special character'), findsOneWidget);
    });

    testWidgets('shows error when Terms of Service is not checked', (WidgetTester tester) async {
      await tester.pumpWidget(createRegisterScreen());

      // Enter valid text
      await tester.enterText(find.byType(TextField).at(0), 'John Doe');
      await tester.enterText(find.byType(TextField).at(1), 'john@example.com');
      await tester.enterText(find.byType(TextField).at(2), 'ValidPass1!');

      final button = find.text('Create Account');
      await tester.ensureVisible(button);

      // Tap Sign In without checking checkbox
      await tester.tap(button);
      await tester.pump();

      expect(find.text('Please accept the Terms of Service to continue.'), findsOneWidget);
    });
  });
}
