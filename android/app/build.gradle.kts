import java.util.Properties
import javax.inject.Inject

plugins {
    id("com.android.application")
}

// La firma del APK vive fuera del repositorio: ~/.estela/firma.properties
val firma = Properties().apply {
    val archivo = File(System.getProperty("user.home"), ".estela/firma.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

android {
    namespace = "io.github.xdaserr.estela"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.xdaserr.estela"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.3"
    }

    // Un APK por tipo de procesador (más ligeros) y uno universal que sirve en todos
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (firma.isNotEmpty()) {
            create("release") {
                storeFile = File(firma.getProperty("storeFile"))
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // youtubedl-android necesita que las librerías nativas se extraigan al instalar
    packaging {
        jniLibs {
            useLegacyPackaging = true
            // Python y FFmpeg vienen como .zip con extensión .so: no son librerías que se puedan "strip"
            keepDebugSymbols += setOf("**/libpython.zip.so", "**/libffmpeg.zip.so")
        }
    }
}

// Usa la misma interfaz que la versión de escritorio (../../ui), copiada a assets/ui al compilar
abstract class CopiarUi : DefaultTask() {
    @get:InputDirectory abstract val origen: DirectoryProperty
    @get:OutputDirectory abstract val salida: DirectoryProperty
    @get:Inject abstract val archivos: FileSystemOperations

    @TaskAction
    fun copiar() {
        archivos.sync {
            from(origen)
            into(salida.dir("ui"))
        }
    }
}

val copiarUi = tasks.register<CopiarUi>("copiarUi") {
    origen.set(layout.projectDirectory.dir("../../ui"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copiarUi, CopiarUi::salida)
    }
}

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}
