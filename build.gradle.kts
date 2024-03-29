plugins {
    kotlin("jvm") version "1.9.23"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
dependencies {
    implementation(platform("org.http4k:http4k-bom:5.14.4.0"))
    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-server-undertow")
    implementation("org.http4k:http4k-client-apache")

    val version = "0.3.1"
    // For parsing HTML
    implementation("com.mohamedrejeb.ksoup:ksoup-html:$version")
}


tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}