plugins { id("com.android.application") version "9.1.1" }

android {
    namespace = "cl.demeberant.pocketzone"
    compileSdk = 36
    defaultConfig {
        applicationId = "cl.demeberant.pocketzone.experimental"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.$versionCode"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
