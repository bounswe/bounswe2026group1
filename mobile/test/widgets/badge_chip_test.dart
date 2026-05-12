import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/widgets/badge_chip.dart';

void main() {
  group('BadgeChip', () {
    testWidgets('renders empty widget for unknown badge', (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: Scaffold(
          body: BadgeChip(badge: 'UNKNOWN_BADGE'),
        ),
      ));

      expect(find.byType(SizedBox), findsOneWidget);
      expect(find.byType(Container), findsNothing);
    });

    testWidgets('renders full chip for known badge', (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: Scaffold(
          body: BadgeChip(badge: 'TRUSTED_REPORTER'),
        ),
      ));

      expect(find.text('Trusted Reporter'), findsOneWidget);
      expect(find.byIcon(Icons.verified), findsOneWidget);
      expect(find.byType(Tooltip), findsOneWidget);
    });

    testWidgets('renders compact chip without label for known badge', (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: Scaffold(
          body: BadgeChip(badge: 'EXPERT_MAPPER', compact: true),
        ),
      ));

      expect(find.text('Expert Mapper'), findsNothing); // Label should not be present
      expect(find.byIcon(Icons.workspace_premium), findsOneWidget);
      expect(find.byType(Tooltip), findsOneWidget);
    });
  });

  group('BadgeList', () {
    testWidgets('renders nothing when empty list is provided', (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: Scaffold(
          body: BadgeList(badges: []),
        ),
      ));

      expect(find.byType(SizedBox), findsOneWidget);
      expect(find.byType(Wrap), findsNothing);
    });

    testWidgets('renders badges correctly', (WidgetTester tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: Scaffold(
          body: BadgeList(badges: ['TRUSTED_REPORTER', 'EXPERT_MAPPER']),
        ),
      ));

      expect(find.byType(Wrap), findsOneWidget);
      expect(find.byType(BadgeChip), findsNWidgets(2));
      expect(find.text('Trusted Reporter'), findsOneWidget);
      expect(find.text('Expert Mapper'), findsOneWidget);
    });
  });
}
