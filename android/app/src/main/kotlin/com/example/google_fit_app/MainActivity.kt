package com.example.google_fit_app

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.*
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.GoogleApiActivity
import android.os.Bundle
import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant
import java.util.concurrent.TimeUnit
import androidx.annotation.NonNull
import com.google.android.gms.fitness.result.DataReadResponse
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.MethodCall
import com.google.android.gms.tasks.OnSuccessListener
import android.os.Handler
import com.google.android.gms.tasks.Tasks


class MainActivity: FlutterActivity() {
    private val TAG = "MESSAGE OF APP"
    private val CHANNEL_FIT = "flutter.fit.requests";
    private val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1
    private val fitnessOptions = FitnessOptions.builder()
        .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_WEIGHT, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_NUTRITION, FitnessOptions.ACCESS_READ)
        .build()

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_FIT).setMethodCallHandler { call, result ->
            if(call.method == "getHealthData") {
                val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)
                if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                    GoogleSignIn.requestPermissions(
                        this,
                        GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                        account,
                        fitnessOptions)
                } else {
                    val end = LocalDateTime.now()
                    val start = end.minusWeeks(1)
                    val endSeconds = end.atZone(ZoneId.systemDefault()).toEpochSecond()
                    val startSeconds = start.atZone(ZoneId.systemDefault()).toEpochSecond()
                
                    val readRequest = DataReadRequest.Builder()
                        .read(DataType.TYPE_STEP_COUNT_DELTA)
                        .bucketByTime(1, TimeUnit.DAYS)
                        .setTimeRange(startSeconds, endSeconds, TimeUnit.SECONDS)
                        .build()

                    val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)
                    Fitness.getHistoryClient(context.applicationContext, account)
                        .readData(readRequest)
                        .addOnSuccessListener({ response ->
                            result.success(response.dataSets.size.toString())
                            // тут расписываем полученные данные, но, так как dataSet постоянно пустой, просто кидаем длину списка
                            for (dataSet in response.dataSets) {
                                val dataType = dataSet.dataType
                                val dataTypeName = dataType.name
                    
                                Log.i(TAG, "Data Type Name: $dataTypeName")
                                for (dataPoint in dataSet.dataPoints) {
                                    Log.i(TAG, dataPoint.dataType.name)
                                    val fields = dataPoint.dataType.fields
                                    for (field in fields) {
                                        val value = dataPoint.getValue(field).asString()
                                        Log.i(TAG, "${field.name}: $value")
                                    }
                                }
                            }
                        })
                        .addOnFailureListener({ e -> Log.d(TAG, "OnFailure()", e) })
                }
            } else {
                result.notImplemented()
            }
        }
    }
}
