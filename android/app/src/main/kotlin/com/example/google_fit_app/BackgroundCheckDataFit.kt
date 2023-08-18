package com.example.google_fit_app

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import io.flutter.plugin.common.MethodChannel
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private const val TAG = "ФОНОВО_ИНТЕРВАЛЬНЫЙ ЗАПРОС ДАННЫХ"
private var lastRequestTime: LocalDateTime = LocalDateTime.now().minusMinutes(2)
class BackgroundCheckDataFit(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        try {
            Log.i(TAG, "TIME $lastRequestTime ")

            val context = applicationContext
            val nowTime = LocalDateTime.now()

            checkPermission(context)

            val startTimeRequest = lastRequestTime.atZone(ZoneId.systemDefault()).toEpochSecond()
            val endTimeRequest = nowTime.atZone(ZoneId.systemDefault()).toEpochSecond()

            Fitness.getHistoryClient(context, GoogleSignIn.getAccountForExtension(context, fitnessOptions))
                .readData(readRequest
                        .setTimeRange(startTimeRequest, endTimeRequest, TimeUnit.SECONDS)
                        .build())
                .addOnSuccessListener { response ->
                    val json = getJson(response)
                    Log.i(TAG, json)
                    MethodChannel(flutterEngineGlobal.dartExecutor.binaryMessenger, CHANNEL_FIT).invokeMethod("sendDataToFlutter", json)
                    lastRequestTime = nowTime
                }
                .addOnFailureListener {
                    e -> Log.e(TAG, "There was a problem getting steps.", e)
                    Result.failure()
                }
                .addOnFailureListener {
                    e -> Log.e(TAG, "OnFailure()", e)
                    Result.failure()
                }
            return Result.success()
        } catch (e: InterruptedException) {
            e.printStackTrace()
            return Result.failure()
        }
    }
}
