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
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType

val zalypa = mutableListOf<String>()
lateinit var flutterEngineGlobal: FlutterEngine
private const val TAG = "MESSAGE OF APP"
const val CHANNEL_FIT = "flutter.fit.requests"
const val providerPackageName = "com.google.android.apps.healthdata"
private val classesPermissions: Set<KClass<out Record>> = setOf(
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
private val permissions = classesPermissions.flatMap { permission ->
    listOf(
        HealthPermission.getReadPermission(permission),
        HealthPermission.getWritePermission(permission)
    )
}.toSet()

@OptIn(DelicateCoroutinesApi::class)
class MainActivity: FlutterActivity() {

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        flutterEngineGlobal = flutterEngine
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_FIT).setMethodCallHandler { call, result ->
            when(call.method){
                "getHealthData" -> {
                    GlobalScope.launch {
                        result.success(getHealthData())
                    }
                }
                "generateData" -> {
                    GlobalScope.launch {
                        writeDataSteps()
                    }
                    result.success("")
                }
                "start" -> {
                    GlobalScope.launch {
                        try {
                            for (classPermission in classesPermissions) {
                                zalypa.add(HealthConnectClient.getOrCreate(applicationContext, providerPackageName)
                                    .getChangesToken(
                                        ChangesTokenRequest(setOf(classPermission))
                                    )
                                )
                            }
                            val periodicTimeWorkRequest = PeriodicWorkRequestBuilder<BackgroundCheckDataHealthConnect>(1, TimeUnit.MINUTES)
                                .build()
                            WorkManager.getInstance(context).enqueue(periodicTimeWorkRequest)
                        } catch (e: Exception) {
                            Log.i(TAG, "$e")
                        }
                    }
                    result.success("")
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun getHealthData(): String = runBlocking{
        val availabilityStatus = HealthConnectClient.getSdkStatus(context, providerPackageName)
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE) {
            Log.e(TAG, "Сработала проверка с providerPackageName 1")
            return@runBlocking "SDK_UNAVAILABLE"
        }
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            Log.e(TAG, "Сработала проверка с providerPackageName 2")
            val uriString = "market://details?id=$providerPackageName&url=healthconnect%3A%2F%2Fonboarding"
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setPackage("com.android.vending")
                    data = Uri.parse(uriString)
                    putExtra("overlay", true)
                    putExtra("callerId", context.packageName)
                }
            )
            return@runBlocking "SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED"
        }
        return@runBlocking async {
            if (checkPermissionsAndRun()) {
                val endTime = Instant.now()
                val startTime = endTime.minusSeconds(604800)
                val list = aggregateSteps(startTime, endTime)
                return@async list.first()
            } else return@async "NO WORK"
        }.await()
        // Поток не приостанавливается так что надо это как то изменить
    }
    private suspend fun checkPermissionsAndRun(healthConnectClient: HealthConnectClient = HealthConnectClient.getOrCreate(context, providerPackageName)): Boolean {
        try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            if (!granted.containsAll(permissions)) {
                return false
            }
        } catch (e: AndroidException) {
            Log.e(TAG+"ERROR", "$e")
        }
        return true
    }
    private suspend fun aggregateSteps(
        startTime: Instant,
        endTime: Instant,
        healthConnectClient: HealthConnectClient = HealthConnectClient.getOrCreate(context, providerPackageName)
    ): List<String> {
        val listOfValue = mutableListOf<String>()
        try {
            val presetClassPermission = mutableSetOf<Any>()
            for (classPermission in classesPermissions) {
                val companion = classPermission.companionObject
                val companionInstance = companion?.objectInstance
                if (companionInstance != null) {
                    val properties = companionInstance::class.memberProperties
                    for (property in properties) {
                        if (property.returnType.isSubtypeOf(AggregateMetric::class.starProjectedType)) {
                            val aggregateMetricValue = property.getter.call(companionInstance)
                            if (aggregateMetricValue != null && aggregateMetricValue is AggregateMetric<*>) {
                                presetClassPermission.add(aggregateMetricValue)
                            }
                        }
                    }
                }
            }
            val setClassPermission: Set<AggregateMetric<*>> = presetClassPermission as Set<AggregateMetric<*>>
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setClassPermission,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            for (type in setClassPermission) {
                val step = response[type]
                if (step != null) listOfValue.add(step.toString())
            }
            return listOfValue
        } catch (e: Exception) {
            Log.e(TAG, "Function aggregateSteps $e")
        }
        return emptyList()
    }
    private suspend fun writeDataSteps() {
        try {
            val startTime = Instant.now().minusSeconds(300)
            val endTime = Instant.now()
            val stepsRecord = StepsRecord(
                count = Random.nextLong(50, 210),
                startTime = startTime,
                endTime = endTime,
                startZoneOffset = null,
                endZoneOffset = null,
            )
            HealthConnectClient.getOrCreate(applicationContext, providerPackageName).insertRecords(listOf(stepsRecord))
            Log.i(TAG, "Произошла запись шагов start= $startTime end= $endTime")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка записи данных: $e")
        }
    }
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
            if (response.records.isEmpty()) writeDataSteps()
            for (stepRecord in response.records) {
                Log.i(TAG, "${stepRecord.count}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "ОШИБКА В readStepsByTimeRange $e")
        }
    }
}
