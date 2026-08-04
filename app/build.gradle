plugins {
    id 'com.android.application'
}

import java.security.MessageDigest

android {
    namespace 'ru.splitproxy.mobile'
    compileSdk 35
    ndkVersion '26.3.11579264'

    defaultConfig {
        applicationId 'ru.splitproxy.mobile'
        minSdk 23
        targetSdk 35
        versionCode 2
        versionName '0.2.0'

        externalNativeBuild {
            cmake {
                cppFlags '-std=c++17 -Wall -Wextra'
                abiFilters 'arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'
            }
        }
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    externalNativeBuild {
        cmake {
            path file('CMakeLists.txt')
            version '3.22.1'
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'com.google.android.material:material:1.12.0'
}

// Официальные Android-библиотеки tun2proxy. Серверная часть не меняется.
def tun2proxyVersion = '0.8.1'
def tun2proxyUrl = "https://github.com/tun2proxy/tun2proxy/releases/download/v${tun2proxyVersion}/tun2proxy-android-libs.zip"
def nativeOutput = file('src/main/jniLibs')
def nativeWork = layout.buildDirectory.dir('tun2proxy-download')

tasks.register('prepareTun2Proxy') {
    outputs.dir(nativeOutput)
    doLast {
        def required = file("${nativeOutput}/arm64-v8a/libtun2proxy.so")
        if (required.exists()) {
            return
        }

        def workDir = nativeWork.get().asFile
        delete workDir
        workDir.mkdirs()
        def archive = new File(workDir, 'tun2proxy.zip')
        println "Downloading tun2proxy ${tun2proxyVersion}..."
        new URL(tun2proxyUrl).withInputStream { input ->
            archive.withOutputStream { output -> output << input }
        }

        copy {
            from zipTree(archive)
            into workDir
        }

        ['arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'].each { abi ->
            def source = new File(workDir, "tun2proxy-android-libs/${abi}/libtun2proxy.so")
            if (!source.exists()) {
                throw new GradleException("Missing native library: ${source}")
            }
            copy {
                from source
                into new File(nativeOutput, abi)
            }
        }
    }
}

tasks.named('preBuild').configure {
    dependsOn tasks.named('prepareTun2Proxy')
}
