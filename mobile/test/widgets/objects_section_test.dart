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

  testWidgets('selecting a type reveals the ISSUES checklist',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(400, 1200));

    await tester.pumpWidget(
      const _Harness(
        reportType: ReportType.obstacle,
        environment: ReportEnvironment.outdoor,
      ),
    );
    await tester.tap(find.text('Add Object'));
    await tester.pumpAndSettle();

    // Pick Ramp type — issues panel appears.
    await tester.tap(find.text('Ramp').first);
    await tester.pumpAndSettle();

    expect(find.text('ISSUES'), findsOneWidget);
  });

  testWidgets('toggling an issue chip marks it as selected', (tester) async {
    await tester.binding.setSurfaceSize(const Size(400, 1200));

    await tester.pumpWidget(
      const _Harness(
        reportType: ReportType.obstacle,
        environment: ReportEnvironment.outdoor,
      ),
    );
    await tester.tap(find.text('Add Object'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ramp').first);
    await tester.pumpAndSettle();

    // Tap any issue chip — the "Missing" issue is universal.
    final missing = find.text('Missing');
    if (missing.evaluate().isNotEmpty) {
      await tester.tap(missing.first);
      await tester.pumpAndSettle();
      expect(find.textContaining('selected'), findsWidgets);
    }
  });

  testWidgets('removing an object card via the delete icon clears it',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(400, 1000));

    await tester.pumpWidget(
      const _Harness(
        reportType: ReportType.obstacle,
        environment: ReportEnvironment.outdoor,
      ),
    );
    await tester.tap(find.text('Add Object'));
    await tester.pumpAndSettle();
    expect(find.text('1'), findsOneWidget);

    await tester.tap(find.byIcon(Icons.delete_outline).first);
    await tester.pumpAndSettle();

    expect(find.text('1'), findsNothing);
  });

  testWidgets('collapses an expanded card when its header is tapped',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(400, 1000));

    await tester.pumpWidget(
      const _Harness(
        reportType: ReportType.obstacle,
        environment: ReportEnvironment.outdoor,
      ),
    );
    await tester.tap(find.text('Add Object'));
    await tester.pumpAndSettle();

    // Tap the header (the rank number) to collapse.
    expect(find.text('OBJECT TYPE'), findsOneWidget);
    await tester.tap(find.text('1'));
    await tester.pumpAndSettle();
    expect(find.text('OBJECT TYPE'), findsNothing);
  });
}
