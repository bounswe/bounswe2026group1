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

    testWidgets('renders Edit profile and Preferences action buttons when authenticated',
        (WidgetTester tester) async {
      when(() => mockAuthService.isAuthenticated).thenReturn(true);
      when(() => mockAuthService.userId).thenReturn(1);
      when(() => mockApiService.getMyProfile()).thenAnswer((_) async => {
            'name': 'X',
            'email': 'x@x.com',
            'bio': '',
            'points': 0,
            'rank': 99,
            'contributionStats': {'reportsSubmitted': 0, 'routesPlanned': 0},
            'badges': <String>[],
          });
      when(() => mockApiService.getReportsByUser(1))
          .thenAnswer((_) async => []);

      await tester.pumpWidget(createProfileScreen());
      await tester.pumpAndSettle();

      expect(find.text('Edit profile'), findsOneWidget);
      expect(find.text('Preferences'), findsOneWidget);
      expect(find.text('Replay tutorial'), findsOneWidget);
    });

    testWidgets('tapping Edit profile reveals the Save / Cancel controls',
        (WidgetTester tester) async {
      when(() => mockAuthService.isAuthenticated).thenReturn(true);
      when(() => mockAuthService.userId).thenReturn(1);
      when(() => mockApiService.getMyProfile()).thenAnswer((_) async => {
            'name': 'X',
            'email': 'x@x.com',
            'bio': 'old bio',
            'points': 0,
            'rank': 99,
            'contributionStats': {'reportsSubmitted': 0, 'routesPlanned': 0},
            'badges': <String>[],
          });
      when(() => mockApiService.getReportsByUser(1))
          .thenAnswer((_) async => []);

      await tester.pumpWidget(createProfileScreen());
      await tester.pumpAndSettle();

      await tester.tap(find.text('Edit profile'));
      await tester.pumpAndSettle();

      expect(find.text('Save'), findsOneWidget);
      expect(find.text('Cancel'), findsOneWidget);
      // Bio text becomes editable; the typed value should round-trip.
      await tester.enterText(
        find.widgetWithText(TextFormField, 'old bio').first,
        'new bio',
      );
      await tester.pump();
      expect(find.text('new bio'), findsOneWidget);
    });

    testWidgets('loads getReportsByUser on mount when authenticated',
        (WidgetTester tester) async {
      when(() => mockAuthService.isAuthenticated).thenReturn(true);
      when(() => mockAuthService.userId).thenReturn(42);
      when(() => mockApiService.getMyProfile()).thenAnswer((_) async => {
            'name': 'Test',
            'email': 't@t.com',
            'bio': '',
            'points': 0,
            'rank': 1,
            'contributionStats': {'reportsSubmitted': 0, 'routesPlanned': 0},
            'badges': <String>[],
          });
      when(() => mockApiService.getReportsByUser(42))
          .thenAnswer((_) async => []);

      await tester.pumpWidget(createProfileScreen());
      await tester.pumpAndSettle();

      verify(() => mockApiService.getReportsByUser(42)).called(1);
      verify(() => mockApiService.getMyProfile()).called(1);
    });

    testWidgets('does not call profile APIs when unauthenticated',
        (WidgetTester tester) async {
      // Default setUp leaves isAuthenticated == false.
      await tester.pumpWidget(createProfileScreen());
      await tester.pumpAndSettle();

      verifyNever(() => mockApiService.getMyProfile());
      verifyNever(() => mockApiService.getReportsByUser(any()));
    });

    testWidgets('Save in edit mode calls updateUserProfile with the typed bio',
        (WidgetTester tester) async {
      when(() => mockAuthService.isAuthenticated).thenReturn(true);
      when(() => mockAuthService.userId).thenReturn(1);
      when(() => mockApiService.getMyProfile()).thenAnswer((_) async => {
            'name': 'Old Name',
            'email': 'x@x.com',
            'bio': 'old bio',
            'points': 0,
            'rank': 99,
            'contributionStats': {'reportsSubmitted': 0, 'routesPlanned': 0},
            'badges': <String>[],
          });
      when(() => mockApiService.getReportsByUser(1))
          .thenAnswer((_) async => []);
      when(() => mockApiService.updateUserProfile(
            userId: any(named: 'userId'),
            name: any(named: 'name'),
            bio: any(named: 'bio'),
          )).thenAnswer((_) async => {
            'name': 'New Name',
            'email': 'x@x.com',
            'bio': 'new bio',
            'points': 0,
            'rank': 99,
            'contributionStats': {'reportsSubmitted': 0, 'routesPlanned': 0},
            'badges': <String>[],
          });

      await tester.pumpWidget(createProfileScreen());
      await tester.pumpAndSettle();

      await tester.tap(find.text('Edit profile'));
      await tester.pumpAndSettle();

      // Edit the bio.
      await tester.enterText(
        find.widgetWithText(TextFormField, 'old bio').first,
        'new bio',
      );
      await tester.pump();

      await tester.tap(find.text('Save'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));

      verify(() => mockApiService.updateUserProfile(
            userId: 1,
            name: any(named: 'name'),
            bio: any(named: 'bio'),
          )).called(1);
    });

    testWidgets('Cancel in edit mode returns to view mode without saving',
        (WidgetTester tester) async {
      when(() => mockAuthService.isAuthenticated).thenReturn(true);
      when(() => mockAuthService.userId).thenReturn(1);
      when(() => mockApiService.getMyProfile()).thenAnswer((_) async => {
            'name': 'X',
            'email': 'x@x.com',
            'bio': 'orig',
            'points': 0,
            'rank': 99,
            'contributionStats': {'reportsSubmitted': 0, 'routesPlanned': 0},
            'badges': <String>[],
          });
      when(() => mockApiService.getReportsByUser(1))
          .thenAnswer((_) async => []);

      await tester.pumpWidget(createProfileScreen());
      await tester.pumpAndSettle();

      await tester.tap(find.text('Edit profile'));
      await tester.pumpAndSettle();
      expect(find.text('Save'), findsOneWidget);

      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();

      expect(find.text('Save'), findsNothing);
      expect(find.text('Edit profile'), findsOneWidget);
      verifyNever(() => mockApiService.updateUserProfile(
            userId: any(named: 'userId'),
            name: any(named: 'name'),
            bio: any(named: 'bio'),
          ));
    });
  });
}
