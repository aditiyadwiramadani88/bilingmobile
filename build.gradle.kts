// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    kotlin("jvm") version "2.0.0"

}



dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.2.1")
    implementation("io.socket:socket.io-client:2.1.2")
    implementation(libs.kotlinx.coroutines.android)
    implementation("org.json:json:20250517")
    implementation("dev.icerock.moko:socket-io:0.6.0")
}



