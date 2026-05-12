import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/screens/home_screen.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mapcess/services/sse_service.dart';
import 'package:mapcess/models/sse_event.dart';
import 'package:mocktail/mocktail.dart';
import 'package:provider/provider.dart';

class MockAuthService extends Mock implements AuthService {}
class MockSseService extends Mock implements SseService {}

void main() {
  late MockAuthService mockAuthService;
  late MockSseService mockSseService;

  setUp(() {
    mockAuthService = MockAuthService();
    mockSseService = MockSseService();

    when(() => mockAuthService.isAuthenticated).thenReturn(false);
    when(() => mockSseService.events).thenAnswer((_) => const Stream<SseEvent>.empty());
    when(() => mockSseService.needsFullRefresh).thenReturn(false);
    when(() => mockSseService.connected).thenReturn(false);
  });

  Widget createHomeScreen() {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<AuthService>.value(value: mockAuthService),
        ChangeNotifierProvider<SseService>.value(value: mockSseService),
      ],
      child: const MaterialApp(
        home: HomeScreen(),
      ),
    );
  }

  group('HomeScreen Tests', () {
    testWidgets('renders home screen structure', (WidgetTester tester) async {
      await tester.pumpWidget(createHomeScreen());
      
      // The map takes time to load or we might just check for the FABs
      expect(find.byType(Scaffold), findsWidgets);
    });
  });
}
