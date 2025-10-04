package com.example.billingapps

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.billingapps.api.ApiService
import com.example.billingapps.api.RetrofitClient
import com.example.billingapps.services.BlockedAppsManager

class DeviceStatusWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "DeviceStatusWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting work to check device status from server.")

        val sharedPreferences =
            applicationContext.getSharedPreferences("BillingAppPrefs", Context.MODE_PRIVATE)
        val deviceId = sharedPreferences.getString("deviceId", null)

        if (deviceId == null) {
            Log.e(TAG, "Device ID not found in SharedPreferences. Cannot perform work.")
            return Result.failure()
        }

        try {
            val apiService = RetrofitClient.instance
            val response = apiService.getDeviceStatus(deviceId)

            if (response.isSuccessful) {
                val deviceStatusResponse = response.body()
                val isLockedFromServer = deviceStatusResponse?.data?.isLocked

                if (isLockedFromServer != null) {
                    Log.i(TAG, "API call successful. Server is_locked status: $isLockedFromServer")
                    // Simpan status dari server ke SharedPreferences
                    BlockedAppsManager.saveServerLockStatus(applicationContext, isLockedFromServer)
                    return Result.success()
                } else {
                    Log.w(TAG, "API response body or data is null.")
                    return Result.failure()
                }
            } else {
                Log.e(
                    TAG,
                    "API call failed with code: ${response.code()} and message: ${response.message()}"
                )
                return Result.retry() // Coba lagi nanti jika request gagal (misal: server down)
            }

        } catch (e: Exception) {
            Log.e(TAG, "An exception occurred during API call", e)
            return Result.retry() // Coba lagi nanti jika ada error jaringan
        }
    }
}

