import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:mapcess/screens/report_location_picker_screen.dart';

void main() {
  group('ReportLocationPickerScreen', () {
    testWidgets('renders search field and Confirm Location button',
        (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: ReportLocationPickerScreen(initial: LatLng(41.0857, 29.0510)),
        ),
      );
      await tester.pump();

      expect(find.text('Search for a place…'), findsOneWidget);
      expect(find.text('Confirm Location'), findsOneWidget);
      // The close (×) icon dismisses the picker by popping the route.
      expect(find.byIcon(Icons.close), findsOneWidget);
    });

    testWidgets('Confirm returns the initial pin when nothing was tapped',
        (tester) async {
      const initial = LatLng(38.5, 27.1);
      LatLng? result;

      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (ctx) => Scaffold(
              body: Center(
                child: ElevatedButton(
                  onPressed: () async {
                    result = await Navigator.of(ctx).push<LatLng>(
                      MaterialPageRoute(
                        builder: (_) => const ReportLocationPickerScreen(
                          initial: initial,
                        ),
                      ),
                    );
                  },
                  child: const Text('open'),
                ),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Confirm Location'));
      await tester.pumpAndSettle();

      expect(result, equals(initial));
    });

    testWidgets('typing into the search activates the back arrow affordance',
        (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: ReportLocationPickerScreen(initial: LatLng(41, 29)),
        ),
      );
      await tester.pump();

      // Idle: × close icon is visible, back arrow is not.
      expect(find.byIcon(Icons.close), findsOneWidget);
      expect(find.byIcon(Icons.arrow_back), findsNothing);

      // Focus the search field by tapping it.
      await tester.tap(find.byType(TextField));
      await tester.pump();

      // Active state swaps the close icon for a back arrow.
      expect(find.byIcon(Icons.arrow_back), findsOneWidget);
    });

    testWidgets('search field accepts text input', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: ReportLocationPickerScreen(initial: LatLng(41, 29)),
        ),
      );
      await tester.pump();

      await tester.enterText(find.byType(TextField), 'Boğaziçi');
      // Pump just enough to flush the input event without firing the 500ms
      // debounced Nominatim call — we're asserting the controller behavior,
      // not network search.
      await tester.pump();

      // The TextField shows the typed text.
      expect(find.text('Boğaziçi'), findsOneWidget);
    });
  });
}
