// ignore_for_file: avoid_print
// ignore_for_file: unused_import
import 'dart:async';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fit_app/firebase_options.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:googleapis/customsearch/v1.dart';

final _googleSignIn = GoogleSignIn(
  scopes: [
    'email',
  ],
);
late bool _isAuth;
Future<void> main() async {
  // WidgetsFlutterBinding.ensureInitialized();
  // await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
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

  @override
  void initState() {
    super.initState();
    checkUserLoggedIn();
  }

  void checkUserLoggedIn() async {
    bool isSignedIn = await _googleSignIn.isSignedIn();
    print(isSignedIn);
    setState(() {
      _isAuth = isSignedIn;
    });
  }

  Future<void> _getHealthData() async {
    if (_isAuth) {
      await channelRequest.invokeMethod('getHealthData');
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
                  ElevatedButton(
                      child: const Text('SignOut'),
                      onPressed: () {
                        try {
                          _googleSignIn.signOut();
                          checkUserLoggedIn();
                        } catch (e) {
                          print('ошибка выхода');
                        }
                      }),
                ],
              )
            : ElevatedButton(
                child: const Text('SignIn'),
                onPressed: () async {
                  try {
                    await _googleSignIn.signIn();
                    checkUserLoggedIn();
                  } catch (error) {
                    print('ошибка входа');
                  }
                },
              ),
      ),
    );
  }
}
