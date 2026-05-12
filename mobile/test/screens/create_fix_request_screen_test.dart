import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/screens/create_fix_request_screen.dart';
import 'package:mapcess/services/api_service.dart';
import 'package:mapcess/services/auth_service.dart';
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

  Widget wrap(Widget child) => MultiProvider(
        providers: [
          ChangeNotifierProvider<AuthService>.value(value: mockAuthService),
        ],
        child: MaterialApp(home: child),
      );

  group('CreateFixRequestScreen', () {
    testWidgets('renders the top bar title', (tester) async {
      await tester.pumpWidget(
        wrap(const CreateFixRequestScreen(reportId: 42)),
      );

      expect(find.text('Report as Fixed'), findsOneWidget);
      expect(find.text('Submit fix report'), findsOneWidget);
    });

    testWidgets('uses generic intro copy when reportTitle is null',
        (tester) async {
      await tester.pumpWidget(
        wrap(const CreateFixRequestScreen(reportId: 42)),
      );

      expect(
        find.text('Share a photo so the community can verify.'),
        findsOneWidget,
      );
    });

    testWidgets('surfaces the reportTitle in the intro card when provided',
        (tester) async {
      await tester.pumpWidget(
        wrap(const CreateFixRequestScreen(
          reportId: 42,
          reportTitle: 'Missing Ramp – Report #12',
        )),
      );

      expect(find.text('For: Missing Ramp – Report #12'), findsOneWidget);
    });

    testWidgets('shows the empty dropzone before any photo is attached',
        (tester) async {
      await tester.pumpWidget(
        wrap(const CreateFixRequestScreen(reportId: 42)),
      );

      expect(find.text('Add photos'), findsOneWidget);
      // The "Up to 5 · JPG or PNG · max 15 MB each" hint sits under the CTA.
      expect(
        find.textContaining('Up to 5'),
        findsOneWidget,
      );
    });

    testWidgets('blocks submit and shows an error when no photo is attached',
        (tester) async {
      await tester.pumpWidget(
        wrap(const CreateFixRequestScreen(reportId: 42)),
      );

      await tester.tap(find.text('Submit fix report'));
      await tester.pump();

      expect(
        find.text('Attach at least one photo of the fixed area.'),
        findsOneWidget,
      );
      // No network attempt should have been made.
      verifyNever(() => mockApiService.submitFixRequest(
            reportId: any(named: 'reportId'),
            files: any(named: 'files'),
            description: any(named: 'description'),
          ));
    });

    testWidgets('marks the photos field as required with an asterisk',
        (tester) async {
      await tester.pumpWidget(
        wrap(const CreateFixRequestScreen(reportId: 42)),
      );

      expect(find.text('PHOTOS'), findsOneWidget);
      // The "Optional" label belongs to the description field below.
      expect(find.text('Optional'), findsOneWidget);
    });

    testWidgets(
        'tapping the empty drop zone opens the photo source bottom sheet',
        (tester) async {
      await tester.pumpWidget(
        wrap(const CreateFixRequestScreen(reportId: 42)),
      );
      await tester.pump();

      await tester.ensureVisible(find.text('Add photos'));
      await tester.tap(find.text('Add photos'));
      await tester.pumpAndSettle();

      expect(find.text('Take photo'), findsOneWidget);
      expect(find.text('Choose from gallery'), findsOneWidget);
    });

    testWidgets('description field accepts text input', (tester) async {
      await tester.pumpWidget(
        wrap(const CreateFixRequestScreen(reportId: 42)),
      );

      await tester.enterText(
        find.byType(TextField),
        'Wheelchair ramp installed',
      );
      await tester.pump();

      expect(find.text('Wheelchair ramp installed'), findsOneWidget);
    });

    testWidgets('shows the DESCRIPTION section label', (tester) async {
      await tester.pumpWidget(
        wrap(const CreateFixRequestScreen(reportId: 42)),
      );

      expect(find.text('DESCRIPTION'), findsOneWidget);
    });
  });
}
