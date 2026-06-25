import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

val krugLocalProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Release signing — vrednosti se čitaju iz local.properties (gitignored). Ako bilo koja
// vrednost fali ili keystore fajl ne postoji (npr. drugi developer / CI bez secrets-a),
// release build se pravi unsigned umesto da pukne. To omogućava da non-owner može da
// kompajlira projekat dok release potpisivanje ostaje vezano za ownerov keystore.
val keystoreRelPath = krugLocalProps.getProperty("KRUG_KEYSTORE_PATH")
val keystorePassword = krugLocalProps.getProperty("KRUG_KEYSTORE_PASSWORD")
val releaseKeyAlias = krugLocalProps.getProperty("KRUG_KEY_ALIAS")
val releaseKeyPassword = krugLocalProps.getProperty("KRUG_KEY_PASSWORD")
val releaseKeystoreFile = keystoreRelPath?.let { rootProject.file(it) }
val hasReleaseKeystore = releaseKeystoreFile?.exists() == true &&
    !keystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "org.krug.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.krug.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Mapbox public token: kept as manifest placeholder for legacy meta-data,
        // and exposed via BuildConfig so KrugApplication can set MapboxOptions.accessToken
        // (Mapbox SDK 11+ ignores the meta-data tag).
        val mapboxPublicToken: String =
            krugLocalProps.getProperty("KRUG_MAPBOX_PUBLIC_TOKEN")
                ?: providers.gradleProperty("KRUG_MAPBOX_PUBLIC_TOKEN").orNull
                ?: ""
        manifestPlaceholders["MAPBOX_PUBLIC_TOKEN"] = mapboxPublicToken
        buildConfigField("String", "MAPBOX_PUBLIC_TOKEN", "\"$mapboxPublicToken\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = keystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.appcheck.playintegrity)
    // App Check debug provider — moramo da bude u svim build-ovima (ne samo debug) jer
    // KrugApplication ima compile-time referencu na DebugAppCheckProviderFactory u
    // `if (BuildConfig.DEBUG)` granu. Runtime ostaje DEBUG-only.
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.play.services.auth)

    // Credential Manager (modern Google sign-in)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Mapbox
    implementation(libs.mapbox.android)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // Location
    implementation(libs.play.services.location)

    // Permissions
    implementation(libs.accompanist.permissions)

    // Image loading
    implementation(libs.coil.compose)

    // Logging
    implementation(libs.timber)

    // WorkManager — periodic health check / FGS keepalive
    implementation(libs.androidx.work.runtime)

    // Android 12+ splash screen API — drži sistemski splash dok ViewModel ne odluči,
    // pa direktno na sledeći route. Eliminiše "double splash" jump.
    implementation(libs.androidx.core.splashscreen)

    // Chrome Custom Tabs — in-app browser za About → Privacy/Terms, ne baca user-a
    // u eksterni browser task (back vraća u Krug, ne gasi app).
    implementation(libs.androidx.browser)

    // ProfileInstaller — instalira baseline profile pri install-u app-a. Sa
    // `baseline-prof.txt` u src/main/, ART precompile-uje navedene Kotlin/Compose
    // hot path-ove pri install-u → ~10-30% brži cold start. Google Play takođe
    // distribuira "cloud profile" agregiran iz svih usera kad ima dovoljno traffic-a.
    implementation(libs.androidx.profileinstaller)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
