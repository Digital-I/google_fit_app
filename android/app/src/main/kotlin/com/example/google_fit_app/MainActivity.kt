package com.example.google_fit_app

import android.content.Intent
import android.net.Uri
import android.util.AndroidException
import android.util.Log
import androidx.core.content.ContextCompat.*
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.*
import androidx.health.connect.client.time.TimeRangeFilter
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import java.time.Instant
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType


data class DataPoint (val point :MutableMap<String, Any>)

class MainActivity: FlutterActivity() {
    private val TAG = "MESSAGE OF APP"
    private val CHANNEL_FIT = "flutter.fit.requests"
    val CLASSES_PERMISSIONS: Set<KClass<out Record>> = setOf(
        ActiveCaloriesBurnedRecord::class,
        BasalMetabolicRateRecord::class,
        BodyTemperatureRecord::class,
        DistanceRecord::class,
        ElevationGainedRecord::class,
        ExerciseSessionRecord::class,
        FloorsClimbedRecord::class,
        HeartRateRecord::class,
        HeightRecord::class,
        NutritionRecord::class,
        RestingHeartRateRecord::class,
        SexualActivityRecord::class,
        SleepSessionRecord::class,
        StepsCadenceRecord::class,
        StepsRecord::class,
        TotalCaloriesBurnedRecord::class,
        WheelchairPushesRecord::class
    )


    val PERMISSIONS = CLASSES_PERMISSIONS.flatMap { permission ->
        listOf(
            HealthPermission.getReadPermission(permission),
            HealthPermission.getWritePermission(permission)
        )
    }.toSet()



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
            checkPermissionsAndRun(healthConnectClient)
        }
        // Поток не приостанавливается так что надо это как то изменить
    }

    private suspend fun checkPermissionsAndRun(healthConnectClient: HealthConnectClient) {
        try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            if (granted.containsAll(PERMISSIONS)) {
                val endTime = Instant.now()
                val startTime = endTime.minusSeconds(604800)
                aggregateSteps(healthConnectClient, startTime, endTime)
            } else {
                Log.e(TAG, "ОШИБКА С РАЗРЕШЕНИЕМ")
            }
        } catch (e: AndroidException) {
            Log.e(TAG+"ERROR", "$e")
        }
    }

    private suspend fun aggregateSteps(
        healthConnectClient: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ) {
        try {

//
//            for (classPermission in CLASSES_PERMISSIONS) {
//                val companion = classPermission.companionObject
//                val companionInstance = companion?.objectInstance
//                if (companionInstance != null) {
//                    val properties = companionInstance::class.memberProperties
//                    val aggregateMetricProperty = properties.firstOrNull()
//
//                    if (aggregateMetricProperty != null) {
//                        val aggregateMetricValue =
//                            aggregateMetricProperty.getter.call(companionInstance)
//                            Log.i(TAG, "#$classPermission, $aggregateMetricValue $companionInstance")
//                        if (aggregateMetricValue != null && aggregateMetricValue is AggregateMetric<*>) {
//                            setet.add(aggregateMetricValue)
//                        }
//                    }
//                }
//            }

            val setet = mutableSetOf<Any>()
            for (classPermission in CLASSES_PERMISSIONS) {
                val companion = classPermission.companionObject
                val companionInstance = companion?.objectInstance
                if (companionInstance != null) {
                    val properties = companionInstance::class.memberProperties

                    for (property in properties) {
                        if (property.returnType.isSubtypeOf(AggregateMetric::class.starProjectedType)) {
                            val aggregateMetricValue = property.getter.call(companionInstance)
                            if (aggregateMetricValue != null && aggregateMetricValue is AggregateMetric<*>) {
                                setet.add(aggregateMetricValue)
                            }
                        }
                    }
                }
            }
            //
            val newset: Set<AggregateMetric<*>> = setet as Set<AggregateMetric<*>>
                val response = healthConnectClient.aggregate(
                    AggregateRequest(
                        // какое же это говно
                        metrics = newset,
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                    )
                )
            for (type in newset) {
                val stepCount = response[type] ?: "NO DATA"
                Log.i(TAG, "$stepCount")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Function aggregateSteps $e")
        }
    }

    // Если шагов нет то пишем ее в сервис
    private suspend fun writeDataSteps(healthConnectClient: HealthConnectClient) {
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
    // Функция для чтения только шагов
    private suspend fun readStepsByTimeRange(
        healthConnectClient: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ) {
        try {
            val response =
                healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                    )
                )
            if (response.records.isEmpty()) writeDataSteps(healthConnectClient)
            for (stepRecord in response.records) {
                Log.i(TAG, "${stepRecord.count}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "ОШИБКА В readStepsByTimeRange $e")
        }
    }
}
