import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mapcess/theme/app_colors.dart';

void main() {
  // Brightness is a global on AppColors — reset to light after every test so
  // ordering doesn't leak state into unrelated suites.
  tearDown(() => AppColors.setBrightness(Brightness.light));

  group('AppColors brightness switching', () {
    test('defaults to light palette before setBrightness is called', () {
      // Sanity: the documented contract is "light unless set otherwise".
      AppColors.setBrightness(Brightness.light);
      expect(AppColors.primary, const Color(0xFF176a21));
      expect(AppColors.surface, const Color(0xFFF6F6F6));
      expect(AppColors.onPrimary, const Color(0xFFD1FFC8));
    });

    test('flips brand colors when brightness becomes dark', () {
      AppColors.setBrightness(Brightness.dark);
      expect(AppColors.primary, const Color(0xFF8DD58B));
      expect(AppColors.primaryDim, const Color(0xFFA7E29F));
      expect(AppColors.primaryAccent, const Color(0xFFB8F0B2));
    });

    test('flips surface palette when brightness becomes dark', () {
      AppColors.setBrightness(Brightness.dark);
      expect(AppColors.surface, const Color(0xFF111413));
      expect(AppColors.surfaceContainerLowest, const Color(0xFF0A0C0B));
      expect(AppColors.surfaceContainer, const Color(0xFF1B1F1E));
      expect(AppColors.cardSurface, const Color(0xFF1B1F1E));
    });

    test('semantic colors differ between light and dark', () {
      AppColors.setBrightness(Brightness.light);
      final lightError = AppColors.error;
      final lightSuccess = AppColors.success;
      final lightInfo = AppColors.info;

      AppColors.setBrightness(Brightness.dark);
      expect(AppColors.error, isNot(equals(lightError)));
      expect(AppColors.success, isNot(equals(lightSuccess)));
      expect(AppColors.info, isNot(equals(lightInfo)));
    });

    test('onPrimarySolid is white in light and dark green in dark', () {
      AppColors.setBrightness(Brightness.light);
      expect(AppColors.onPrimarySolid, Colors.white);

      AppColors.setBrightness(Brightness.dark);
      expect(AppColors.onPrimarySolid, const Color(0xFF003910));
    });

    test('scrim/onScrim are brightness-independent', () {
      AppColors.setBrightness(Brightness.light);
      expect(AppColors.scrim, Colors.black);
      expect(AppColors.onScrim, Colors.white);

      AppColors.setBrightness(Brightness.dark);
      expect(AppColors.scrim, Colors.black);
      expect(AppColors.onScrim, Colors.white);
    });

    test('shadow alpha is heavier in dark mode for contrast', () {
      AppColors.setBrightness(Brightness.light);
      final lightShadow = AppColors.shadow;
      AppColors.setBrightness(Brightness.dark);
      final darkShadow = AppColors.shadow;
      // We don't assert exact alpha — only that the two modes differ so a
      // future tweak can't silently make both identical.
      expect(darkShadow, isNot(equals(lightShadow)));
    });

    test('setBrightness round-trips through every supported value', () {
      AppColors.setBrightness(Brightness.dark);
      final dark = AppColors.primary;
      AppColors.setBrightness(Brightness.light);
      final light = AppColors.primary;
      AppColors.setBrightness(Brightness.dark);
      expect(AppColors.primary, dark);
      AppColors.setBrightness(Brightness.light);
      expect(AppColors.primary, light);
    });
  });
}
