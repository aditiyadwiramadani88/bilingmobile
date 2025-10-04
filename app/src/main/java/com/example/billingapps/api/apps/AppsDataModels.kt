package com.example.billingapps.api.apps

import android.R
import com.google.gson.annotations.SerializedName

/**
 * Berisi semua model data (request & response) yang telah disesuaikan dengan API baru.
 */

// --- Response Models ---

data class AppInfoResponse(
    @SerializedName("id")
    val id: Int,
    @SerializedName("device_id")
    val deviceId: Int, // Sesuai contoh response, tipe data adalah integer
    @SerializedName("app_name")
    val appName: String,
    @SerializedName("logo")
    val logo: String?,
    @SerializedName("package_name")
    val packageName: String,
    @SerializedName("status_block")
    val statusBlock: String,
    @SerializedName("is_whitelist")
    val isWhitelist: Int, // Sesuai contoh response, tipe data adalah integer (1 atau 0)
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String
)

data class BulkInsertResponseItem(
    @SerializedName("app_name")
    val appName: String,
    @SerializedName("package_name")
    val packageName: String,
    @SerializedName("status_block")
    val statusBlock: String,
    @SerializedName("is_whitelist")
    val isWhitelist: Boolean,
    @SerializedName("device_id")
    val deviceId: Int,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("id")
    val id: Int
)

data class BulkInsertSuccessResponse(
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: List<BulkInsertResponseItem>
)

data class BulkUpdateResponseItem(
    @SerializedName("package_name")
    val packageName: String,
    @SerializedName("status_block")
    val statusBlock: String,
    @SerializedName("is_whitelist")
    val isWhitelist: Boolean? = null // Dibuat nullable karena tidak ada di semua contoh
)

data class BulkUpdateSuccessResponse(
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: List<BulkUpdateResponseItem>
)


// --- Request Models ---

data class BulkInsertAppItem(
    @SerializedName("app_name")
    val appName: String,
    @SerializedName("package_name")
    val packageName: String,
    @SerializedName("status_block")
    val statusBlock: String,
    @SerializedName("is_whitelist")
    val isWhitelist: Boolean
)

data class BulkInsertAppsRequest(
    @SerializedName("apps")
    val apps: List<BulkInsertAppItem>
)

data class BulkUpdateAppItem(
    @SerializedName("app_name")
    val appName: String,
    @SerializedName("package_name")
    val packageName: String,
    @SerializedName("status_block")
    val statusBlock: String?,
    @SerializedName("is_whitelist")
    val isWhitelist: Boolean? = null // Dibuat nullable karena tidak ada di semua contoh
)

data class BulkUpdateAppsRequest(
    @SerializedName("apps")
    val apps: List<BulkUpdateAppItem>
)
