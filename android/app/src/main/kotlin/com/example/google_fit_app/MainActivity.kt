package com.example.google_fit_app

import android.content.Intent
import android.net.Uri
import android.util.AndroidException
import android.util.Log
import androidx.core.content.ContextCompat.*
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import java.time.Instant
import kotlin.random.Random


data class DataPoint (val point :MutableMap<String, Any>)

class MainActivity: FlutterActivity() {
    private val TAG = "MESSAGE OF APP"
    private val CHANNEL_FIT = "flutter.fit.requests"
    // Create a set of permission strings for required data types
    val PERMISSIONS =
        setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class)
        )
    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_FIT).setMethodCallHandler { call, result ->
            if(call.method == "getHealthData") {
                getHealthData()
                result.success("message")
            } else {
                result.notImplemented()
            }
        }
    }

    private fun getHealthData() {
        val providerPackageName = "com.google.android.apps.healthdata"
        val availabilityStatus = HealthConnectClient.getSdkStatus(context, providerPackageName)
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE) {
            Log.e(TAG, "Сработала проверка с providerPackageName 1")
            return // early return as there is no viable integration
        }
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            Log.e(TAG, "Сработала проверка с providerPackageName 2")
            // Optionally redirect to package installer to find a provider, for example:
            val uriString = "market://details?id=$providerPackageName&url=healthconnect%3A%2F%2Fonboarding"
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setPackage("com.android.vending")
                    data = Uri.parse(uriString)
                    putExtra("overlay", true)
                    putExtra("callerId", context.packageName)
                }
            )
            return
        }
        val healthConnectClient = HealthConnectClient.getOrCreate(context, providerPackageName)
        GlobalScope.launch {
            Log.i(TAG, "runBlocking")
            checkPermissionsAndRun(healthConnectClient)
        }
        // Поток не приостанавливается так что надо это как то изменить
    }

    suspend fun checkPermissionsAndRun(healthConnectClient: HealthConnectClient) {
        Log.i(TAG, "checkPermissionsAndRun")
        try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            if (granted.containsAll(PERMISSIONS)) {
                Log.i(TAG, "РАЗРЕШЕНИЯ ПОЛУЧЕНЫ")

                val endTime = Instant.now()
                val startTime = endTime.minusSeconds(604800)
                readStepsByTimeRange(healthConnectClient, startTime, endTime)
            } else {
                Log.e(TAG, "ОШИБКА С РАЗРЕШЕНИЕМ")
            }
        } catch (e: AndroidException) {
            Log.e(TAG+"ERROR", "$e")
        }
    }
    suspend fun readStepsByTimeRange(
        healthConnectClient: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ) {
        Log.i(TAG, "readStepsByTimeRange")
        try {
            val response =
                healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                    )
                )
            Log.i(TAG, "Start loop")
            if (response.records.isEmpty()) writeDataSteps(healthConnectClient)
            for (stepRecord in response.records) {
                Log.i(TAG, "${stepRecord.count}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "ОШИБКА В readStepsByTimeRange $e")
        }
    }
    suspend fun writeDataSteps(healthConnectClient: HealthConnectClient) {
        try {
            val stepsRecord = StepsRecord(
                count = Random.nextLong(50, 210),
                startTime = Instant.now().minusSeconds(300),
                endTime = Instant.now(),
                startZoneOffset = null,
                endZoneOffset = null,
            )
            healthConnectClient.insertRecords(listOf(stepsRecord))
            Log.i(TAG, "Произошла запись шагов")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка записи данных: $e")
        }
    }
}
