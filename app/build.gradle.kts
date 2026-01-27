plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.afonso.fiveminutediary"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.afonso.fiveminutediary"
        minSdk = 24
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    val room_version = "2.8.4"

    implementation("androidx.room:room-runtime:$room_version")

    // If this project only uses Java source, use the Java annotationProcessor
    // No additional plugins are necessary
    annotationProcessor("androidx.room:room-compiler:$room_version")
    // Material Design
    implementation("com.google.android.material:material:1.11.0")
// RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
// CardView
    implementation("androidx.cardview:cardview:1.0.0")
// ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
// CoordinatorLayout
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    // AppCompat
    implementation("androidx.appcompat:appcompat:1.6.1")

    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}