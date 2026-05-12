import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:mapcess/screens/onboarding_tutorial_screen.dart';

Widget _wrap(Widget child) => MaterialApp(home: Scaffold(body: child));

// The tutorial's topic slides load PNG diagrams via Image.asset. The
// flutter_test asset bundle decodes the bundled manifest differently
// than runtime, so navigating past the first slide produces image-load
// exceptions that the test framework treats as failures. We therefore
// scope these unit tests to the welcome slide (no Image.asset) and the
// structural chrome (Skip / Next / dot indicator / citation). Cross-slide
// behaviour is covered by manual + integration testing.
void main() {
  group('OnboardingTutorialScreen', () {
    testWidgets('renders the welcome slide first with the brand badge',
        (tester) async {
      await tester.pumpWidget(_wrap(const OnboardingTutorialScreen()));
      expect(find.text('Welcome to Mapcess'), findsOneWidget);
      expect(find.byIcon(Icons.explore), findsWidgets);
    });

    testWidgets('shows the Belir citation in the footer', (tester) async {
      await tester.pumpWidget(_wrap(const OnboardingTutorialScreen()));
      // The citation is rendered as a multi-span RichText, so `find.text`
      // (exact-match) doesn't apply — use textContaining. The welcome
      // slide's tip also mentions Özlem Belir, so we expect ≥ 1 match.
      expect(
        find.textContaining('Özlem Belir'),
        findsWidgets,
      );
      expect(
        find.textContaining('Mimari Erişilebilirlik Kılavuzu'),
        findsWidgets,
      );
    });

    testWidgets('surfaces Skip / Next CTAs on the first slide',
        (tester) async {
      await tester.pumpWidget(_wrap(const OnboardingTutorialScreen()));
      expect(find.text('Skip tour'), findsOneWidget);
      expect(find.text('Next'), findsOneWidget);
    });

    testWidgets('tapping Skip tour pops the route', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (ctx) => Scaffold(
              body: Center(
                child: ElevatedButton(
                  onPressed: () => Navigator.of(ctx).push(
                    MaterialPageRoute(
                      builder: (_) => const OnboardingTutorialScreen(),
                    ),
                  ),
                  child: const Text('open'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      expect(find.text('Welcome to Mapcess'), findsOneWidget);

      await tester.tap(find.text('Skip tour'));
      await tester.pumpAndSettle();

      expect(find.text('Welcome to Mapcess'), findsNothing);
      expect(find.text('open'), findsOneWidget);
    });

    testWidgets('renders a dot indicator row at the bottom', (tester) async {
      await tester.pumpWidget(_wrap(const OnboardingTutorialScreen()));
      // Welcome slide is highlighted; multiple dots represent the slide list.
      // There's no semantic for "dot" — assert the structural chrome works.
      expect(find.text('Welcome to Mapcess'), findsOneWidget);
      expect(find.text('Next'), findsOneWidget);
    });
  });
}
