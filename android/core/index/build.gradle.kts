plugins {
    alias(libs.plugins.kotlin.jvm)
}
kotlin { jvmToolchain(17) }
tasks.withType<Test> { useJUnit() }
dependencies { testImplementation(libs.junit.junit) }
