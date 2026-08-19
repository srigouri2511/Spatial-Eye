import 'package:flutter/material.dart';
import 'ui/screens/navigation_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const SpatialEyeApp());
}

class SpatialEyeApp extends StatefulWidget {
  const SpatialEyeApp({Key? key}) : super(key: key);

  static _SpatialEyeAppState? of(BuildContext context) =>
      context.findAncestorStateOfType<_SpatialEyeAppState>();

  @override
  State<SpatialEyeApp> createState() => _SpatialEyeAppState();
}

class _SpatialEyeAppState extends State<SpatialEyeApp> {
  ThemeMode _themeMode = ThemeMode.dark;

  ThemeMode get themeMode => _themeMode;

  void toggleTheme([ThemeMode? targetMode]) {
    setState(() {
      if (targetMode != null) {
        _themeMode = targetMode;
      } else {
        _themeMode =
            _themeMode == ThemeMode.dark ? ThemeMode.light : ThemeMode.dark;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Spatial Eye',
      debugShowCheckedModeBanner: false,
      themeMode: _themeMode,
      theme: ThemeData.light().copyWith(
        scaffoldBackgroundColor: const Color(0xFFF4F6F9),
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.white,
          foregroundColor: Colors.black80,
          elevation: 0,
        ),
        colorScheme: const ColorScheme.light(
          primary: Colors.blueAccent,
          secondary: Colors.purpleAccent,
          surface: Colors.white,
        ),
      ),
      darkTheme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: Colors.black,
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.black,
          foregroundColor: Colors.white,
          elevation: 0,
        ),
        colorScheme: const ColorScheme.dark(
          primary: Colors.cyanAccent,
          secondary: Colors.purpleAccent,
          surface: Color(0xFF121212),
        ),
      ),
      home: const NavigationScreen(),
    );
  }
}
