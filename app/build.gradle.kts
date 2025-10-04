plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.billingapps"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.billingapps"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

        }
        debug {

        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
}


dependencies {
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.pusher.java)   // untuk pusher-java-client
    // Dependensi Compose yang sudah ada
    implementation(libs.androidx.core.ktx)
    // Baris ini duplikat, bisa dihapus, tapi saya biarkan sesuai file Anda
    implementation(libs.androidx.core.ktx)
    implementation(libs.work.runtime)
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0")

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.dotenv.kotlin)
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation(libs.bundles.media3)

    // --- Tambahkan dependensi BARU di sini ---
    // Retrofit untuk Networking
    implementation(libs.retrofit.core)
    // Converter Gson untuk parsing JSON
    implementation(libs.retrofit.converter.gson)
    // Coroutines untuk asynchronous call
    implementation(libs.kotlinx.coroutines.android)

    // Dependensi Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

}



