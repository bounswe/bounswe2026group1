import 'package:flutter/material.dart';
import 'screens/login_screen.dart';

void main() {
  runApp(const MapcessApp());
}

class MapcessApp extends StatelessWidget {
  const MapcessApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Mapcess',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF176a21)),
        fontFamily: 'Inter',
        useMaterial3: true,
      ),
      home: const LoginScreen(),
    );
  }
}