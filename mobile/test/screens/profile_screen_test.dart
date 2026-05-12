import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/screens/profile_screen.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mapcess/services/api_service.dart';
import 'package:mapcess/services/sse_service.dart';
import 'package:mapcess/models/sse_event.dart';
import 'package:mocktail/mocktail.dart';
import 'package:provider/provider.dart';

class MockAuthService extends Mock implements AuthService {}
class MockApiService extends Mock implements ApiService {}
class MockSseService extends Mock implements SseService {}

void main() {
  late MockAuthService mockAuthService;
  late MockApiService mockApiService;
  late MockSseService mockSseService;

  setUp(() {
    mockAuthService = MockAuthService();
    mockApiService = MockApiService();
    mockSseService = MockSseService();

    when(() => mockAuthService.api).thenReturn(mockApiService);
    when(() => mockAuthService.isAuthenticated).thenReturn(false);
    when(() => mockSseService.events).thenAnswer((_) => const Stream<SseEvent>.empty());
  });

  Widget createProfileScreen() {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<AuthService>.value(value: mockAuthService),
        ChangeNotifierProvider<SseService>.value(value: mockSseService),
      ],
      child: const MaterialApp(
        home: ProfileScreen(),
      ),
    );
  }

  group('ProfileScreen Tests', () {
    testWidgets('shows guest view when not authenticated', (WidgetTester tester) async {
      await tester.pumpWidget(createProfileScreen());
      await tester.pumpAndSettle();

      expect(find.text('Sign In'), findsOneWidget);
    });

    testWidgets('shows profile when authenticated', (WidgetTester tester) async {
      when(() => mockAuthService.isAuthenticated).thenReturn(true);
      when(() => mockAuthService.userId).thenReturn(1);
      
      when(() => mockApiService.getMyProfile()).thenAnswer((_) async => {
        'name': 'Test User',
        'email': 'test@example.com',
        'bio': 'Test Bio',
        'points': 100,
        'rank': 5,
        'contributionStats': {
          'reportsSubmitted': 10,
          'routesPlanned': 2
        },
        'badges': ['TRUSTED_REPORTER']
      });
      when(() => mockApiService.getReportsByUser(1)).thenAnswer((_) async => []);

      await tester.pumpWidget(createProfileScreen());
      
      // Wait for future to complete
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      await tester.pumpAndSettle();

      expect(find.text('Test User'), findsOneWidget);
      expect(find.text('Test Bio'), findsOneWidget);
      expect(find.text('10'), findsWidgets); // Reports
      expect(find.text('2'), findsWidgets); // Routes
      expect(find.text('100'), findsOneWidget); // Points
      expect(find.text('#5'), findsOneWidget); // Rank
    });
  });
}
