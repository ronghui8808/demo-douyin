import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun ossProp(key: String, default: String = ""): String {
    return localProperties.getProperty(key, default).replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "com.example.douyin"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.douyin"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val ossEnabled = localProperties.getProperty("oss.enabled", "false").toBoolean()
        buildConfigField("boolean", "OSS_ENABLED", ossEnabled.toString())
        buildConfigField("String", "OSS_ENDPOINT", "\"${ossProp("oss.endpoint")}\"")
        buildConfigField("String", "OSS_BUCKET", "\"${ossProp("oss.bucket")}\"")
        buildConfigField("String", "OSS_ACCESS_KEY_ID", "\"${ossProp("oss.accessKeyId")}\"")
        buildConfigField("String", "OSS_ACCESS_KEY_SECRET", "\"${ossProp("oss.accessKeySecret")}\"")
        buildConfigField("String", "OSS_PUBLIC_DOMAIN", "\"${ossProp("oss.publicDomain")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.lifecycle.runtime)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)

    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    implementation(libs.aliyun.oss.android.sdk)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
