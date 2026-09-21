plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "com.goldrs.vida"; compileSdk = 35
    defaultConfig { applicationId = "com.goldrs.vida"; minSdk = 24; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
