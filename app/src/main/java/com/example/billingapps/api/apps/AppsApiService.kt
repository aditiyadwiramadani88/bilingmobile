package com.example.billingapps.api.apps

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Interface Retrofit yang disesuaikan dengan spesifikasi API baru.
 */
interface AppsApiService {

    /**
     * Mengambil semua aplikasi untuk deviceId tertentu.
     * Method: GET
     * Endpoint: /api/apps/{deviceId}
     */
    @GET("api/apps/{deviceId}")
    suspend fun getAllAppsByDevice(
        @Path("deviceId") deviceId: String
    ): Response<List<AppInfoResponse>>

    /**
     * Melakukan insert beberapa aplikasi sekaligus untuk deviceId tertentu.
     * Method: POST
     * Endpoint: /api/apps/bulk/{deviceId}
     */
    @POST("api/apps/bulk/{deviceId}")
    suspend fun bulkInsertApps(
        @Path("deviceId") deviceId: String,
        @Body request: BulkInsertAppsRequest
    ): Response<BulkInsertSuccessResponse>

    /**
     * Melakukan update beberapa aplikasi sekaligus berdasarkan deviceId dan package_name.
     * Method: PUT
     * Endpoint: /api/apps/bulk/{deviceId}
     */
    @PUT("api/apps/bulk/{deviceId}")
    suspend fun bulkUpdateApps(
        @Path("deviceId") deviceId: String,
        @Body request: BulkUpdateAppsRequest
    ): Response<BulkUpdateSuccessResponse>
}
