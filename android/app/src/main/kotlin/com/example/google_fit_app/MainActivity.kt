package com.example.google_fit_app

import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataType.*
import com.google.android.gms.fitness.data.HealthDataTypes.*
import com.google.gson.Gson
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private const val TAG = "MAIN ACTIVITY"
const val CHANNEL_FIT = "flutter.fit.requests"
@Suppress("DEPRECATION")
class MainActivity: FlutterActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dateFormat = DateFormat.getDateFormat(
            applicationContext
        )
        checkPermission(this)
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        flutterEngineGlobal = flutterEngine
        Log.i("FLUTTER CONFIG", "START ${LocalDateTime.now()}")
        val _channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_FIT)
        _channel.setMethodCallHandler { call, result ->
            if(call.method == "getHealthData") {
                getHealthData(result)
            } else {
                result.notImplemented()
            }
        }

        val oneTimeWorkRequest = PeriodicWorkRequestBuilder<BackgroundCheckDataFit>(1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(applicationContext).enqueue(oneTimeWorkRequest)
    }

    private fun getHealthData(result: MethodChannel.Result) {

        checkPermission(applicationContext)

        val end = LocalDateTime.now()
        val start = end.minusWeeks(1)
        val endSeconds = end.atZone(ZoneId.systemDefault()).toEpochSecond()
        val startSeconds = start.atZone(ZoneId.systemDefault()).toEpochSecond()

        Fitness.getHistoryClient(this, GoogleSignIn.getAccountForExtension(this, fitnessOptions))
            .readData(readRequest
                .setTimeRange(startSeconds, endSeconds, TimeUnit.SECONDS)
                .build())
            .addOnSuccessListener { response ->
                val json = getJson(response)
                Log.i(TAG, Gson().fromJson(json, MutableMap::class.java).toString())
                result.success(json)
            }
            .addOnFailureListener { e -> Log.e(TAG, "There was a problem getting steps.", e) }
            .addOnFailureListener { e -> Log.e(TAG, "OnFailure()", e) }
    }
}
