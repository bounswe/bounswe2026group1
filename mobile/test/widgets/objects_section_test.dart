import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/models/report_model.dart';
import 'package:mapcess/theme/app_colors.dart';
import 'package:mapcess/widgets/objects_section.dart';

class _Harness extends StatefulWidget {
  final ReportType reportType;
  final ReportEnvironment environment;

  const _Harness({
    required this.reportType,
    required this.environment,
  });

  @override
  State<_Harness> createState() => _HarnessState();
}

class _HarnessState extends State<_Harness> {
  final List<ObjectDraft> objects = [];

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        body: SingleChildScrollView(
          child: ObjectsSection(
            objects: objects,
            reportType: widget.reportType,
            environment: widget.environment,
            onChanged: () => setState(() {}),
          ),
        ),
      ),
    );
  }
}

void main() {
  setUp(() {
    AppColors.setBrightness(Brightness.light);
  });

  testWidgets('ObjectsSection: add object card and scroll without overflow',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(400, 700));

    await tester.pumpWidget(
      const _Harness(
        reportType: ReportType.obstacle,
        environment: ReportEnvironment.outdoor,
      ),
    );

    expect(find.text('OBJECTS'), findsOneWidget);
    await tester.tap(find.text('Add Object'));
    await tester.pumpAndSettle();

    expect(find.textContaining('Select a type'), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.drag(find.byType(SingleChildScrollView), const Offset(0, -120));
    await tester.pump();
    expect(tester.takeException(), isNull);
  });

  testWidgets('FEATURE report only offers Ramp type pool', (tester) async {
    await tester.binding.setSurfaceSize(const Size(400, 900));

    await tester.pumpWidget(
      const _Harness(
        reportType: ReportType.feature,
        environment: ReportEnvironment.outdoor,
      ),
    );

    await tester.tap(find.text('Add Object'));
    await tester.pumpAndSettle();

    expect(find.text('Ramp'), findsWidgets);
    expect(find.text('Elevator'), findsNothing);
  });
}
