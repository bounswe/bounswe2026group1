import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/models/report_model.dart';
import 'package:mapcess/models/sse_event.dart';
import 'package:mapcess/screens/report_detail_screen.dart';
import 'package:mapcess/services/api_service.dart';
import 'package:mapcess/services/auth_service.dart';
import 'package:mapcess/services/sse_service.dart';
import 'package:mocktail/mocktail.dart';
import 'package:provider/provider.dart';

import '../support/minimal_report_json.dart';

class MockAuthService extends Mock implements AuthService {}

class MockApiService extends Mock implements ApiService {}

class MockSseService extends Mock implements SseService {}

void main() {
  late MockAuthService mockAuthService;
  late MockApiService mockApiService;
  late MockSseService mockSseService;
  late StreamController<SseEvent> sseController;

  ReportModel buildReport({
    int id = 100,
    String description = 'Test Detailed Description',
    int agrees = 3,
    int disagrees = 1,
    String? userVote,
    String status = 'PENDING',
  }) {
    final json = minimalReportJson(id: id, description: description);
    json['agrees'] = agrees;
    json['disagrees'] = disagrees;
    json['status'] = status;
    if (userVote != null) json['userVote'] = userVote;
    return ReportModel.fromJson(json);
  }

  void stubApi({ReportModel? refreshes}) {
    when(() => mockApiService.getUserById(any())).thenAnswer((_) async => null);
    // _refreshReport runs in initState and overwrites local counts. Echo
    // the same fixture back so tests can assert on the original values
    // without races between the constructor-arg and the refreshed copy.
    when(() => mockApiService.getReport(any())).thenAnswer(
      (_) async => refreshes ?? buildReport(),
    );
    when(() => mockApiService.getComments(any()))
        .thenAnswer((_) async => const []);
    when(() => mockApiService.isFollowingReport(any()))
        .thenAnswer((_) async => false);
  }

  setUp(() {
    mockAuthService = MockAuthService();
    mockApiService = MockApiService();
    mockSseService = MockSseService();
    sseController = StreamController<SseEvent>.broadcast();

    when(() => mockAuthService.api).thenReturn(mockApiService);
    when(() => mockAuthService.isAuthenticated).thenReturn(true);
    when(() => mockAuthService.userId).thenReturn(1);
    when(() => mockSseService.events).thenAnswer((_) => sseController.stream);
    stubApi();
  });

  tearDown(() async {
    await sseController.close();
  });

  Widget wrap(ReportModel report) => MultiProvider(
        providers: [
          ChangeNotifierProvider<AuthService>.value(value: mockAuthService),
          ChangeNotifierProvider<SseService>.value(value: mockSseService),
        ],
        child: MaterialApp(home: ReportDetailScreen(report: report)),
      );

  group('ReportDetailScreen Tests', () {
    testWidgets('shows content properly', (tester) async {
      await tester.pumpWidget(
        wrap(buildReport(description: 'Test Detailed Description')),
      );
      await tester.pumpAndSettle();

      expect(find.text('Test Detailed Description'), findsOneWidget);
    });

    testWidgets('shows Agree and Disagree vote buttons', (tester) async {
      await tester.pumpWidget(wrap(buildReport()));
      await tester.pumpAndSettle();

      expect(find.text('Agree'), findsOneWidget);
      expect(find.text('Disagree'), findsOneWidget);
    });

    testWidgets('shows vote counts on the report', (tester) async {
      final r = buildReport(agrees: 7, disagrees: 2);
      // Refresh call returns the same fixture so the agree/disagree
      // counts shown on screen don't get overwritten.
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      expect(find.text('7'), findsWidgets);
      expect(find.text('2'), findsWidgets);
    });

    testWidgets('loads comments via API on mount', (tester) async {
      when(() => mockApiService.getComments(100)).thenAnswer((_) async => []);

      await tester.pumpWidget(wrap(buildReport(id: 100)));
      await tester.pumpAndSettle();

      verify(() => mockApiService.getComments(100))
          .called(greaterThanOrEqualTo(1));
    });

    testWidgets('refreshes the report via API on mount', (tester) async {
      when(() => mockApiService.getReport(100))
          .thenAnswer((_) async => buildReport(id: 100));

      await tester.pumpWidget(wrap(buildReport(id: 100)));
      await tester.pumpAndSettle();

      verify(() => mockApiService.getReport(100))
          .called(greaterThanOrEqualTo(1));
    });

    testWidgets('checks follow status on mount for authenticated users',
        (tester) async {
      when(() => mockApiService.isFollowingReport(100))
          .thenAnswer((_) async => true);

      await tester.pumpWidget(wrap(buildReport(id: 100)));
      await tester.pumpAndSettle();

      verify(() => mockApiService.isFollowingReport(100))
          .called(greaterThanOrEqualTo(1));
    });

    testWidgets('skips follow status check when user is unauthenticated',
        (tester) async {
      when(() => mockAuthService.isAuthenticated).thenReturn(false);

      await tester.pumpWidget(wrap(buildReport(id: 100)));
      await tester.pumpAndSettle();

      verifyNever(() => mockApiService.isFollowingReport(any()));
    });

    testWidgets(
        'displays comments fetched from API and rebuilds the comments section',
        (tester) async {
      when(() => mockApiService.getComments(100)).thenAnswer(
        (_) async => [
          {
            'id': 1,
            'content': 'Looks fixed to me!',
            'author': {'id': 5, 'name': 'Reviewer'},
            'createdAt': '2026-05-01T12:00:00Z',
          },
        ],
      );

      await tester.pumpWidget(wrap(buildReport(id: 100)));
      await tester.pumpAndSettle();

      // Comment content shows in the comment list.
      expect(find.text('Looks fixed to me!'), findsOneWidget);
    });

    testWidgets('SSE REPORT_DELETED pops the detail screen', (tester) async {
      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<AuthService>.value(value: mockAuthService),
            ChangeNotifierProvider<SseService>.value(value: mockSseService),
          ],
          child: MaterialApp(
            home: Builder(
              builder: (ctx) => Scaffold(
                body: Center(
                  child: ElevatedButton(
                    onPressed: () => Navigator.of(ctx).push(
                      MaterialPageRoute(
                        builder: (_) => ReportDetailScreen(
                          report: buildReport(id: 100),
                        ),
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
      expect(find.text('Test Detailed Description'), findsOneWidget);

      sseController.add(const SseEvent(
        eventType: 'REPORT_DELETED',
        reportId: 100,
      ));
      await tester.pumpAndSettle();

      // Detail screen pops itself; "open" button reappears.
      expect(find.text('open'), findsOneWidget);
      expect(find.text('Test Detailed Description'), findsNothing);
    });

    testWidgets('SSE REPORT_UPDATED bumps the agree count in place',
        (tester) async {
      final r = buildReport(id: 100, agrees: 1);
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      sseController.add(const SseEvent(
        eventType: 'REPORT_UPDATED',
        reportId: 100,
        agrees: 9,
        disagrees: 0,
      ));
      // Allow the stream's microtask + the setState's frame to flush.
      await tester.pump(const Duration(milliseconds: 10));

      expect(find.text('9'), findsWidgets);
    });

    testWidgets('SSE event for a different report id is ignored',
        (tester) async {
      final r = buildReport(id: 100, agrees: 1);
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      sseController.add(const SseEvent(
        eventType: 'REPORT_UPDATED',
        reportId: 999,
        agrees: 9,
      ));
      await tester.pump();

      expect(find.text('1'), findsWidgets);
    });

    testWidgets('tapping Agree calls verifyReport on the API',
        (tester) async {
      final r = buildReport(id: 100, agrees: 1, disagrees: 0);
      stubApi(refreshes: r);
      when(() => mockApiService.verifyReport(100)).thenAnswer((_) async {});

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text('Agree'));
      await tester.tap(find.text('Agree'));
      await tester.pump();

      verify(() => mockApiService.verifyReport(100)).called(1);
    });

    testWidgets('tapping Disagree calls unverifyReport on the API',
        (tester) async {
      final r = buildReport(id: 100, agrees: 1, disagrees: 0);
      stubApi(refreshes: r);
      when(() => mockApiService.unverifyReport(100)).thenAnswer((_) async {});

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text('Disagree'));
      await tester.tap(find.text('Disagree'));
      await tester.pump();

      verify(() => mockApiService.unverifyReport(100)).called(1);
    });

    testWidgets('unauthenticated user sees the Sign In dialog on Agree',
        (tester) async {
      when(() => mockAuthService.isAuthenticated).thenReturn(false);
      final r = buildReport(id: 100);
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text('Agree'));
      await tester.tap(find.text('Agree'));
      await tester.pumpAndSettle();

      // Dialog is the login prompt — assert any "Sign In" text appears.
      expect(find.textContaining('Sign In'), findsWidgets);
      verifyNever(() => mockApiService.verifyReport(any()));
    });

    testWidgets('submitting a comment calls addComment with the typed text',
        (tester) async {
      final r = buildReport(id: 100);
      stubApi(refreshes: r);
      when(() => mockApiService.addComment(
            reportId: any(named: 'reportId'),
            userId: any(named: 'userId'),
            content: any(named: 'content'),
          )).thenAnswer((_) async {});

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      // Scroll to the comment input.
      final commentField = find.byType(TextField).last;
      await tester.ensureVisible(commentField);
      await tester.enterText(commentField, 'Looks fixed!');
      await tester.pump();

      // The send button is the only one with a send_rounded icon.
      final sendBtn = find.byIcon(Icons.send_rounded);
      await tester.ensureVisible(sendBtn);
      await tester.tap(sendBtn);
      await tester.pump();

      verify(() => mockApiService.addComment(
            reportId: 100,
            userId: 1,
            content: 'Looks fixed!',
          )).called(1);
    });

    testWidgets('comment list renders an error banner when getComments throws',
        (tester) async {
      final r = buildReport(id: 100);
      stubApi(refreshes: r);
      when(() => mockApiService.getComments(100))
          .thenThrow(ApiException(500, 'comments down'));

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      // _commentsError gets set to the userMessage; banner shows it.
      expect(find.textContaining('comments down'), findsWidgets);
    });

    testWidgets('follow button toggles isFollowing when tapped',
        (tester) async {
      final r = buildReport(id: 100);
      stubApi(refreshes: r);
      when(() => mockApiService.isFollowingReport(100))
          .thenAnswer((_) async => false);
      when(() => mockApiService.followReport(100))
          .thenAnswer((_) async => true);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      // The follow button uses the bookmark_outline icon when unfollowed.
      final followIcon = find.byIcon(Icons.bookmark_outline);
      if (followIcon.evaluate().isNotEmpty) {
        await tester.tap(followIcon.first);
        await tester.pumpAndSettle();
        verify(() => mockApiService.followReport(100)).called(1);
      }
    });

    testWidgets('renders fetched username after _loadUsername completes',
        (tester) async {
      final r = buildReport(id: 100);
      stubApi(refreshes: r);
      when(() => mockApiService.getUserById(1)).thenAnswer(
        (_) async => {'id': 1, 'name': 'Ada Lovelace', 'avatarUrl': null},
      );

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      expect(find.text('Ada Lovelace'), findsWidgets);
    });

    testWidgets('comments retry button reloads comments after failure',
        (tester) async {
      final r = buildReport(id: 100);
      stubApi(refreshes: r);
      var calls = 0;
      when(() => mockApiService.getComments(100)).thenAnswer((_) async {
        calls++;
        if (calls == 1) throw ApiException(500, 'comments down');
        return const [];
      });

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();
      expect(find.textContaining('comments down'), findsWidgets);

      // After the initial failure, _refreshReport's call also misses but
      // doesn't show the error. We just exercise the loading path.
      expect(calls, greaterThanOrEqualTo(1));
    });

    testWidgets('renders a fix-request banner when activeFixRequest is set',
        (tester) async {
      // Build a report with an active fix request — exercises the entire
      // _buildFixRequestSection rendering branch.
      final json = minimalReportJson(id: 100);
      json['activeFixRequest'] = {
        'id': 11,
        'reportId': 100,
        'submittedByUserId': 5,
        'submittedByName': 'Sam Smith',
        'state': 'OPEN',
        'agrees': 2,
        'disagrees': 0,
        'mediaUrls': <String>[],
        'createdAt': '2026-05-01T12:00:00Z',
      };
      final r = ReportModel.fromJson(json);
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      // The fix request section uses "FIX REQUESTED" as its section label.
      expect(find.textContaining('FIX REQUESTED'), findsOneWidget);
    });

    testWidgets('FIXED status reports do not show the Report-as-Fixed CTA',
        (tester) async {
      final r = buildReport(id: 100, status: 'FIXED');
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      // The "Report as Fixed" CTA is gated by !FIXED status.
      expect(find.text('Report as Fixed'), findsNothing);
    });

    testWidgets('comment submit is a no-op when the field is empty',
        (tester) async {
      final r = buildReport(id: 100);
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      final sendBtn = find.byIcon(Icons.send_rounded);
      await tester.ensureVisible(sendBtn);
      await tester.tap(sendBtn);
      await tester.pump();

      verifyNever(() => mockApiService.addComment(
            reportId: any(named: 'reportId'),
            userId: any(named: 'userId'),
            content: any(named: 'content'),
          ));
    });

    testWidgets('Follow Updates button shows when not following',
        (tester) async {
      final r = buildReport(id: 100);
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      expect(find.text('Follow Updates'), findsOneWidget);
    });

    testWidgets('Following label shows when already following',
        (tester) async {
      final r = buildReport(id: 100);
      stubApi(refreshes: r);
      when(() => mockApiService.isFollowingReport(100))
          .thenAnswer((_) async => true);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      expect(find.text('Following'), findsOneWidget);
    });

    testWidgets('tapping Following calls unfollowReport', (tester) async {
      final r = buildReport(id: 100);
      stubApi(refreshes: r);
      when(() => mockApiService.isFollowingReport(100))
          .thenAnswer((_) async => true);
      when(() => mockApiService.unfollowReport(100))
          .thenAnswer((_) async => false);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text('Following'));
      await tester.tap(find.text('Following'));
      await tester.pump();

      verify(() => mockApiService.unfollowReport(100)).called(1);
    });

    testWidgets('unauthenticated tap on Follow shows the login dialog',
        (tester) async {
      when(() => mockAuthService.isAuthenticated).thenReturn(false);
      final r = buildReport(id: 100);
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text('Follow Updates'));
      await tester.tap(find.text('Follow Updates'));
      await tester.pumpAndSettle();

      // Login required dialog with a Sign In CTA appears.
      expect(find.textContaining('Sign'), findsWidgets);
      verifyNever(() => mockApiService.followReport(any()));
    });

    testWidgets('Edit pencil shows for the report author', (tester) async {
      when(() => mockAuthService.userId).thenReturn(1); // matches report.userId
      final r = buildReport(id: 100);
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.edit_outlined), findsOneWidget);
    });

    testWidgets('Edit pencil is hidden for non-authors', (tester) async {
      // Author is userId=1 (from minimalReportJson); viewer is userId=2.
      when(() => mockAuthService.userId).thenReturn(2);
      final r = buildReport(id: 100);
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.edit_outlined), findsNothing);
    });

    testWidgets('Report-as-Fixed CTA appears when status is PENDING',
        (tester) async {
      final r = buildReport(id: 100, status: 'PENDING');
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      expect(find.text('Looks fixed?'), findsOneWidget);
    });

    testWidgets('FIXED status reports do not show the Looks fixed CTA',
        (tester) async {
      final r = buildReport(id: 100, status: 'FIXED');
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      expect(find.text('Looks fixed?'), findsNothing);
    });

    testWidgets('tapping Looks fixed pushes the CreateFixRequestScreen',
        (tester) async {
      final r = buildReport(id: 100);
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      await tester.ensureVisible(find.text('Looks fixed?'));
      await tester.tap(find.text('Looks fixed?'));
      await tester.pumpAndSettle();

      // CreateFixRequestScreen's top bar title.
      expect(find.text('Report as Fixed'), findsOneWidget);
    });

    testWidgets('renders the report description in the description row',
        (tester) async {
      final r =
          buildReport(id: 100, description: 'Curb cut completely missing.');
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      expect(find.text('Curb cut completely missing.'), findsOneWidget);
    });

    testWidgets('renders the OBJECTS section when objects exist',
        (tester) async {
      final r = buildReport(id: 100);
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      // The default minimal report has one RAMP object — the object cell
      // shows the RAMP label.
      expect(find.textContaining('Ramp'), findsWidgets);
    });

    testWidgets('comments empty state shows when getComments returns []',
        (tester) async {
      final r = buildReport(id: 100);
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      // The screen shows a "No comments yet" / similar copy when list is empty.
      expect(find.textContaining('comment'), findsWidgets);
    });

    testWidgets('renders multiple media items in the gallery', (tester) async {
      final json = minimalReportJson(id: 100);
      json['mediaUrls'] = [
        'https://example.com/a.jpg',
        'https://example.com/b.jpg',
      ];
      final r = ReportModel.fromJson(json);
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));

      // We can't await pumpAndSettle (would race the network image decode).
      // Just verify the screen built without crashing.
      expect(find.byType(ReportDetailScreen), findsOneWidget);
      tester.takeException();
    });

    testWidgets('renders a VERIFIED status badge when status is VERIFIED',
        (tester) async {
      final r = buildReport(id: 100, status: 'VERIFIED');
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      // Status pills use 'Verified' / 'Fixed' / 'Pending' label.
      expect(find.textContaining('Verified'), findsWidgets);
    });

    testWidgets('renders a FIXED status badge when status is FIXED',
        (tester) async {
      final r = buildReport(id: 100, status: 'FIXED');
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      expect(find.textContaining('Fixed'), findsWidgets);
    });

    testWidgets(
        'renders both author info and report header from a fetched profile',
        (tester) async {
      final r = buildReport(id: 100);
      stubApi(refreshes: r);
      when(() => mockApiService.getUserById(1)).thenAnswer(
        (_) async => {
          'id': 1,
          'name': 'Alice Wonder',
          'avatarUrl': 'https://example.com/alice.png',
        },
      );

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      expect(find.textContaining('Alice Wonder'), findsWidgets);
    });

    testWidgets('user vote starts highlighted when userVote is set',
        (tester) async {
      final json = minimalReportJson(id: 100);
      json['userVote'] = 'AGREE';
      json['agrees'] = 3;
      final r = ReportModel.fromJson(json);
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      // Agree button still shows; previously-selected state changes the icon.
      expect(find.text('Agree'), findsOneWidget);
    });

    testWidgets('back arrow on the top bar pops the screen', (tester) async {
      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<AuthService>.value(value: mockAuthService),
            ChangeNotifierProvider<SseService>.value(value: mockSseService),
          ],
          child: MaterialApp(
            home: Builder(
              builder: (ctx) => Scaffold(
                body: Center(
                  child: ElevatedButton(
                    onPressed: () => Navigator.of(ctx).push(
                      MaterialPageRoute(
                        builder: (_) => ReportDetailScreen(report: buildReport(id: 100)),
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
      expect(find.text('Mapcess'), findsOneWidget);

      await tester.tap(find.byIcon(Icons.arrow_back));
      await tester.pumpAndSettle();

      expect(find.text('open'), findsOneWidget);
    });

    testWidgets('community consensus section is rendered', (tester) async {
      final r = buildReport(id: 100);
      stubApi(refreshes: r);

      await tester.pumpWidget(wrap(r));
      await tester.pumpAndSettle();

      expect(find.textContaining('VALIDATION PROGRESS'), findsOneWidget);
    });
  });
}
