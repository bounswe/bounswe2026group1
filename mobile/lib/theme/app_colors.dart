import 'package:flutter/material.dart';

/// Theme-aware color palette. Callsites use the static getters
/// (`AppColors.primary`, etc.); the value returned depends on the current
/// brightness, which is set via [setBrightness] from MaterialApp's builder
/// before children render.
class AppColors {
  AppColors._();

  static Brightness _brightness = Brightness.light;
  static bool get _dark => _brightness == Brightness.dark;

  static void setBrightness(Brightness b) {
    _brightness = b;
  }

  // ── Brand ────────────────────────────────────────────────────────────────
  static Color get primary =>
      _dark ? const Color(0xFF8DD58B) : const Color(0xFF176a21);
  static Color get primaryDim =>
      _dark ? const Color(0xFFA7E29F) : const Color(0xFF025d16);

  /// Lighter green used in primary gradients (`primary → primaryAccent`).
  static Color get primaryAccent =>
      _dark ? const Color(0xFFB8F0B2) : const Color(0xFF9DF197);

  // ── Surfaces ─────────────────────────────────────────────────────────────
  static Color get surface =>
      _dark ? const Color(0xFF111413) : const Color(0xFFF6F6F6);
  static Color get surfaceContainerLowest =>
      _dark ? const Color(0xFF0A0C0B) : const Color(0xFFFFFFFF);
  static Color get surfaceContainer =>
      _dark ? const Color(0xFF1B1F1E) : const Color(0xFFE7E8E8);
  static Color get surfaceContainerHigh =>
      _dark ? const Color(0xFF252928) : const Color(0xFFE1E3E3);

  /// Subtle off-surface tint used by the top app bar.
  static Color get surfaceTint =>
      _dark ? const Color(0xFF161A18) : const Color(0xFFF4F7F4);

  // ── Foreground on surfaces ───────────────────────────────────────────────
  static Color get onSurface =>
      _dark ? const Color(0xFFE3E5E4) : const Color(0xFF2D2F2F);
  static Color get onSurfaceVariant =>
      _dark ? const Color(0xFFB0B3B2) : const Color(0xFF5A5C5C);
  static Color get onPrimary =>
      _dark ? const Color(0xFF003910) : const Color(0xFFD1FFC8);

  // ── Outlines ─────────────────────────────────────────────────────────────
  static Color get outlineVariant =>
      _dark ? const Color(0xFF3A3D3C) : const Color(0xFFACADAD);
  static Color get outline =>
      _dark ? const Color(0xFF6E7170) : const Color(0xFF767777);

  // ── Other ────────────────────────────────────────────────────────────────
  static Color get secondary =>
      _dark ? const Color(0xFFB0C5CF) : const Color(0xFF495F69);
  static Color get inverseSurface =>
      _dark ? const Color(0xFFE3E5E4) : const Color(0xFF0C0F0F);

  // ── Semantic / status (light/dark variants tuned for contrast) ───────────
  static Color get error =>
      _dark ? const Color(0xFFFFB4AB) : const Color(0xFFBA1A1A);
  static Color get errorStrong =>
      _dark ? const Color(0xFFFFB4AB) : const Color(0xFFB02500);
  static Color get errorContainer =>
      _dark ? const Color(0xFF5C1A14) : const Color(0xFFFFDAD6);
  static Color get errorContainerBorder =>
      _dark ? const Color(0xFF8C2A1F) : const Color(0xFFFFB4AB);
  static Color get onErrorContainer =>
      _dark ? const Color(0xFFFFDAD6) : const Color(0xFFBA1A1A);

  /// Bold red used for map error markers / banners.
  static Color get errorBanner =>
      _dark ? const Color(0xFFE57373) : const Color(0xFFE53935);
  static Color get errorBannerStrong =>
      _dark ? const Color(0xFFE57373) : const Color(0xFFF95630);

  static Color get success =>
      _dark ? const Color(0xFF7DD876) : const Color(0xFF2E7D32);
  static Color get successContainer =>
      _dark ? const Color(0xFF1F3D24) : const Color(0xFFDCF5DC);

  static Color get warning =>
      _dark ? const Color(0xFFFFB68F) : const Color(0xFFE65100);
  static Color get warningSoft =>
      _dark ? const Color(0xFFFFCA9D) : const Color(0xFFFFA726);

  static Color get info =>
      _dark ? const Color(0xFF8AB4F8) : const Color(0xFF1A73E8);
  static Color get infoContainer =>
      _dark ? const Color(0xFF1F3A4D) : const Color(0xFFCFE6F2);
  static Color get onInfoContainer =>
      _dark ? const Color(0xFFB7CDD8) : const Color(0xFF40555F);

  /// Accent purple/teal used by tag chips on the map.
  static Color get accentPurple =>
      _dark ? const Color(0xFFCBA5E5) : const Color(0xFF6A1B9A);
  static Color get accentTeal =>
      _dark ? const Color(0xFF80CBC4) : const Color(0xFF00695C);
  static Color get accentBlue =>
      _dark ? const Color(0xFF8AB4F8) : const Color(0xFF1565C0);

  // ── Neutrals that flip ───────────────────────────────────────────────────
  /// Text/icon foreground on top of `primary` blocks (chips, FABs, gradient
  /// buttons). White in light mode, dark green in dark mode for contrast.
  static Color get onPrimarySolid =>
      _dark ? const Color(0xFF003910) : Colors.white;

  /// Card / sheet background on top of which "elevated" content sits.
  static Color get cardSurface =>
      _dark ? const Color(0xFF1B1F1E) : Colors.white;

  /// Surface that reads as *raised* against [surfaceContainer] in both modes.
  /// Material 3 inverts the elevation hierarchy between light and dark, so we
  /// pick brightest-in-mode rather than relying on a single container token.
  static Color get raisedSurface =>
      _dark ? const Color(0xFF2C302F) : Colors.white;

  /// Generic translucent shadow color.
  static Color get shadow => _dark
      ? Colors.black.withValues(alpha: 0.6)
      : Colors.black.withValues(alpha: 0.14);

  /// Scrim used behind full-screen image viewers.
  static Color get scrim => Colors.black;

  /// Foreground color used on top of [scrim].
  static Color get onScrim => Colors.white;
}
