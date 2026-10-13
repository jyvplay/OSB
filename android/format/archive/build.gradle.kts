plugins {
    id("org.jetbrains.kotlin.jvm")
}
kotlin { jvmToolchain(17) }
tasks.withType<Test> { useJUnit() }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation(project(":testing"))
}
