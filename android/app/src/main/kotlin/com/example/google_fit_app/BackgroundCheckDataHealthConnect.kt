package com.example.google_fit_app

import android.content.Context
import android.os.RemoteException
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking

private const val TAG = "ФОНОВО-ИНТЕРВАЛЬНЫЙ ЗАПРОС ДАННЫХ"
class BackgroundCheckDataHealthConnect(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    //val channal = MethodChannel(flutterEngineGlobal.dartExecutor.binaryMessenger, CHANNEL_FIT)
    override fun doWork(): Result {
        return try {
            Log.i(TAG, "START BACKGROUND WORKER")
            runBlocking {
                for (i in zalypa.indices) {
                    val el = zalypa[i]
                    zalypa[i] = processChanges(el)
                }
            }
            Result.success()
        } catch (e: RemoteException) {
            Log.e(TAG, "ERROR FROM doWork()")
            e.printStackTrace()
            Result.failure()
        }
    }
    private suspend fun processChanges(token: String): String {
        var nextChangesToken = token
        do {
            val response = HealthConnectClient.getOrCreate(applicationContext, providerPackageName).getChanges(nextChangesToken)
            //Log.i(TAG, response.changes.size.toString())
            response.changes.forEach { change ->
                when (change) {
                    is UpsertionChange -> {
                        Log.i(TAG, "UPDATE DATE")
                        // channal.invokeMethod("sendDataToFlutter", change.record.metadata.id)
                    }
                    is DeletionChange -> Log.i(TAG, "DELETE DATA")
                    else -> Log.i(TAG, "NO CHANGE DATA")
                }
            }
            nextChangesToken = response.nextChangesToken
        } while (response.hasMore)
        return nextChangesToken
    }
}
