plugins { id("com.android.application") }

val metaAppId = providers.gradleProperty("driveLogMetaAppId").orElse("").get()
require(metaAppId.isEmpty() || metaAppId.matches(Regex("[1-9][0-9]{1,24}")))

android {
    namespace = "kr.drivelog.phone"
    compileSdk = 35
    defaultConfig {
        applicationId = "kr.drivelog.phone"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        resValue("string", "meta_app_id", metaAppId)
    }
}

dependencies { implementation("androidx.core:core:1.13.1") }
