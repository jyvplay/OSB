plugins {
    alias(libs.plugins.kotlin.jvm)
}
kotlin { jvmToolchain(17) }
tasks.withType<Test> { useJUnit() }
dependencies {
    testImplementation(libs.junit.junit)
}

// Production-code contract runner, separate from fixtures consumed by other modules.
val contract by sourceSets.creating
kotlin.sourceSets.named("contract") {
    kotlin.srcDir("src/contract/kotlin")
    kotlin.srcDir("../core/documents/src/contract/kotlin")
    kotlin.srcDir("../format/ooxml/src/contract/kotlin")
    kotlin.srcDir("../core/windowing/src/contract/kotlin")
    kotlin.srcDir("../core/index/src/contract/kotlin")
    kotlin.srcDir("../core/files/src/main/kotlin")
    kotlin.srcDir("../feature/notes/src/main/kotlin")
    kotlin.srcDir("../feature/notes/src/contract/kotlin")
    // Compile this pure policy source, not the Android adapters in the same directory.
    kotlin.srcDir("../core/perf/src/main/java")
    kotlin.srcDir("../feature/beam/src/main/java")
    kotlin.srcDir("../feature/calc/src/main/kotlin")
    kotlin.srcDir("../feature/calc/src/contract/kotlin")
    kotlin.include("**/PilotCoreChecks.kt", "**/PerformancePolicy.kt", "**/QualityGovernor.kt", "**/calc/*.kt", "**/EditorChecks.kt", "**/EncodingDiffChecks.kt", "**/OoxmlChecks.kt", "**/CopyChecks.kt", "**/WindowRestoreChecks.kt", "**/EncryptedIndexChecks.kt", "**/SearchAndGateChecks.kt", "**/FileMutationPolicy.kt", "**/notes/*.kt")
}
configurations[contract.implementationConfigurationName].extendsFrom(configurations.implementation.get())
dependencies {
    add(contract.implementationConfigurationName, project(":core:windowing"))
    add(contract.implementationConfigurationName, project(":core:model"))
    add(contract.implementationConfigurationName, project(":core:documents"))
    add(contract.implementationConfigurationName, project(":format:ooxml"))
    add(contract.implementationConfigurationName, project(":core:commands"))
    add(contract.implementationConfigurationName, project(":core:index"))
}
tasks.register<JavaExec>("pilotContracts") {
    dependsOn(contract.classesTaskName)
    classpath = contract.runtimeClasspath
    mainClass.set("dev.fusion.testing.PilotCoreChecks")
}

tasks.register<JavaExec>("calculatorContracts") {
    dependsOn(contract.classesTaskName)
    classpath = contract.runtimeClasspath
    mainClass.set("dev.fusion.calc.CalculatorChecks")
}

tasks.register<JavaExec>("editorContracts") {
    dependsOn(contract.classesTaskName)
    classpath = contract.runtimeClasspath
    mainClass.set("dev.fusion.documents.EditorChecks")
}

tasks.register<JavaExec>("copyContracts") {
    dependsOn(contract.classesTaskName)
    classpath = contract.runtimeClasspath
    mainClass.set("dev.fusion.documents.CopyChecks")
    args(rootProject.file("core/documents/src/contract/resources").absolutePath)
}

tasks.register<JavaExec>("notesContracts") {
    dependsOn(contract.classesTaskName)
    classpath = contract.runtimeClasspath
    mainClass.set("dev.fusion.notes.NotesChecks")
}
tasks.register<JavaExec>("searchAndGateContracts") {
    dependsOn(contract.classesTaskName)
    classpath = contract.runtimeClasspath
    mainClass.set("dev.fusion.testing.SearchAndGateChecks")
}
tasks.register<JavaExec>("windowRestoreContracts") {
    dependsOn(contract.classesTaskName)
    classpath = contract.runtimeClasspath
    mainClass.set("dev.fusion.windowing.WindowRestoreChecks")
    args(rootProject.file("core/windowing/src/contract/resources").absolutePath)
}

// Linux POSIX/file-lock contracts; native Keystore acceptance is a separate device test.
tasks.register<JavaExec>("encryptedIndexContracts") {
    dependsOn(contract.classesTaskName)
    classpath = contract.runtimeClasspath
    mainClass.set("dev.fusion.index.EncryptedIndexChecks")
}

tasks.register<JavaExec>("encodingDiffContracts") {
    dependsOn(contract.classesTaskName)
    classpath = contract.runtimeClasspath
    mainClass.set("dev.fusion.documents.EncodingDiffChecks")
    args(rootProject.file("core/documents/src/contract/resources").absolutePath)
}
tasks.register<JavaExec>("ooxmlGuardContracts") {
    dependsOn(contract.classesTaskName)
    classpath = contract.runtimeClasspath
    mainClass.set("dev.fusion.ooxml.OoxmlChecks")
}
