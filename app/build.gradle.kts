import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val versionProps = Properties().apply {
    val file = rootProject.file("version.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val resolvedVersionName: String =
    (findProperty("versionName") as String?)?.takeIf { it.isNotBlank() }
        ?: versionProps.getProperty("VERSION_NAME", "1.0.0-beta.1")

val resolvedVersionCode: Int =
    (findProperty("versionCode") as String?)?.toIntOrNull()
        ?: versionProps.getProperty("VERSION_CODE")?.toIntOrNull()
        ?: 100021

val resolvedChannel: String =
    (findProperty("buildChannel") as String?)?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        ?: when {
            resolvedVersionName.contains("-alpha", ignoreCase = true) -> "ALPHA"
            resolvedVersionName.contains("-beta", ignoreCase = true) -> "BETA"
            resolvedVersionName.contains("-rc", ignoreCase = true) -> "RC"
            else -> "STABLE"
        }

val fastDebugMode: Boolean =
    (findProperty("fastDebug") as String?)?.toBooleanStrictOrNull() ?: false

val localSigningProps = Properties().apply {
    val propFile = listOfNotNull(
        System.getenv("HOME"),
        System.getProperty("user.home"),
        gradle.gradleUserHomeDir.parentFile?.absolutePath
    ).distinct()
        .map { File(it, ".android/playswitch-release.properties") }
        .firstOrNull { it.exists() }
    if (propFile != null) {
        propFile.inputStream().use { load(it) }
    }
}

val releaseKeystorePath: String? =
    System.getenv("RELEASE_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
        ?: localSigningProps.getProperty("storeFile")?.takeIf { it.isNotBlank() }
val releaseKeystorePassword: String? =
    System.getenv("RELEASE_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: localSigningProps.getProperty("storePassword")?.takeIf { it.isNotBlank() }
val releaseKeyAlias: String? =
    System.getenv("RELEASE_KEY_ALIAS")?.takeIf { it.isNotBlank() }
        ?: localSigningProps.getProperty("keyAlias")?.takeIf { it.isNotBlank() }
val releaseKeyPassword: String? =
    System.getenv("RELEASE_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: localSigningProps.getProperty("keyPassword")?.takeIf { it.isNotBlank() }

val hasReleaseSigning: Boolean =
    !releaseKeystorePath.isNullOrBlank() &&
        File(releaseKeystorePath).exists() &&
        !releaseKeystorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.playswitch.ziadzero"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.playswitch.ziadzero"
        minSdk = 29
        targetSdk = 36
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
        buildConfigField("String", "BUILD_CHANNEL", "\"$resolvedChannel\"")
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = File(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        // Developer-only side-by-side build for IDE debugging; user Beta/Stable releases share `release`.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            buildConfigField("String", "BUILD_CHANNEL", "\"DEV\"")
            if (!fastDebugMode) {
                isMinifyEnabled = true
                isShrinkResources = true
                setProguardFiles(
                    listOf(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-debug-shrink.pro"
                    )
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.txt",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

base {
    archivesName.set("PlaySwitch-v$resolvedVersionName")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.libsu.core)
    implementation(libs.libsu.service)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}

tasks.withType<Test>().configureEach {
    val testTmp = layout.buildDirectory.dir("tmp/test").get().asFile
    doFirst {
        testTmp.mkdirs()
    }
    systemProperty("java.io.tmpdir", testTmp.absolutePath)
}
