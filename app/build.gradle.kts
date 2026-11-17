plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

import java.util.Properties

val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.juge.app"
    compileSdk = 37

    signingConfigs {
        create("release") {
            // keystore.properties 被 .gitignore 挡在仓库外，干净 clone / CI 上没有它。
            // 早先无条件 rootProject.file(null) 会在**配置阶段**直接抛异常，
            // 连 lint / 单测都跑不起来。这里只在四项齐全时填签名配置：
            // 缺配置时 release 构建会明确报「未签名」，但 debug 构建不受影响。
            val required = listOf("keyAlias", "keyPassword", "storeFile", "storePassword")
            if (required.all { keystoreProperties.getProperty(it) != null }) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.juge.app"
        minSdk = 24
        // targetSdk 跟 compileSdk 走：Google Play 对 targetSdk 有下限要求，
        // 停在被淘汰的 35 上会在提交审核时被拒；行为差异集中在对组件的边到边
        // （edge-to-edge）强制与前台服务类型声明，本应用都没有依赖旧行为。
        targetSdk = 37
        // 版本号从 1 起步的那个值是脚手架默认值，一直没改过——每次发版都会
        // 被商店判成「同一个包重复上传」。这里是第一次正式发布前的复位。
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG 用于把 DevProToggle 圈进 debug 构建（release 无偿失去该开关）
        buildConfig = true
    }
    testOptions {
        // WidgetStyle 默认值与 PRESETS 使用 android.graphics.Color.parseColor，
        // 本地单元测试的 mockable android.jar 需要返回默认值而不是抛 "not mocked"
        unitTests.isReturnDefaultValues = true
        // Robolectric 渲染预览图时要能读到 res/ 下的 drawable 与 assets/ 下的字体
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // collectAsStateWithLifecycle：界面不在前台时停止收集，避免后台线程把
    // 状态推给已经不可见的界面
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coil.compose)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    implementation(libs.alipay.sdk)
    testImplementation(libs.junit)
    // WidgetStyle 的 JSON 序列化在单元测试中依赖 org.json；Android 运行时自带，测试运行时需要显式引入
    testImplementation("org.json:json:20240303")
    // 在 JVM 上以真实图形栈渲染组件预览图（无需连接真机/模拟器）
    testImplementation(libs.robolectric)
    // 在 JVM 上跑 Compose：UI 基线（截图 + 语义树）用，无需真机/模拟器
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// CI 上跳过「像素 / UI 指纹基线」这几个类：它们用 Robolectric NATIVE 的真实 Skia
// 栅格化出图并逐像素比对，macOS 与 Linux 的抗锯齿实现有细微差异，
// 换台机器就会假红。这类基线保留在本地手动跑；CI 仍跑其余全部单测。
// 用法：./gradlew :app:testDebugUnitTest -PciSkipPixelBaselines
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    if (project.hasProperty("ciSkipPixelBaselines")) {
        listOf(
            "*WidgetRenderBaselineTest*",
            "*MainUiBaselineTest*",
            "*PrivacyDialogUiBaselineTest*",
        ).forEach { filter.excludeTestsMatching(it) }
    }
}
