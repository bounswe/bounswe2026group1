import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:mapcess/screens/location_picker_screen.dart';

void main() {
  // FlutterMap tile requests are answered by the binding with HTTP 400; we
  // never assert on tiles, only on the chrome around the map.
  Widget wrap(Widget child) => MaterialApp(home: child);

  group('LocationPickerScreen', () {
    testWidgets('shows the helper hint and Confirm CTA on first paint',
        (tester) async {
      await tester.pumpWidget(wrap(const LocationPickerScreen()));
      await tester.pump();

      expect(
        find.text('Tap on the map to choose a location'),
        findsOneWidget,
      );
      expect(find.text('Confirm Location'), findsOneWidget);
      // The "Use my location" FAB is keyed by its tooltip.
      expect(find.byTooltip('Use my location'), findsOneWidget);
    });

    testWidgets('returns the initial pin when Confirm is tapped untouched',
        (tester) async {
      const initial = LatLng(40.0, 30.0);
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
                        builder: (_) => const LocationPickerScreen(
                          initialLocation: initial,
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
      expect(find.text('Confirm Location'), findsOneWidget);

      await tester.tap(find.text('Confirm Location'));
      await tester.pumpAndSettle();

      expect(result, equals(initial));
    });

    testWidgets('back arrow pops the picker with null', (tester) async {
      LatLng? result = const LatLng(0, 0);

      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (ctx) => Scaffold(
              body: Center(
                child: ElevatedButton(
                  onPressed: () async {
                    result = await Navigator.of(ctx).push<LatLng>(
                      MaterialPageRoute(
                        builder: (_) => const LocationPickerScreen(),
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

      await tester.tap(find.byIcon(Icons.arrow_back));
      await tester.pumpAndSettle();

      expect(result, isNull);
      expect(find.text('open'), findsOneWidget);
    });
  });
}
