import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/models/report_model.dart';
import 'package:mapcess/theme/app_colors.dart';
import 'package:mapcess/widgets/report_card.dart';

void main() {
  setUp(() {
    AppColors.setBrightness(Brightness.light);
  });

  testWidgets('ReportCard renders headline and taps propagate', (tester) async {
    await tester.binding.setSurfaceSize(const Size(420, 720));

    var tapped = false;
    final report = ReportModel.fromJson({
      'reportId': 1,
      'userId': 2,
      'latitude': 0.0,
      'longitude': 0.0,
      'description': 'Description text for the card body.',
      'reportType': 'OBSTACLE',
      'environment': 'OUTDOOR',
      'status': 'PENDING',
      'agrees': 3,
      'disagrees': 1,
      'publishDate': '2026-05-01T10:00:00Z',
      'mediaUrls': <String>[],
      'objects': [
        {
          'objectType': 'RAMP',
          'issues': ['TOO_STEEP'],
        },
      ],
    });

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ReportCard(
            report: report,
            onTap: () => tapped = true,
          ),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.textContaining('Ramp'), findsWidgets);

    await tester.tap(find.byType(InkWell).first);
    expect(tapped, true);
  });
}
