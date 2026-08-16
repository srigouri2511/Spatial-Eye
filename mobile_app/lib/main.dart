import 'package:flutter/material.dart';
import 'ui/screens/navigation_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const SpatialEyeApp());
}

class SpatialEyeApp extends StatelessWidget {
  const SpatialEyeApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Spatial Eye',
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: Colors.black,
        colorScheme: const ColorScheme.dark(
          primary: Colors.cyanAccent,
          secondary: Colors.purpleAccent,
        ),
      ),
      home: const NavigationScreen(),
    );
  }
}
