import java.util.Properties

// Read secret key from local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.portjeep"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.portjeep"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject secret key from local.properties into BuildConfig
        val secretKey = localProperties.getProperty("CRYPTO_SECRET_KEY") ?: "\"\""
        buildConfigField("String", "CRYPTO_SECRET_KEY", secretKey)
    }

    // Enables BuildConfig generation in modern AGP
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Lottie Animation Library
    implementation("com.airbnb.android:lottie:6.7.1")

    // Firebase (BoM, Analytics, Auth, Firestore)
    implementation(platform("com.google.firebase:firebase-bom:34.17.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    //glide
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // loading
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    //swipe
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}