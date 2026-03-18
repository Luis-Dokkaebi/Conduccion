package com.dms.app

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TelemetrySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "TelemetrySyncWorker"
        // In a real app, this would be your production backend URL
        private const val BASE_URL = "http://10.0.2.2:8000"
        const val ACTION_CLEARANCE_UPDATE = "com.dms.app.ACTION_CLEARANCE_UPDATE"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting telemetry sync...")
        val database = AppDatabase.getDatabase(applicationContext)
        val eventDao = database.microSleepEventDao()

        val unsyncedEvents = eventDao.getAllEvents()

        if (unsyncedEvents.isEmpty()) {
            Log.i(TAG, "No events to sync. Work finished.")
            return Result.success()
        }

        Log.i(TAG, "Attempting to sync ${unsyncedEvents.size} events.")

        return try {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val dmsApi = retrofit.create(DmsApi::class.java)

            val response = dmsApi.syncEvents(unsyncedEvents)

            if (response.isSuccessful) {
                // Task 6.2.2: Si responde 200 OK, marcar como "Sync" y purgar métricas antiguas
                val idsToDelete = unsyncedEvents.map { it.id }
                eventDao.deleteEvents(idsToDelete)
                Log.i(TAG, "Successfully synced and purged ${idsToDelete.size} events.")

                // Task 8.2: Consultar estado del conductor (Clearance) después de sincronizar
                val driverId = "driver_123" // Same default used in MicroSleepEvent
                val clearanceResponse = dmsApi.getClearance(driverId)

                if (clearanceResponse.isSuccessful && clearanceResponse.body() != null) {
                    val clearance = clearanceResponse.body()!!
                    Log.i(TAG, "Received clearance status: ${clearance.status} (FRS: ${clearance.frs_score})")

                    // Notificar a MainActivity usando LocalBroadcastManager
                    val intent = Intent(ACTION_CLEARANCE_UPDATE).apply {
                        putExtra("status", clearance.status)
                        putExtra("frs_score", clearance.frs_score)
                        putExtra("message", clearance.message)
                        putExtra("mandatory_rest_minutes", clearance.mandatory_rest_minutes)
                    }
                    LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
                } else {
                    Log.w(TAG, "Failed to get clearance status: ${clearanceResponse.code()}")
                }

                Result.success()
            } else {
                Log.e(TAG, "Sync failed with server error: ${response.code()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed with exception", e)
            Result.retry()
        }
    }
}
