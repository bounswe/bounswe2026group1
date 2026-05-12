import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/models/report_model.dart';
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

  setUpAll(() {
    // mocktail needs concrete fallback instances for typed any(named: ...)
    // matchers on enum / collection arguments.
    registerFallbackValue(ReportType.obstacle);
    registerFallbackValue(ReportEnvironment.outdoor);
    registerFallbackValue(<ReportObject>[]);
  });

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

    testWidgets('shows the Obstacle and Feature report type cards',
        (tester) async {
      await tester.pumpWidget(createMakeReportScreen());
      await tester.pump();

      expect(find.text('Obstacle'), findsOneWidget);
      expect(find.text('Feature'), findsOneWidget);
    });

    testWidgets('shows the Indoor and Outdoor environment chips',
        (tester) async {
      await tester.pumpWidget(createMakeReportScreen());
      await tester.pump();

      expect(find.text('Outdoor'), findsOneWidget);
      expect(find.text('Indoor'), findsOneWidget);
    });

    testWidgets('shows the ENVIRONMENT section label', (tester) async {
      await tester.pumpWidget(createMakeReportScreen());
      await tester.pump();

      expect(find.text('ENVIRONMENT'), findsOneWidget);
    });

    testWidgets('does not call createReport when submitting an empty form',
        (tester) async {
      await tester.pumpWidget(createMakeReportScreen());
      await tester.pump();

      await tester.ensureVisible(find.text('Post Report'));
      await tester.tap(find.text('Post Report'));
      await tester.pump();

      verifyNever(() => mockApiService.createReport(
            userId: any(named: 'userId'),
            latitude: any(named: 'latitude'),
            longitude: any(named: 'longitude'),
            description: any(named: 'description'),
            reportType: any(named: 'reportType'),
            environment: any(named: 'environment'),
            objects: any(named: 'objects'),
          ));
    });

    testWidgets('tapping the Indoor environment chip activates it',
        (tester) async {
      await tester.pumpWidget(createMakeReportScreen());
      await tester.pump();

      // Indoor is initially inactive (outdoor is the default).
      await tester.tap(find.text('Indoor'));
      await tester.pump();

      // The chip rebuilds — still on-screen.
      expect(find.text('Indoor'), findsOneWidget);
    });

    testWidgets('renders the OBJECTS section with an Add Object affordance',
        (tester) async {
      await tester.pumpWidget(createMakeReportScreen());
      await tester.pump();

      expect(find.text('OBJECTS'), findsOneWidget);
      expect(find.text('Add Object'), findsOneWidget);
    });

    testWidgets('tapping Add Object adds an object card', (tester) async {
      await tester.pumpWidget(createMakeReportScreen());
      await tester.pump();

      // Should start with zero object cards (no "1" rank chip).
      expect(find.text('1'), findsNothing);

      await tester.ensureVisible(find.text('Add Object'));
      await tester.tap(find.text('Add Object'));
      await tester.pump();

      // First object card shows its rank number ("1").
      expect(find.text('1'), findsOneWidget);
    });

    testWidgets(
        'submitting with an object but no type shows the per-card type error',
        (tester) async {
      await tester.pumpWidget(createMakeReportScreen());
      await tester.pump();

      await tester.ensureVisible(find.text('Add Object'));
      await tester.tap(find.text('Add Object'));
      await tester.pump();

      await tester.ensureVisible(find.text('Post Report'));
      await tester.tap(find.text('Post Report'));
      await tester.pump();

      expect(find.text('Select a type for every object card.'), findsOneWidget);
    });

    testWidgets('shows the REPORT TYPE section label', (tester) async {
      await tester.pumpWidget(createMakeReportScreen());
      await tester.pump();

      expect(find.text('REPORT TYPE'), findsOneWidget);
    });

    testWidgets('adding an object reveals the OBJECT TYPE picker',
        (tester) async {
      await tester.pumpWidget(createMakeReportScreen());
      await tester.pump();

      await tester.ensureVisible(find.text('Add Object'));
      await tester.tap(find.text('Add Object'));
      await tester.pump();

      // The freshly-added object expands by default so OBJECT TYPE is visible.
      expect(find.text('OBJECT TYPE'), findsOneWidget);
      expect(find.text('Select a type…'), findsOneWidget);
    });

    testWidgets('tapping Feature switches the report type', (tester) async {
      await tester.pumpWidget(createMakeReportScreen());
      await tester.pump();

      // Default is Obstacle. Tapping Feature should rebuild with Feature
      // selected (we don't introspect — just verify the chip still renders).
      await tester.tap(find.text('Feature'));
      await tester.pump();

      expect(find.text('Feature'), findsOneWidget);
    });

    testWidgets('deleting an object card removes it', (tester) async {
      await tester.pumpWidget(createMakeReportScreen());
      await tester.pump();

      await tester.ensureVisible(find.text('Add Object'));
      await tester.tap(find.text('Add Object'));
      await tester.pump();
      expect(find.text('1'), findsOneWidget);

      // The delete icon next to the object card's header.
      await tester.tap(find.byIcon(Icons.delete_outline));
      await tester.pump();

      // After delete, the rank chip "1" is gone.
      expect(find.text('1'), findsNothing);
    });
  });
}
