plugins {
    alias(libs.plugins.kotlin.jvm)
}
kotlin { jvmToolchain(17) }
tasks.withType<Test> { useJUnit() }
dependencies { implementation(project(":format:archive")); testImplementation(libs.junit.junit) }
