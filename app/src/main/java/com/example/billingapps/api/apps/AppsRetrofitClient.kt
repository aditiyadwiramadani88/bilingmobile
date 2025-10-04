package com.example.billingapps.api.apps

import com.example.billingapps.MyApp
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Object Retrofit Client untuk AppsApiService.
 */
object AppsRetrofitClient {

    // BASE_URL disesuaikan dengan dokumentasi API
    private val BASE_URL: String = MyApp.BE_URL

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val token = MyApp.TOKEN
            val newRequest = originalRequest.newBuilder()
                // Header Authorization bisa ditambahkan di sini jika diperlukan
                 .header("Authorization", "Bearer ${token}" )
                .header("Accept", "application/json")
                .build()
            chain.proceed(newRequest)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val instance: AppsApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
        retrofit.create(AppsApiService::class.java)
    }
}
