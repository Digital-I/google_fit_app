import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_sign_in/google_sign_in.dart';

final _googleSignIn = GoogleSignIn(
  scopes: [
    'email',
    'https://www.googleapis.com/auth/contacts.readonly',
    'https://www.googleapis.com/auth/fitness.activity.read',
    'https://www.googleapis.com/auth/fitness.blood_glucose.read',
    'https://www.googleapis.com/auth/fitness.blood_pressure.read',
    'https://www.googleapis.com/auth/fitness.body.read',
    'https://www.googleapis.com/auth/fitness.body_temperature.read',
    'https://www.googleapis.com/auth/fitness.heart_rate.read',
    'https://www.googleapis.com/auth/fitness.nutrition.read',
    'https://www.googleapis.com/auth/fitness.oxygen_saturation.read',
  ],
);
late bool _isAuth;
Future<void> main() async {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});
  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      title: 'My App',
      home: MyHomePage(),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key});
  @override
  State<MyHomePage> createState() => _MyHomePage();
}

class _MyHomePage extends State<MyHomePage> {
  static const channelRequest = MethodChannel('flutter.fit.requests');
  bool _isAuth = false;
  var text = 'пока пусто';

  @override
  void initState() {
    super.initState();
    checkUserLoggedIn();
  }

  void checkUserLoggedIn() async {
    bool isSignedIn = await _googleSignIn.isSignedIn();
    setState(() {
      _isAuth = isSignedIn;
    });
  }

  Future<void> _getHealthData() async {
    if (_isAuth) {
      var t = await channelRequest.invokeMethod('getHealthData');
      setState(() {
        text = t;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      child: Center(
        child: _isAuth
            ? Column(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  ElevatedButton(
                    onPressed: _getHealthData,
                    child: const Text('Get health data'),
                  ),
                  Text(text),
                  ElevatedButton(
                      child: const Text('SignOut'),
                      onPressed: () {
                        _googleSignIn.signOut();
                        checkUserLoggedIn();
                      }),
                ],
              )
            : ElevatedButton(
                child: const Text('SignIn'),
                onPressed: () async {
                  await _googleSignIn.signIn();
                  checkUserLoggedIn();
                },
              ),
      ),
    );
  }
}
