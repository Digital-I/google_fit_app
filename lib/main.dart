import 'package:flutter/material.dart';
import 'package:google_sign_in/google_sign_in.dart';

final _googleSignIn = GoogleSignIn(
  scopes: [
    'https://www.googleapis.com/auth/drive',
  ],
);

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Google Fit App',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: const MyHomePage(),
    );
  }
}

class MyHomePage extends StatelessWidget {
  const MyHomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Google Fit App'),
      ),
      body: Center(
        child: ElevatedButton(
          child: const Text('Sign In with Google'),
          onPressed: () {
            _signInWithGoogle();
          },
        ),
      ),
    );
  }

  Future<void> _signInWithGoogle() async {
    try {
      await _googleSignIn.signIn();
    } catch (error) {
      print('Ошибка аутентификации: $error');
    }
  }
}
