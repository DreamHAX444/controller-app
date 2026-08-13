plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.livelocationservice"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.livelocationservice"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation("androidx.activity:activity-ktx:1.9.0")

  // Added Dependencies
  implementation(platform("io.github.jan-tennert.supabase:bom:3.7.0-beta-1"))
  implementation("io.github.jan-tennert.supabase:postgrest-kt")
  implementation("io.github.jan-tennert.supabase:realtime-kt")
  implementation("io.github.jan-tennert.supabase:storage-kt")
  implementation("io.ktor:ktor-client-cio:2.3.11")
  implementation("io.ktor:ktor-client-okhttp:3.0.0")
  implementation("com.google.android.gms:play-services-location:21.3.0")
  implementation("androidx.work:work-runtime-ktx:2.9.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
