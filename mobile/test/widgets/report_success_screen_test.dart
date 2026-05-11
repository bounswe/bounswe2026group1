import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/models/report_model.dart';
import 'package:mapcess/screens/report_success_screen.dart';
import 'package:mapcess/theme/app_colors.dart';

void main() {
  setUp(() {
    AppColors.setBrightness(Brightness.light);
  });

  testWidgets('ReportSuccessScreen renders summary and scrolls', (tester) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));

    final report = ReportModel.fromJson({
      'reportId': 101,
      'userId': 1,
      'latitude': 0.0,
      'longitude': 0.0,
      'description': 'Ramp repair requested.',
      'reportType': 'OBSTACLE',
      'environment': 'OUTDOOR',
      'status': 'PENDING',
      'agrees': 0,
      'disagrees': 0,
      'publishDate': '2026-05-10T12:00:00Z',
      'mediaUrls': <String>[],
      'objects': [
        {
          'objectType': 'RAMP',
          'issues': ['MISSING'],
        },
      ],
    });

    await tester.pumpWidget(
      MaterialApp(
        home: ReportSuccessScreen(report: report),
      ),
    );

    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.textContaining('Report Successfully'), findsOneWidget);
    expect(find.textContaining('Return to Home'), findsOneWidget);

    await tester.drag(
      find.byType(SingleChildScrollView),
      const Offset(0, -200),
    );
    await tester.pump();
    expect(tester.takeException(), isNull);
  });
}
