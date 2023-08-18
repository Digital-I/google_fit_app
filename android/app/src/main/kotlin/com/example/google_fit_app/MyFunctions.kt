package com.example.google_fit_app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.data.HealthDataTypes
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.result.DataReadResponse
import com.google.gson.Gson
import io.flutter.embedding.engine.FlutterEngine

lateinit var flutterEngineGlobal: FlutterEngine
data class DataPoint (val point :MutableMap<String, Any>)
private const val TAG = "FitDataOperation"
private const val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1
val fitnessOptions = FitnessOptions.builder()
    .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA)
    .addDataType(HealthDataTypes.TYPE_BLOOD_GLUCOSE)
    .addDataType(HealthDataTypes.TYPE_BLOOD_PRESSURE)
    // нет пульса
    .addDataType(DataType.TYPE_SLEEP_SEGMENT)
    .addDataType(HealthDataTypes.TYPE_OXYGEN_SATURATION)
    .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
    .addDataType(HealthDataTypes.TYPE_BODY_TEMPERATURE)
    .addDataType(DataType.TYPE_WEIGHT)
    .addDataType(DataType.TYPE_NUTRITION)
    .addDataType(DataType.TYPE_HYDRATION)
    .addDataType(HealthDataTypes.TYPE_CERVICAL_MUCUS)
    .addDataType(HealthDataTypes.TYPE_CERVICAL_POSITION)
    .addDataType(HealthDataTypes.TYPE_MENSTRUATION)
    .addDataType(HealthDataTypes.TYPE_OVULATION_TEST)
    .addDataType(HealthDataTypes.TYPE_VAGINAL_SPOTTING)
    .build()
val readRequest = DataReadRequest.Builder()
    .read(DataType.AGGREGATE_STEP_COUNT_DELTA)
    .read(HealthDataTypes.TYPE_BLOOD_GLUCOSE)
    .read(HealthDataTypes.TYPE_BLOOD_PRESSURE)
    // нет пульса
    .read(DataType.TYPE_SLEEP_SEGMENT)
    .read(HealthDataTypes.TYPE_OXYGEN_SATURATION)
    .read(DataType.TYPE_STEP_COUNT_DELTA)
    .read(HealthDataTypes.TYPE_BODY_TEMPERATURE)
    .read(DataType.TYPE_WEIGHT)
    .read(DataType.TYPE_NUTRITION)
    .read(DataType.TYPE_HYDRATION)
    .read(HealthDataTypes.TYPE_CERVICAL_MUCUS)
    .read(HealthDataTypes.TYPE_CERVICAL_POSITION)
    .read(HealthDataTypes.TYPE_MENSTRUATION)
    .read(HealthDataTypes.TYPE_OVULATION_TEST)
    .read(HealthDataTypes.TYPE_VAGINAL_SPOTTING)
@SuppressLint("InlinedApi")
fun checkPermission(context: Context) {
    val account = GoogleSignIn.getAccountForExtension(context, fitnessOptions)
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BODY_SENSORS
        ) != PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) != PackageManager.PERMISSION_GRANTED)
    {
        ActivityCompat.requestPermissions(
            context as Activity,
            arrayOf(
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACTIVITY_RECOGNITION
            ),
            GOOGLE_FIT_PERMISSIONS_REQUEST_CODE
        )
    }
    if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
        GoogleSignIn.requestPermissions(
            context as Activity,
            GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
            account,
            fitnessOptions)
    }
}

fun getJson(response: DataReadResponse): String {
    val data: MutableMap<String, MutableList<DataPoint>> = mutableMapOf()
    for (dataSet in response.dataSets) {
        val set = mutableListOf<DataPoint>()

        if (dataSet.dataPoints.isEmpty()) continue
        when (dataSet.dataType) {
            DataType.TYPE_STEP_COUNT_DELTA, HealthDataTypes.TYPE_VAGINAL_SPOTTING -> {
                val point = DataPoint(mutableMapOf())
                var count = 0.0
                var value: Double
                var nameType = "no data"
                for (dataPoint in dataSet.dataPoints) {
                    for (field in dataPoint.dataType.fields) {
                        value =
                            if (field.format == 1) dataPoint.getValue(field).asInt()
                                .toDouble()
                            else dataPoint.getValue(field).asFloat().toDouble()
                        count += value
                        nameType = field.name
                    }
                }
                point.point[nameType] = count
                set.add(point)
            }

            HealthDataTypes.TYPE_BLOOD_GLUCOSE,
            HealthDataTypes.TYPE_BLOOD_PRESSURE,
            DataType.TYPE_HEART_RATE_BPM,
            HealthDataTypes.TYPE_OXYGEN_SATURATION,
            DataType.TYPE_SLEEP_SEGMENT,
            HealthDataTypes.TYPE_BODY_TEMPERATURE,
            DataType.TYPE_WEIGHT,
            HealthDataTypes.TYPE_CERVICAL_MUCUS,
            HealthDataTypes.TYPE_CERVICAL_POSITION,
            HealthDataTypes.TYPE_MENSTRUATION,
            HealthDataTypes.TYPE_OVULATION_TEST -> {
                val point = DataPoint(mutableMapOf())
                for (field in dataSet.dataType.fields) {
                    point.point[field.name] = dataSet.dataPoints.last().getValue(field)
                }
                set.add(point)
            }

            DataType.TYPE_NUTRITION, DataType.TYPE_HYDRATION -> {
                for (dataPoint in dataSet.dataPoints) {
                    val point = DataPoint(mutableMapOf())
                    val map = mutableMapOf<String, Any>()
                    map[Field.FIELD_VOLUME.name] =
                        dataPoint.getValue(Field.FIELD_VOLUME).asFloat()
                    map[Field.FIELD_MEAL_TYPE.name] =
                        dataPoint.getValue(Field.FIELD_MEAL_TYPE).asInt()
                    map[Field.FIELD_FOOD_ITEM.name] =
                        dataPoint.getValue(Field.FIELD_FOOD_ITEM).asString()
                    point.point["nutrition"] = map
                    set.add(point)
                }
            }
        }
        data[dataSet.dataType.name] = set
    }
    return Gson().toJson(data)
}