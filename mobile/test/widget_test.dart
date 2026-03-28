import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/main.dart';

void main() {
  testWidgets('Mapcess smoke test', (WidgetTester tester) async {
    await tester.pumpWidget(const MapcessApp());
  });
}