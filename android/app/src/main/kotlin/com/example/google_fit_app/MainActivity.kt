package com.example.google_fit_app

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.DataType.TYPE_HYDRATION
import com.google.android.gms.fitness.data.DataType.TYPE_NUTRITION
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.data.HealthDataTypes.TYPE_BLOOD_GLUCOSE
import com.google.android.gms.fitness.data.HealthDataTypes.TYPE_BLOOD_PRESSURE
import com.google.android.gms.fitness.data.HealthDataTypes.TYPE_BODY_TEMPERATURE
import com.google.android.gms.fitness.data.HealthDataTypes.TYPE_CERVICAL_MUCUS
import com.google.android.gms.fitness.data.HealthDataTypes.TYPE_CERVICAL_POSITION
import com.google.android.gms.fitness.data.HealthDataTypes.TYPE_MENSTRUATION
import com.google.android.gms.fitness.data.HealthDataTypes.TYPE_OVULATION_TEST
import com.google.android.gms.fitness.data.HealthDataTypes.TYPE_OXYGEN_SATURATION
import com.google.android.gms.fitness.data.HealthDataTypes.TYPE_VAGINAL_SPOTTING
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.gson.Gson
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit


data class DataPoint (val point :MutableMap<String, Any>)

class MainActivity: FlutterActivity() {
    private val TAG = "MESSAGE OF APP"
    private val CHANNEL_FIT = "flutter.fit.requests"
    private val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1
    private val fitnessOptions = FitnessOptions.builder()
        .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA)
        .addDataType(TYPE_BLOOD_GLUCOSE)
        .addDataType(TYPE_BLOOD_PRESSURE)
        // нет пульса
        .addDataType(DataType.TYPE_SLEEP_SEGMENT)
        .addDataType(TYPE_OXYGEN_SATURATION)
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
        .addDataType(TYPE_BODY_TEMPERATURE)
        .addDataType(DataType.TYPE_WEIGHT)
        .addDataType(TYPE_NUTRITION)
        .addDataType(TYPE_HYDRATION)
        .addDataType(TYPE_CERVICAL_MUCUS)
        .addDataType(TYPE_CERVICAL_POSITION)
        .addDataType(TYPE_MENSTRUATION)
        .addDataType(TYPE_OVULATION_TEST)
        .addDataType(TYPE_VAGINAL_SPOTTING)
        .build()

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_FIT).setMethodCallHandler { call, result ->
            val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)
            if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                GoogleSignIn.requestPermissions(
                    this,
                    GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                    account,
                    fitnessOptions)
            }
            if(call.method == "getHealthData" && GoogleSignIn.hasPermissions(account, fitnessOptions)) { // потом исправлю
                if (checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
                {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.BODY_SENSORS,
                            Manifest.permission.ACTIVITY_RECOGNITION
                        ),
                        GOOGLE_FIT_PERMISSIONS_REQUEST_CODE
                    )
                }
                    
                val end = LocalDateTime.now()
                val start = end.minusWeeks(1)
                val endSeconds = end.atZone(ZoneId.systemDefault()).toEpochSecond()
                val startSeconds = start.atZone(ZoneId.systemDefault()).toEpochSecond()

                val readRequest = DataReadRequest.Builder()
                    .read(DataType.AGGREGATE_STEP_COUNT_DELTA)
                    .read(TYPE_BLOOD_GLUCOSE)
                    .read(TYPE_BLOOD_PRESSURE)
                    // нет пульса
                    .read(DataType.TYPE_SLEEP_SEGMENT)
                    .read(TYPE_OXYGEN_SATURATION)
                    .read(DataType.TYPE_STEP_COUNT_DELTA)
                    .read(TYPE_BODY_TEMPERATURE)
                    .read(DataType.TYPE_WEIGHT)
                    .read(TYPE_NUTRITION)
                    .read(TYPE_HYDRATION)
                    .read(TYPE_CERVICAL_MUCUS)
                    .read(TYPE_CERVICAL_POSITION)
                    .read(TYPE_MENSTRUATION)
                    .read(TYPE_OVULATION_TEST)
                    .read(TYPE_VAGINAL_SPOTTING)
                    .setTimeRange(startSeconds, endSeconds, TimeUnit.SECONDS)
                    .build()

                Fitness.getHistoryClient(this, GoogleSignIn.getAccountForExtension(this, fitnessOptions))
                    .readData(readRequest)
                    .addOnSuccessListener { response ->
                        val data: MutableMap<String, MutableList<DataPoint>> = mutableMapOf()
                        for (dataSet in response.dataSets){
                            val set = mutableListOf<DataPoint>()

                            if (dataSet.dataPoints.isEmpty()) continue
                            when (dataSet.dataType) {
                                DataType.TYPE_STEP_COUNT_DELTA, TYPE_VAGINAL_SPOTTING -> {
                                    val point = DataPoint(mutableMapOf())
                                    var count = 0.0
                                    var value: Double
                                    var nameType = "no data"
                                    for (dataPoint in dataSet.dataPoints) {
                                        for (field in dataPoint.dataType.fields) {
                                            value =
                                                if (field.format == 1) dataPoint.getValue(field).asInt().toDouble()
                                                else dataPoint.getValue(field).asFloat().toDouble()
                                            count += value
                                            nameType = field.name
                                        }
                                    }
                                    point.point[nameType] = count
                                    set.add(point)
                                }

                                TYPE_BLOOD_GLUCOSE,
                                TYPE_BLOOD_PRESSURE,
                                DataType.TYPE_HEART_RATE_BPM,
                                TYPE_OXYGEN_SATURATION,
                                DataType.TYPE_SLEEP_SEGMENT,
                                TYPE_BODY_TEMPERATURE,
                                DataType.TYPE_WEIGHT,
                                TYPE_CERVICAL_MUCUS,
                                TYPE_CERVICAL_POSITION,
                                TYPE_MENSTRUATION,
                                TYPE_OVULATION_TEST-> {
                                    val point = DataPoint(mutableMapOf())
                                    for (field in dataSet.dataType.fields){
                                        point.point[field.name] = dataSet.dataPoints.last().getValue(field)
                                    }
                                    set.add(point)
                                }

                                TYPE_NUTRITION, TYPE_HYDRATION -> {
                                    for (dataPoint in dataSet.dataPoints) {
                                        val point = DataPoint(mutableMapOf())
                                        val map = mutableMapOf<String, Any>()
                                        lateinit var answer: Any
                                        for (field in dataPoint.dataType.fields) {
                                            val value = dataPoint.getValue(field)
                                            when(field){
                                                Field.FIELD_VOLUME -> answer = value.asFloat()
                                                Field.FIELD_MEAL_TYPE -> answer = value.asInt()
                                                Field.FIELD_FOOD_ITEM -> answer = value.asString()
                                            }
                                            map[field.name] = answer
                                        }
                                        point.point["nutrition"] = map
                                        set.add(point)
                                    }
                                }
                            }
                            data[dataSet.dataType.name] = set
                        }
                        try {
                            val json = Gson().toJson(data)
                            Log.i(TAG, Gson().fromJson(json, MutableMap::class.java).toString())
                        } catch (e:Exception) {
                            Log.e("Error что-то там", e.toString())
                        }
                    }
                    .addOnFailureListener { e -> Log.e(TAG, "There was a problem getting steps.", e) }
                    .addOnFailureListener { e -> Log.e(TAG, "OnFailure()", e) }
            } else {
                result.notImplemented()
            }
        }
    }
}
