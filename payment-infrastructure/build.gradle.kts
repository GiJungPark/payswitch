plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":payment-application"))
    implementation(project(":payment-domain"))
}
