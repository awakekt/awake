plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

group = "com.awakekt.awake.template"
version = "1.0.0"
application {
    mainClass = "com.awakekt.awake.template.ApplicationKt"
}

dependencies {
    api(project(":core"))
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    // Holds Netty at the catalog's fixed version until Ktor brings it.
    implementation(platform(libs.netty.bom))
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
