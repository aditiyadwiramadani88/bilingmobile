package com.example.billingapps.api
import com.example.billingapps.MyApp
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    /**
     * Mengambil base URL dari file .env dengan cara yang aman (safe call).
     * Jika BE_URL tidak ditemukan atau .env gagal dimuat, aplikasi akan crash
     * dengan pesan yang jelas.
     */
    private val BASE_URL: String = MyApp.BE_URL

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            // Mengambil token dengan cara yang aman dari dotenv yang mungkin null
            val token = MyApp.TOKEN
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
            chain.proceed(newRequest)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()

        retrofit.create(ApiService::class.java)
    }
}
