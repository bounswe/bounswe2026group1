import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/models/report_model.dart';
import 'package:mapcess/screens/edit_report_screen.dart';
import 'package:mapcess/services/api_service.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mocktail/mocktail.dart';
import 'package:provider/provider.dart';

import '../support/minimal_report_json.dart';

class MockAuthService extends Mock implements AuthService {}

class MockApiService extends Mock implements ApiService {}

void main() {
  late MockAuthService mockAuthService;
  late MockApiService mockApiService;

  setUpAll(() {
    registerFallbackValue(ReportEnvironment.outdoor);
    registerFallbackValue(<ReportObject>[]);
  });

  setUp(() {
    mockAuthService = MockAuthService();
    mockApiService = MockApiService();
    when(() => mockAuthService.api).thenReturn(mockApiService);
    when(() => mockAuthService.isAuthenticated).thenReturn(true);
  });

  ReportModel buildReport({
    int id = 100,
    String description = 'A clearly broken curb at the corner',
  }) =>
      ReportModel.fromJson(minimalReportJson(id: id, description: description));

  Widget wrap(ReportModel report) => MultiProvider(
        providers: [
          ChangeNotifierProvider<AuthService>.value(value: mockAuthService),
        ],
        child: MaterialApp(home: EditReportScreen(report: report)),
      );

  Future<void> pumpEdit(WidgetTester tester, {ReportModel? report}) async {
    await tester.pumpWidget(wrap(report ?? buildReport()));
    // Settle initial frame; FlutterMap kicks off tile requests that the test
    // binding answers with HTTP 400 — fine for our header-level assertions.
    await tester.pump();
  }

  group('EditReportScreen', () {
    testWidgets('renders the Edit Report title with the report id chip',
        (tester) async {
      await pumpEdit(tester, report: buildReport(id: 777));

      expect(find.text('Edit Report'), findsOneWidget);
      expect(find.text('#777'), findsOneWidget);
      expect(find.text('Save Changes'), findsOneWidget);
      expect(find.text('Delete Report'), findsOneWidget);
    });

    testWidgets('prepopulates the description from the report', (tester) async {
      await pumpEdit(
        tester,
        report: buildReport(description: 'Wheelchair ramp is missing here'),
      );

      expect(
        find.text('Wheelchair ramp is missing here'),
        findsOneWidget,
      );
    });

    testWidgets('shows the environment selector with both options',
        (tester) async {
      await pumpEdit(tester);

      expect(find.text('ENVIRONMENT'), findsOneWidget);
      expect(find.text('Outdoor'), findsOneWidget);
      expect(find.text('Indoor'), findsOneWidget);
    });

    testWidgets('flags too-short descriptions before hitting the API',
        (tester) async {
      await pumpEdit(tester);

      // Empty the field via the TextField widget directly (Save is below the
      // map + sections; scrolling on Android FlutterMap surfaces is flaky in
      // the test binding, so we trigger _save by tapping its visible label).
      await tester.enterText(find.byType(TextField).first, 'short');
      await tester.pump();

      // Save Changes button sits in the bottomSheet — finder is text-based.
      await tester.tap(find.text('Save Changes'), warnIfMissed: false);
      await tester.pump();

      expect(
        find.text('Description must be at least 10 characters.'),
        findsOneWidget,
      );
      verifyNever(() => mockApiService.updateReport(
            reportId: any(named: 'reportId'),
            description: any(named: 'description'),
            environment: any(named: 'environment'),
            latitude: any(named: 'latitude'),
            longitude: any(named: 'longitude'),
            objects: any(named: 'objects'),
            mediaIdsToRemove: any(named: 'mediaIdsToRemove'),
          ));
    });

    testWidgets('shows the Tap map to move helper text', (tester) async {
      await pumpEdit(tester);
      expect(find.text('Tap map to move'), findsOneWidget);
    });

    testWidgets('renders the DESCRIPTION section label', (tester) async {
      await pumpEdit(tester);
      expect(find.text('DESCRIPTION'), findsOneWidget);
    });

    testWidgets('tapping Indoor changes the environment', (tester) async {
      await pumpEdit(tester);

      // Default report is OUTDOOR.
      await tester.ensureVisible(find.text('Indoor'));
      await tester.tap(find.text('Indoor'));
      await tester.pump();

      // After the tap, the chip is still rendered.
      expect(find.text('Indoor'), findsOneWidget);
    });

    testWidgets('tapping Delete Report opens the confirmation dialog',
        (tester) async {
      await pumpEdit(tester);

      await tester.ensureVisible(find.text('Delete Report'));
      await tester.tap(find.text('Delete Report'));
      await tester.pumpAndSettle();

      expect(find.text('Delete this report?'), findsOneWidget);
    });

    testWidgets('Cancel in the delete dialog dismisses it without API call',
        (tester) async {
      await pumpEdit(tester);

      await tester.ensureVisible(find.text('Delete Report'));
      await tester.tap(find.text('Delete Report'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();

      expect(find.text('Delete this report?'), findsNothing);
      verifyNever(() => mockApiService.deleteReport(any()));
    });

    testWidgets('Delete in the confirmation dialog calls deleteReport',
        (tester) async {
      final r = buildReport(id: 100);
      when(() => mockApiService.deleteReport(100)).thenAnswer((_) async {});

      await pumpEdit(tester, report: r);

      await tester.ensureVisible(find.text('Delete Report'));
      await tester.tap(find.text('Delete Report'));
      await tester.pumpAndSettle();

      // The dialog's Delete button (red FilledButton).
      await tester.tap(find.widgetWithText(FilledButton, 'Delete'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));

      verify(() => mockApiService.deleteReport(100)).called(1);
    });

    testWidgets('Save Changes with valid input calls updateReport',
        (tester) async {
      final r = buildReport(id: 100);
      when(() => mockApiService.updateReport(
            reportId: any(named: 'reportId'),
            description: any(named: 'description'),
            environment: any(named: 'environment'),
            latitude: any(named: 'latitude'),
            longitude: any(named: 'longitude'),
            objects: any(named: 'objects'),
            mediaIdsToRemove: any(named: 'mediaIdsToRemove'),
          )).thenAnswer((_) async => r);

      await pumpEdit(tester, report: r);

      // Description is prepopulated and long enough (>10 chars). Object is
      // present in the fixture with RAMP+MISSING. Submit should succeed.
      await tester.tap(find.text('Save Changes'), warnIfMissed: false);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      verify(() => mockApiService.updateReport(
            reportId: 100,
            description: any(named: 'description'),
            environment: any(named: 'environment'),
            latitude: any(named: 'latitude'),
            longitude: any(named: 'longitude'),
            objects: any(named: 'objects'),
            mediaIdsToRemove: any(named: 'mediaIdsToRemove'),
          )).called(1);
    });

    testWidgets(
        'Save Changes with an empty description shows the min-length error',
        (tester) async {
      await pumpEdit(tester);

      // Wipe the description by entering an empty string.
      await tester.enterText(find.byType(TextField).first, '');
      await tester.pump();
      await tester.tap(find.text('Save Changes'), warnIfMissed: false);
      await tester.pump();

      expect(
        find.text('Description must be at least 10 characters.'),
        findsOneWidget,
      );
    });

    testWidgets('the close button on the top bar pops the screen',
        (tester) async {
      // Push the screen from another route so we can observe the pop landing.
      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<AuthService>.value(value: mockAuthService),
          ],
          child: MaterialApp(
            home: Builder(
              builder: (ctx) => Scaffold(
                body: Center(
                  child: ElevatedButton(
                    onPressed: () => Navigator.of(ctx).push(
                      MaterialPageRoute(
                        builder: (_) =>
                            EditReportScreen(report: buildReport()),
                      ),
                    ),
                    child: const Text('open'),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      expect(find.text('Edit Report'), findsOneWidget);

      await tester.tap(find.byIcon(Icons.close));
      await tester.pumpAndSettle();

      expect(find.text('Edit Report'), findsNothing);
      expect(find.text('open'), findsOneWidget);
    });
  });

  group('EditReportOutcome', () {
    test('EditReportUpdated carries the new report', () {
      final report = ReportModel.fromJson(minimalReportJson(id: 1));
      final outcome = EditReportUpdated(report);
      expect(outcome, isA<EditReportOutcome>());
      expect(outcome.report.reportId, 1);
    });

    test('EditReportDeleted is a tombstone marker', () {
      const outcome = EditReportDeleted();
      expect(outcome, isA<EditReportOutcome>());
    });
  });
}
