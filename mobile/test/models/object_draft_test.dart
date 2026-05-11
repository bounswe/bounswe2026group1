import 'package:flutter_test/flutter_test.dart';

import 'package:mapcess/models/report_model.dart';
import 'package:mapcess/widgets/objects_section.dart';

void main() {
  group('ObjectDraft.fromReportObject', () {
    test('parses measurements JSON blob into map', () {
      final obj = ReportObject(
        objectType: ObjectType.ramp,
        issues: const [IssueType.tooSteep],
        measurements: '{"slope_percent":"12","width_cm":"110"}',
      );

      final draft = ObjectDraft.fromReportObject(obj, id: 'a1');

      expect(draft.objectType, ObjectType.ramp);
      expect(draft.measurements['slope_percent'], '12');
      expect(draft.showMeasurements, true);
    });

    test('invalid measurements string yields empty map', () {
      final obj = ReportObject(
        objectType: ObjectType.door,
        issues: const [],
        measurements: 'not-json',
      );

      final draft = ObjectDraft.fromReportObject(obj, id: 'b2');

      expect(draft.measurements, isEmpty);
      expect(draft.showMeasurements, false);
    });
  });
}
