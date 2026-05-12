import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/screens/report_detail_screen.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mapcess/services/api_service.dart';
import 'package:mapcess/services/sse_service.dart';
import 'package:mapcess/models/sse_event.dart';
import 'package:mocktail/mocktail.dart';
import 'package:provider/provider.dart';
import '../support/minimal_report_json.dart';
import 'package:mapcess/models/report_model.dart';

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
    when(() => mockAuthService.isAuthenticated).thenReturn(true);
    when(() => mockAuthService.userId).thenReturn(1);
    when(() => mockApiService.getUserById(any())).thenAnswer((_) async => null);
    when(() => mockSseService.events).thenAnswer((_) => const Stream<SseEvent>.empty());
  });

  Widget createReportDetailScreen() {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<AuthService>.value(value: mockAuthService),
        ChangeNotifierProvider<SseService>.value(value: mockSseService),
      ],
      child: MaterialApp(
        home: ReportDetailScreen(
          report: ReportModel.fromJson(minimalReportJson(id: 100, description: 'Test Detailed Description')),
        ),
      ),
    );
  }

  group('ReportDetailScreen Tests', () {
    testWidgets('shows content properly', (WidgetTester tester) async {
      await tester.pumpWidget(createReportDetailScreen());

      await tester.pumpAndSettle();

      expect(find.text('Test Detailed Description'), findsOneWidget);
    });
  });
}
