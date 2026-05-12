import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/screens/leaderboard_screen.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mapcess/services/api_service.dart';
import 'package:mapcess/services/sse_service.dart';
import 'package:mapcess/models/sse_event.dart';
import 'package:mocktail/mocktail.dart';
import 'package:provider/provider.dart';

class MockAuthService extends Mock implements AuthService {}
class MockApiService extends Mock implements ApiService {}
class MockSseService extends Mock implements SseService {}

Map<String, dynamic> _leaderboardPayload({
  List<Map<String, dynamic>> entries = const [],
  Map<String, dynamic>? callerRank,
}) =>
    {
      'entries': entries,
      'callerRank': callerRank,
    };

Map<String, dynamic> _entryJson({
  int rank = 1,
  int userId = 10,
  String name = 'Alice',
  int points = 500,
}) =>
    {
      'rank': rank,
      'userId': userId,
      'name': name,
      'avatarUrl': null,
      'points': points,
    };

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

  Widget createScreen() => MultiProvider(
        providers: [
          ChangeNotifierProvider<AuthService>.value(value: mockAuthService),
          ChangeNotifierProvider<SseService>.value(value: mockSseService),
        ],
        child: const MaterialApp(home: LeaderboardScreen()),
      );

  group('LeaderboardScreen', () {
    testWidgets('shows entries after successful load', (tester) async {
      when(() => mockApiService.getLeaderboard()).thenAnswer(
        (_) async => _leaderboardPayload(
          entries: [_entryJson(name: 'Alice', points: 500)],
        ),
      );

      await tester.pumpWidget(createScreen());
      await tester.pumpAndSettle();

      expect(find.text('Alice'), findsOneWidget);
      expect(find.text('500'), findsOneWidget);
    });

    testWidgets('shows empty message when no entries', (tester) async {
      when(() => mockApiService.getLeaderboard()).thenAnswer(
        (_) async => _leaderboardPayload(),
      );

      await tester.pumpWidget(createScreen());
      await tester.pumpAndSettle();

      expect(find.textContaining('No ranked users'), findsOneWidget);
    });

    testWidgets('shows error message when API fails', (tester) async {
      when(() => mockApiService.getLeaderboard())
          .thenThrow(ApiException(500, 'Server error'));

      await tester.pumpWidget(createScreen());
      await tester.pumpAndSettle();

      expect(find.textContaining('Server error'), findsOneWidget);
    });

    testWidgets('shows login hint when unauthenticated and no callerRank',
        (tester) async {
      when(() => mockApiService.getLeaderboard()).thenAnswer(
        (_) async => _leaderboardPayload(
          entries: [_entryJson()],
        ),
      );

      await tester.pumpWidget(createScreen());
      await tester.pumpAndSettle();

      expect(find.textContaining('Log in to see your own rank'), findsOneWidget);
    });

    testWidgets('shows callerRank banner when present', (tester) async {
      when(() => mockAuthService.isAuthenticated).thenReturn(true);
      when(() => mockApiService.getLeaderboard()).thenAnswer(
        (_) async => _leaderboardPayload(
          entries: [_entryJson()],
          callerRank: {'rank': 3, 'points': 120},
        ),
      );

      await tester.pumpWidget(createScreen());
      await tester.pumpAndSettle();

      expect(find.textContaining('YOUR RANK'), findsOneWidget);
    });

    testWidgets('shows AppBar title Leaderboard', (tester) async {
      when(() => mockApiService.getLeaderboard()).thenAnswer(
        (_) async => _leaderboardPayload(),
      );

      await tester.pumpWidget(createScreen());
      await tester.pump();

      expect(find.text('Leaderboard'), findsOneWidget);
    });
  });
}
