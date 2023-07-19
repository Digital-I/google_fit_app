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

    private fun accessGoogleFit() {
        val end = LocalDateTime.now()
        val start = end.minusWeeks(1)
        val endSeconds = end.atZone(ZoneId.systemDefault()).toEpochSecond()
        val startSeconds = start.atZone(ZoneId.systemDefault()).toEpochSecond()
    
        val readRequest = DataReadRequest.Builder()
            .read(DataType.TYPE_HEART_RATE_BPM)
            .read(DataType.TYPE_SLEEP_SEGMENT)
            .read(DataType.TYPE_STEP_COUNT_DELTA)
            .read(DataType.TYPE_WEIGHT)
            .read(DataType.TYPE_NUTRITION)
            .setTimeRange(startSeconds, endSeconds, TimeUnit.SECONDS)
            .build()

        val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)
        Fitness.getHistoryClient(this, account)
            .readData(readRequest)
            .addOnSuccessListener({ response ->
                for (dataSet in response.dataSets) {
                    val dataType = dataSet.dataType
                    val dataTypeName = dataType.name
        
                    Log.i(TAG, "Data Type Name: $dataTypeName")
        
                    for (dataPoint in dataSet.dataPoints) {
                        val fields = dataPoint.dataType.fields
                        for (field in fields) {
                            val value = dataPoint.getValue(field)
                            Log.i(TAG, "${field.name}: $value")
                        }
                    }
                }
            })
            .addOnFailureListener({ e -> Log.d(TAG, "OnFailure()", e) })
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_FIT).setMethodCallHandler { call, result ->
            if(call.method == "getHealthData") {
                getHealthData()
                result.success("message from method")
            }
        }
    }

    private fun getHealthData() {
        Log.i(TAG, "Start get_health_data")
        val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)
        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                this, // your activity
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE, // e.g. 1
                account,
                fitnessOptions)
        } else {
            accessGoogleFit()
        }
    }

}
