import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../theme/app_colors.dart';

const _themeModeKey = 'theme_mode';

class ThemeService extends ChangeNotifier {
  ThemeMode _mode = ThemeMode.system;

  ThemeMode get mode => _mode;

  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(_themeModeKey);
    _mode = _decode(saved);
    notifyListeners();
  }

  Future<void> setMode(ThemeMode mode) async {
    if (mode == _mode) return;
    _mode = mode;
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_themeModeKey, _encode(mode));
  }

  /// Resolves [_mode] against the platform brightness when the user picks
  /// [ThemeMode.system].
  Brightness brightnessFor(BuildContext context) {
    switch (_mode) {
      case ThemeMode.light:
        return Brightness.light;
      case ThemeMode.dark:
        return Brightness.dark;
      case ThemeMode.system:
        return MediaQuery.platformBrightnessOf(context);
    }
  }

  /// Mirrors [brightnessFor] but bypasses [BuildContext] — used at app boot
  /// to seed [AppColors] before the first frame.
  Brightness resolveAgainstPlatform(Brightness platform) {
    switch (_mode) {
      case ThemeMode.light:
        return Brightness.light;
      case ThemeMode.dark:
        return Brightness.dark;
      case ThemeMode.system:
        return platform;
    }
  }

  static String _encode(ThemeMode m) => switch (m) {
        ThemeMode.light => 'light',
        ThemeMode.dark => 'dark',
        ThemeMode.system => 'system',
      };

  static ThemeMode _decode(String? s) => switch (s) {
        'light' => ThemeMode.light,
        'dark' => ThemeMode.dark,
        _ => ThemeMode.system,
      };
}
