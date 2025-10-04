package com.example.billingapps.api
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path


interface ApiService {

    @GET("api/devices/{deviceId}")
    suspend fun getDeviceStatus(
        @Path("deviceId") deviceId: String
    ): Response<DeviceStatusResponse>

    @GET("api/devices/{deviceId}")
    suspend fun activateDevice(
        @Path("deviceId") deviceId: String
    ): Response<DeviceActivationResponse>

    @POST("api/login/{deviceId}")
    suspend fun loginWithPin(
        @Path("deviceId") deviceId: String,
        @Body request: PinLoginRequest
    ): Response<PinLoginResponse>

    @POST("api/devices/{deviceId}/last-see")
    suspend fun sendStatus(
        @Path("deviceId") deviceId: String,
    ): Response<PinLoginResponse>

    // TAMBAHAN: Endpoint untuk update status iklan
    @PUT("api/ads/{adId}/device-status")
    suspend fun updateAdStatus(
        @Path("adId") adId: String,
        @Body request: UpdateAdStatusRequest
    ): Response<UpdateAdStatusResponse>

    @POST("api/devices/{deviceId}/location")
    suspend fun sendLocation(
        @Path("deviceId") deviceId: String,
        @Body request: LocationUpdateRequest
    ): Response<LocationUpdateResponse>

    // --- TAMBAHAN BARU UNTUK FRAME SCREENSHOT ---
    @POST("api/devices/{deviceId}/update-frame-ss")
    suspend fun updateFrameScreenshot(
        @Path("deviceId") deviceId: String,
        @Body request: FrameScreenshotRequest
    ): Response<FrameScreenshotResponse>

    // +++ TAMBAHAN BARU UNTUK UNLOCK SESSION +++
    @POST("api/session/start")
    suspend fun startSession(
        @Body request: StartSessionRequest
    ): Response<StartSessionResponse>
}
