import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'screens/login_screen.dart';
import 'services/auth_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final auth = AuthService();
  await auth.init();
  runApp(MapcessApp(auth: auth));
}

class MapcessApp extends StatelessWidget {
  final AuthService auth;

  const MapcessApp({super.key, required this.auth});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider.value(
      value: auth,
      child: MaterialApp(
        title: 'Mapcess',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF176a21)),
          fontFamily: 'Inter',
          useMaterial3: true,
        ),
        home: const LoginScreen(),
      ),
    );
  }
}
