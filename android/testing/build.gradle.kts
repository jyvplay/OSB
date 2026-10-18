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
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/contract/kotlin")
    kotlin.srcDir("../feature/calc/src/main/kotlin")
    kotlin.srcDir("../feature/calc/src/contract/kotlin")
    kotlin.include("**/PilotCoreChecks.kt", "**/PerformancePolicy.kt", "**/QualityGovernor.kt", "**/calc/*.kt", "**/EditorChecks.kt", "**/browser/model/*.kt", "**/EncodingDiffChecks.kt", "**/OoxmlChecks.kt", "**/CopyChecks.kt", "**/WindowRestoreChecks.kt", "**/EncryptedIndexChecks.kt", "**/SearchAndGateChecks.kt", "**/FileMutationPolicy.kt", "**/notes/*.kt")
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

tasks.register<JavaExec>("browserSessionContracts") {
    dependsOn(contract.classesTaskName)
    classpath = contract.runtimeClasspath
    mainClass.set("dev.fusion.browser.model.BrowserSessionChecks")
}

tasks.register<JavaExec>("noteLinkContracts") {
    dependsOn(contract.classesTaskName)
    classpath = contract.runtimeClasspath
    mainClass.set("dev.fusion.notes.NoteLinkChecks")
}

tasks.register<JavaExec>("noteTrashContracts") {
    dependsOn(contract.classesTaskName)
    classpath = contract.runtimeClasspath
    mainClass.set("dev.fusion.notes.NoteTrashChecks")
}

tasks.register<JavaExec>("noteDraftContracts") {
    dependsOn(contract.classesTaskName)
    classpath = contract.runtimeClasspath
    mainClass.set("dev.fusion.notes.NoteDraftChecks")
}

// R12 continuation: independent pure source set, no Android stubs or duplicate legacy contracts.
val subsets by sourceSets.creating
kotlin.sourceSets.named("subsets") {
    kotlin.srcDir("src/subsets/kotlin")
    listOf("core/settings", "feature/media", "format/image", "feature/backup", "feature/launcher", "format/native").forEach {
        kotlin.srcDir("../$it/src/main/kotlin")
    }
}
tasks.register<JavaExec>("subsetContracts") {
    dependsOn(subsets.classesTaskName)
    classpath = subsets.runtimeClasspath
    mainClass.set("dev.fusion.testing.SubsetChecks")
}

// R13: actual pure kernels, independent of Android adapters.
val r13 by sourceSets.creating
kotlin.sourceSets.named("r13") {
    kotlin.srcDir("src/r13/kotlin")
    listOf("core/files", "feature/terminal", "feature/automation", "feature/continuity", "format/ooxml").forEach {
        kotlin.srcDir("../$it/src/main/kotlin")
    }
    kotlin.include("**/ContentDigest.kt", "**/ShellWords.kt", "**/ActionPlan.kt", "**/CalendarExport.kt", "**/LiteralWorkbook.kt", "**/R13Checks.kt", "**/R13Oracle.kt")
}
tasks.register<JavaExec>("r13Contracts") {
    dependsOn(r13.classesTaskName)
    classpath = r13.runtimeClasspath
    mainClass.set("dev.fusion.testing.R13Checks")
}

// R14: six real pure production components, separately exercised by host consumers as well.
val r14 by sourceSets.creating
kotlin.sourceSets.named("r14") {
    kotlin.srcDir("src/r14/kotlin")
    listOf("feature/files", "core/designsystem", "feature/accessibility", "feature/alerts", "feature/settings", "format/ooxml").forEach {
        kotlin.srcDir("../$it/src/main/kotlin")
    }
    kotlin.include("**/DirectoryQuery.kt", "**/Contrast.kt", "**/SpatialFocus.kt", "**/NoticeLedger.kt", "**/SettingsPatch.kt", "**/DecimalSheet.kt", "**/R14Checks.kt", "**/R14Oracle.kt")
}
tasks.register<JavaExec>("r14Contracts") {
    dependsOn(r14.classesTaskName)
    classpath = r14.runtimeClasspath
    mainClass.set("dev.fusion.testing.R14Checks")
}

// R15 production kernels and original arithmetic wire format. No Android stubs.
val r15 by sourceSets.creating
kotlin.sourceSets.named("r15") {
    kotlin.srcDir("src/r15/kotlin")
    listOf("feature/lens", "feature/capture", "feature/messages", "feature/vault", "core/model", "format/ooxml").forEach {
        kotlin.srcDir("../$it/src/main/kotlin")
    }
    kotlin.include("**/ViewportTransform.kt", "**/RecordingTimeline.kt", "**/TranscriptArchive.kt", "**/VaultEnvelope.kt", "**/LaunchLinks.kt", "**/SheetFrame.kt", "**/DecimalSheet.kt", "**/R15Checks.kt", "**/R15Oracle.kt")
}
tasks.register<JavaExec>("r15Contracts") {
    dependsOn(r15.classesTaskName)
    classpath = r15.runtimeClasspath
    mainClass.set("dev.fusion.testing.R15Checks")
}

// R16 actual bounded peer transport and causal clock bounds. No simulated player/peer.
val r16 by sourceSets.creating
kotlin.sourceSets.named("r16") {
    kotlin.srcDir("src/r16/kotlin")
    kotlin.srcDir("../feature/together/src/main/kotlin")
}
tasks.register<JavaExec>("r16Contracts") {
    dependsOn(r16.classesTaskName)
    classpath = r16.runtimeClasspath
    mainClass.set("dev.fusion.testing.R16Checks")
}

val r17 by sourceSets.creating
kotlin.sourceSets.named("r17") {
    kotlin.include("**/TaskBook.kt", "**/R17Checks.kt", "**/R17Oracle.kt")
    kotlin.srcDir("src/r17/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
}
tasks.register<JavaExec>("r17Contracts") {
    dependsOn(r17.classesTaskName)
    classpath = r17.runtimeClasspath
    mainClass.set("dev.fusion.testing.R17Checks")
}

val r18 by sourceSets.creating
kotlin.sourceSets.named("r18") {
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.include("**/TaskBook.kt", "**/TaskQuery.kt", "**/OwnedSheetRequest.kt", "**/SheetFrame.kt", "**/DecimalSheet.kt", "**/R18Checks.kt", "**/R18Oracle.kt")
    kotlin.srcDir("src/r18/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
}
tasks.register<JavaExec>("r18Contracts") {
    dependsOn(r18.classesTaskName)
    classpath = r18.runtimeClasspath
    mainClass.set("dev.fusion.testing.R18Checks")
}

val r19 by sourceSets.creating
kotlin.sourceSets.named("r19") {
    kotlin.srcDir("src/r19/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.include("**/TaskBook.kt", "**/TaskChanges.kt", "**/TaskStreams.kt", "**/TaskWorkbook.kt", "**/LiteralWorkbook.kt", "**/R19Checks.kt", "**/R19Oracle.kt")
}
tasks.register<JavaExec>("r19Contracts") {
    dependsOn(r19.classesTaskName)
    classpath = r19.runtimeClasspath
    mainClass.set("dev.fusion.testing.R19Checks")
}

val r20 by sourceSets.creating
kotlin.sourceSets.named("r20") {
    kotlin.srcDir("src/r20/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.include("**/TaskBook.kt", "**/TaskCounts.kt", "**/LiteralWorkbook.kt", "**/LinkDisclosure.kt", "**/BrowserSessions.kt", "**/PermissionLeases.kt", "**/R20Checks.kt", "**/R20Oracle.kt")
}
tasks.register<JavaExec>("r20Contracts") {
    dependsOn(r20.classesTaskName);classpath=r20.runtimeClasspath;mainClass.set("dev.fusion.testing.R20Checks")
}

val r21 by sourceSets.creating
kotlin.sourceSets.named("r21") {
    kotlin.srcDir("../feature/automation/src/main/kotlin")
    kotlin.include("**/ExactReadback.kt")
    kotlin.srcDir("src/r21/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.include("**/LiteralWorkbook.kt", "**/CsvTable.kt", "**/BrowserSessions.kt", "**/PermissionLeases.kt", "**/BrowserConsentSession.kt", "**/R21Checks.kt", "**/R21Oracle.kt")
}
tasks.register<JavaExec>("r21Contracts") {
    dependsOn(r21.classesTaskName);classpath=r21.runtimeClasspath;mainClass.set("dev.fusion.testing.R21Checks")
}

val r22 by sourceSets.creating
kotlin.sourceSets.named("r22") {
    kotlin.srcDir("src/r22/kotlin")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.include("**/WorksheetInput.kt", "**/StrictZip.kt", "**/BoundedXml.kt", "**/LiteralXlsxImport.kt", "**/LiteralWorkbook.kt", "**/CsvTable.kt", "**/BrowserSessions.kt", "**/PermissionLeases.kt", "**/BrowserConsentSession.kt", "**/R22Checks.kt", "**/R22Oracle.kt")
}
tasks.register<JavaExec>("r22Contracts") {
    dependsOn(r22.classesTaskName);classpath=r22.runtimeClasspath;mainClass.set("dev.fusion.testing.R22Checks")
}

// R23: original input/lifetime/exposure kernels, no Android stubs or simulated platform.
val r23 by sourceSets.creating
kotlin.sourceSets.named("r23") {
    kotlin.srcDir("src/r23/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.include("**/TouchAdmission.kt", "**/VolatileDigits.kt", "**/LocalExposure.kt", "**/R23Checks.kt")
}
tasks.register<JavaExec>("r23Contracts") {
    dependsOn(r23.classesTaskName); classpath = r23.runtimeClasspath; mainClass.set("dev.fusion.testing.R23Checks")
}

// R24 actual shared appearance/dock/input policies; Android consumers verified separately.
val r24 by sourceSets.creating
kotlin.sourceSets.named("r24") {
    kotlin.srcDir("src/r24/kotlin")
    kotlin.srcDir("../core/settings/src/main/kotlin")
    kotlin.srcDir("../core/designsystem/src/main/kotlin")
    kotlin.srcDir("../core/windowing/src/main/kotlin")
    kotlin.srcDir("../format/pdf/src/main/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../core/model/src/main/kotlin")
    kotlin.include("**/CsvTable.kt","**/LiteralWorkbook.kt","**/PilotPolicy.kt","**/OwnedPdfRequest.kt","**/Appearance.kt","**/ProfessionalPalettes.kt","**/Contrast.kt","**/WindowEngine.kt","**/DesktopCommandStream.kt","**/InputPresentation.kt","**/R24Checks.kt")
}
tasks.register<JavaExec>("r24Contracts") {
    dependsOn(r24.classesTaskName);classpath=r24.runtimeClasspath;mainClass.set("dev.fusion.testing.R24Checks")
}

// R24 continuation: actual production kernels; no Android substitutes.
val r24c by sourceSets.creating
kotlin.sourceSets.named("r24c") {
    kotlin.srcDir("src/r24c/kotlin")
    kotlin.srcDir("../feature/launcher/src/main/kotlin")
    kotlin.srcDir("../feature/maps/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../core/designsystem/src/main/kotlin")
    kotlin.srcDir("../core/settings/src/main/kotlin")
    kotlin.srcDir("../core/windowing/src/main/kotlin")
    kotlin.include("**/ViewportCommit.kt", "**/Tabletop.kt", "**/TabletopStore.kt", "**/OfflineMap.kt", "**/ServiceLink.kt", "**/InputRearm.kt", "**/DesktopMetrics.kt", "**/Contrast.kt", "**/ProfessionalPalettes.kt", "**/Appearance.kt", "**/R24CChecks.kt")
}
tasks.register<JavaExec>("r24cContracts") {
    dependsOn(r24c.classesTaskName); classpath = r24c.runtimeClasspath; mainClass.set("dev.fusion.testing.R24CChecks")
    args(rootProject.file("feature/maps/src/main/assets/natural-earth-land-v1.txt").absolutePath)
}
