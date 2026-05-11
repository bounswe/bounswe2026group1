import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:mapcess/services/theme_service.dart';

void main() {
  group('ThemeService', () {
    setUp(() {
      SharedPreferences.setMockInitialValues({});
    });

    test('init restores persisted theme mode', () async {
      SharedPreferences.setMockInitialValues({'theme_mode': 'dark'});
      final theme = ThemeService();
      await theme.init();
      expect(theme.mode, ThemeMode.dark);
    });

    test('init defaults invalid key to system', () async {
      SharedPreferences.setMockInitialValues({'theme_mode': 'unknown'});
      final theme = ThemeService();
      await theme.init();
      expect(theme.mode, ThemeMode.system);
    });

    test('setMode persists and skips duplicate writes', () async {
      final theme = ThemeService();
      await theme.setMode(ThemeMode.light);
      expect(theme.mode, ThemeMode.light);

      await theme.setMode(ThemeMode.light);
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('theme_mode'), 'light');

      await theme.setMode(ThemeMode.dark);
      expect(theme.mode, ThemeMode.dark);
      expect(prefs.getString('theme_mode'), 'dark');
    });

    test('resolveAgainstPlatform mirrors brightnessFor without context',
        () async {
      final theme = ThemeService();
      await theme.init();
      await theme.setMode(ThemeMode.light);
      expect(theme.resolveAgainstPlatform(Brightness.dark), Brightness.light);

      await theme.setMode(ThemeMode.system);
      expect(
        theme.resolveAgainstPlatform(Brightness.dark),
        Brightness.dark,
      );
    });

    testWidgets('brightnessFor follows MediaQuery in system mode',
        (tester) async {
      final theme = ThemeService();
      await theme.init();
      await theme.setMode(ThemeMode.system);

      await tester.pumpWidget(
        MaterialApp(
          theme: ThemeData.light(),
          home: Builder(
            builder: (context) {
              expect(theme.brightnessFor(context), Brightness.light);
              return const SizedBox.shrink();
            },
          ),
        ),
      );
    });
  });
}
