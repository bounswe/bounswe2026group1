import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/screens/make_report_screen.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mapcess/services/api_service.dart';
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
    when(() => mockAuthService.isAuthenticated).thenReturn(true);
  });

  Widget createMakeReportScreen() {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<AuthService>.value(value: mockAuthService),
      ],
      child: const MaterialApp(
        home: MakeReportScreen(),
      ),
    );
  }

  group('MakeReportScreen Tests', () {
    testWidgets('renders screen properly', (WidgetTester tester) async {
      await tester.pumpWidget(createMakeReportScreen());
      
      expect(find.text('Post Report'), findsOneWidget);
    });

    testWidgets('shows validation errors when submitting empty', (WidgetTester tester) async {
      await tester.pumpWidget(createMakeReportScreen());
      
      // Tap Submit Report
      final button = find.text('Post Report');
      await tester.ensureVisible(button);
      await tester.tap(button);
      await tester.pump();

      expect(find.text('Add at least one object to describe the report.'), findsOneWidget);
    });
  });
}
