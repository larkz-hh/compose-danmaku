import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "xyz.larkzhh.danmaku"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    explicitApi()
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.runtime)

    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.larkz-hh"
                artifactId = "compose-danmaku"
                version = "0.2.0"

                pom {
                    name.set("compose-danmaku")
                    description.set(
                        "A danmaku (bullet comment) overlay for Jetpack Compose, with lane " +
                            "allocation, collision avoidance and a pluggable item renderer."
                    )
                    url.set("https://github.com/larkz-hh/compose-danmaku")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("larkz-hh")
                            name.set("larkz-hh")
                            email.set("3632378642@qq.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/larkz-hh/compose-danmaku.git")
                        developerConnection.set("scm:git:ssh://github.com/larkz-hh/compose-danmaku.git")
                        url.set("https://github.com/larkz-hh/compose-danmaku")
                    }
                }
            }
        }
    }
}
