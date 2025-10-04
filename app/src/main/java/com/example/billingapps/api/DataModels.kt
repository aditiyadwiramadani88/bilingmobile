package com.example.billingapps.api
import com.google.gson.annotations.SerializedName

// File ini diupdate untuk mencakup semua field dari response API yang baru

// 1. Wrapper utama untuk response
data class DeviceStatusResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("data")
    val data: DeviceDetails?
)

// 2. Detail lengkap dari device, termasuk relasi dan iklan
data class DeviceDetails(
    @SerializedName("id")
    val id: Int,
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("is_locked")
    val isLocked: Boolean,
    @SerializedName("outlet")
    val outlet: Outlet?,
    @SerializedName("lokasi")
    val lokasi: Lokasi?,
    @SerializedName("lockscreen_design")
    val lockscreenDesign: LockscreenDesign?,
    @SerializedName("available_pending_ads") // TAMBAHAN: Menangkap data iklan
    val availablePendingAds: List<Ad>?
)

// --- Model Baru untuk Iklan ---
data class Ad(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("type")
    val type: String, // "image", "text", "video"
    @SerializedName("content")
    val content: String,
    @SerializedName("duration_seconds")
    val durationSeconds: Int
)

// Request body untuk update status iklan
data class UpdateAdStatusRequest(
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("status")
    val status: String
)

// Response body untuk update status iklan
data class UpdateAdStatusResponse(
    @SerializedName("message")
    val message: String,
    @SerializedName("ad_id")
    val adId: Int,
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("new_status")
    val newStatus: String
)
// --- Akhir Model Iklan ---


// 3. Model untuk Outlet
data class Outlet(
    @SerializedName("id")
    val id: Int,
    @SerializedName("nama")
    val nama: String,
    @SerializedName("alamat")
    val alamat: String?,
    @SerializedName("logo")
    val logo: String?
)

// 4. Model untuk Lokasi
data class Lokasi(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String
)

// 5. Model untuk Desain Lockscreen
data class LockscreenDesign(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("background_image")
    val backgroundImage: String?,
    @SerializedName("block_content1")
    val blockContent1: BlockContent?,
    @SerializedName("block_content2")
    val blockContent2: BlockContent?,
    @SerializedName("block_content3")
    val blockContent3: BlockContent?
)

// 6. Model untuk Konten Blok (bisa berupa teks, video, atau slide gambar)
data class BlockContent(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("type")
    val type: String, // "video", "text", "slide_image"
    @SerializedName("content")
    val content: String?,
    @SerializedName("video_url")
    val videoUrl: String?,
    @SerializedName("media")
    val media: List<MediaItem>?
)

// 7. Model untuk item media (gambar dalam slide)
data class MediaItem(
    @SerializedName("url")
    val url: String
)

// --- Model yang sudah ada sebelumnya ---

data class DeviceActivationResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("data")
    val data: DeviceData?,
    @SerializedName("message")
    val message: String?
)

data class DeviceData(
    @SerializedName("id")
    val id: Int,
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("status")
    val status: String
)

data class PinLoginRequest(
    @SerializedName("pin")
    val pin: String
)

data class PinLoginResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String
)

data class LocationUpdateRequest(
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("longitude")
    val longitude: Double,
    @SerializedName("accuracy")
    val accuracy: Float?,
    @SerializedName("altitude")
    val altitude: Double?,
    @SerializedName("speed")
    val speed: Float?,
    @SerializedName("heading")
    val heading: Float?,
    @SerializedName("timestamp")
    val timestamp: String
)

data class LocationUpdateResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String
)


// --- TAMBAHAN BARU UNTUK FRAME SCREENSHOT ---

data class FrameScreenshotRequest(
    @SerializedName("frame_ss_base64")
    val frameSsBase64: String
)

data class FrameScreenshotResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String
)

// +++ TAMBAHAN BARU UNTUK UNLOCK SESSION +++
data class StartSessionRequest(
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("password")
    val password: String
)

data class StartSessionResponse(
    @SerializedName("message")
    val message: String
)
