import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/screens/user_search_screen.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mapcess/services/api_service.dart';
import 'package:mapcess/services/theme_service.dart';
import 'package:mocktail/mocktail.dart';
import 'package:provider/provider.dart';

class MockAuthService extends Mock implements AuthService {}
class MockApiService extends Mock implements ApiService {}
class MockThemeService extends Mock implements ThemeService {}

FeedPage<Map<String, dynamic>> _emptyFeed() => const FeedPage(
      content: [],
      number: 0,
      size: 20,
      last: true,
      totalPages: 0,
    );

FeedPage<Map<String, dynamic>> _feedOf(List<Map<String, dynamic>> items) =>
    FeedPage(
      content: items,
      number: 0,
      size: 20,
      last: true,
      totalPages: 1,
    );

void main() {
  late MockAuthService mockAuthService;
  late MockApiService mockApiService;
  late MockThemeService mockThemeService;

  setUp(() {
    mockAuthService = MockAuthService();
    mockApiService = MockApiService();
    mockThemeService = MockThemeService();

    when(() => mockAuthService.api).thenReturn(mockApiService);
    when(() => mockThemeService.addListener(any())).thenReturn(null);
    when(() => mockThemeService.removeListener(any())).thenReturn(null);
  });

  Widget createScreen() => MultiProvider(
        providers: [
          ChangeNotifierProvider<AuthService>.value(value: mockAuthService),
          ChangeNotifierProvider<ThemeService>.value(value: mockThemeService),
        ],
        child: const MaterialApp(home: UserSearchScreen()),
      );

  group('UserSearchScreen', () {
    testWidgets('shows Find people title', (tester) async {
      await tester.pumpWidget(createScreen());
      await tester.pump();

      expect(find.text('Find people'), findsOneWidget);
    });

    testWidgets('shows placeholder before any query is entered',
        (tester) async {
      await tester.pumpWidget(createScreen());
      await tester.pump();

      expect(find.textContaining('Search for other Mapcess users'), findsOneWidget);
    });

    testWidgets('shows short-query hint after debounce fires with 1 character',
        (tester) async {
      await tester.pumpWidget(createScreen());
      await tester.pump();

      await tester.enterText(find.byType(TextField), 'a');
      await tester.pump(); // process text entry
      await tester.pump(const Duration(milliseconds: 350)); // fire 300ms debounce
      await tester.pump(); // rebuild

      expect(
        find.textContaining('at least 2 characters'),
        findsOneWidget,
      );
    });

    testWidgets('shows results after debounce when query is long enough',
        (tester) async {
      when(() => mockApiService.searchUsers(any(), page: any(named: 'page')))
          .thenAnswer((_) async => _feedOf([
                {'id': 7, 'name': 'Bob Builder', 'avatarUrl': null, 'points': 10},
              ]));

      await tester.pumpWidget(createScreen());
      await tester.pump();

      await tester.enterText(find.byType(TextField), 'Bob');
      // Advance past the 300 ms debounce and the async search.
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pumpAndSettle();

      expect(find.text('Bob Builder'), findsOneWidget);
    });

    testWidgets('shows empty-results message when search returns nothing',
        (tester) async {
      when(() => mockApiService.searchUsers(any(), page: any(named: 'page')))
          .thenAnswer((_) async => _emptyFeed());

      await tester.pumpWidget(createScreen());
      await tester.pump();

      await tester.enterText(find.byType(TextField), 'xyz');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pumpAndSettle();

      expect(find.textContaining('No users found'), findsOneWidget);
    });

    testWidgets('shows error when API throws ApiException', (tester) async {
      when(() => mockApiService.searchUsers(any(), page: any(named: 'page')))
          .thenThrow(ApiException(500, 'Internal error'));

      await tester.pumpWidget(createScreen());
      await tester.pump();

      await tester.enterText(find.byType(TextField), 'err');
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pumpAndSettle();

      expect(find.textContaining('Internal error'), findsOneWidget);
    });
  });
}
