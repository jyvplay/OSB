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

// R25 actual bounded PNG transform, separate from native isolated-worker/picker gates.
val r25png by sourceSets.creating
kotlin.sourceSets.named("r25png") {
    kotlin.srcDir("src/r25/kotlin")
    kotlin.srcDir("../format/image/src/main/kotlin")
    kotlin.include("**/PngMetadata.kt", "**/PngChecks.kt")
}
tasks.register<JavaExec>("r25PngContracts") {
    dependsOn(r25png.classesTaskName);classpath=r25png.runtimeClasspath;mainClass.set("dev.fusion.testing.PngChecks")
}

// R39 kernels executed under real Gradle/AGP toolchain in CI (JVM lane).
val r39 by sourceSets.creating
kotlin.sourceSets.named("r39") {
    kotlin.srcDir("src/r39/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.srcDir("../format/image/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../core/hw/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../core/storage/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../feature/vault/src/main/kotlin")
    kotlin.srcDir("../feature/find/src/main/java")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.srcDir("../feature/backup/src/main/kotlin")
    kotlin.srcDir("../feature/alerts/src/main/kotlin")
    kotlin.srcDir("../feature/capture/src/main/kotlin")
    kotlin.include("**/SerialDate.kt", "**/ColLetters.kt", "**/ColWidth.kt", "**/SmartQuote.kt", "**/HyphenSoft.kt", "**/RomanNum.kt", "**/CropBox.kt", "**/IcoDir.kt", "**/Punycode.kt", "**/UsbDesc.kt", "**/MidiNote.kt", "**/Ulid.kt", "**/JwtSplit.kt", "**/IbanCheck.kt", "**/GlobMatch.kt", "**/MacAddr.kt", "**/Rfc3339.kt", "**/QuietHours.kt", "**/ExposureEv.kt", "**/R39Checks.kt")
}
tasks.register<JavaExec>("r39Contracts") {
    dependsOn(r39.classesTaskName); classpath = r39.runtimeClasspath; mainClass.set("dev.fusion.testing.R39Checks")
    args(rootProject.file("tools/fixtures/r39").absolutePath)
}

// R40 kernels executed under real Gradle/AGP toolchain in CI (JVM lane).
val r40 by sourceSets.creating
kotlin.sourceSets.named("r40") {
    kotlin.srcDir("src/r40/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../feature/messages/src/main/kotlin")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../core/index/src/main/kotlin")
    kotlin.srcDir("../feature/lens/src/main/kotlin")
    kotlin.srcDir("../core/files/src/main/java")
    kotlin.srcDir("../core/perf/src/main/kotlin")
    kotlin.srcDir("../feature/notes/src/main/kotlin")
    kotlin.srcDir("../core/windowing/src/main/kotlin")
    kotlin.srcDir("../cast/airplay/src/main/kotlin")
    kotlin.srcDir("../feature/launcher/src/main/kotlin")
    kotlin.srcDir("../feature/vault/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.include("**/NumFmtSections.kt", "**/CellErr.kt", "**/DashEducate.kt", "**/ObjReplace.kt", "**/FootnoteMark.kt", "**/PdfRotate.kt", "**/HtmlEntity.kt", "**/Rfc2047.kt", "**/SevenZHead.kt", "**/Fnv1a.kt", "**/Mrz.kt", "**/SafeName.kt", "**/Percentile.kt", "**/TaskCheckbox.kt", "**/SnapFraction.kt", "**/PlistHead.kt", "**/Monogram.kt", "**/Bech32.kt", "**/OtpauthUri.kt", "**/IpRedact.kt", "**/R40Checks.kt")
}
tasks.register<JavaExec>("r40Contracts") {
    dependsOn(r40.classesTaskName); classpath = r40.runtimeClasspath; mainClass.set("dev.fusion.testing.R40Checks")
    args(rootProject.file("tools/fixtures/r40").absolutePath)
}

// R38 kernels backported to CI execution (same bridge pattern as r39/r40).
val r38 by sourceSets.creating
kotlin.sourceSets.named("r38") {
    kotlin.srcDir("src/r38/kotlin")
    kotlin.srcDir("../feature/beam/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../format/image/src/main/kotlin")
    kotlin.srcDir("../core/model/src/main/kotlin")
    kotlin.srcDir("../core/storage/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../feature/messages/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
    kotlin.srcDir("../feature/accessibility/src/main/kotlin")
    kotlin.srcDir("../core/hw/src/main/kotlin")
    kotlin.srcDir("../feature/capture/src/main/kotlin")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.srcDir("../core/index/src/main/kotlin")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.include("**/MagnetParse.kt", "**/Bencode.kt", "**/FlacHead.kt", "**/WebpHead.kt", "**/SemVer.kt", "**/UuidInfo.kt", "**/HttpRange.kt", "**/QuotedPrint.kt", "**/PemBound.kt", "**/KeyCadence.kt", "**/IsoWeek.kt", "**/HslColor.kt", "**/EdidHead.kt", "**/VttTime.kt", "**/LuhnCheck.kt", "**/Soundex.kt", "**/PdfName.kt", "**/R38Checks.kt")
}
tasks.register<JavaExec>("r38Contracts") {
    dependsOn(r38.classesTaskName); classpath = r38.runtimeClasspath; mainClass.set("dev.fusion.testing.R38Checks")
    args(rootProject.file("tools/fixtures/r38").absolutePath)
}

// R41 kernels executed under real Gradle/AGP toolchain in CI (JVM lane).
val r41 by sourceSets.creating
kotlin.sourceSets.named("r41") {
    kotlin.srcDir("src/r41/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.srcDir("../core/commands/src/main/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
    kotlin.srcDir("../feature/maps/src/main/kotlin")
    kotlin.srcDir("../core/model/src/main/kotlin")
    kotlin.srcDir("../feature/accessibility/src/main/kotlin")
    kotlin.srcDir("../feature/lens/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../core/index/src/main/kotlin")
    kotlin.srcDir("../feature/together/src/main/kotlin")
    kotlin.srcDir("../core/perf/src/main/kotlin")
    kotlin.srcDir("../core/settings/src/main/kotlin")
    kotlin.srcDir("../cast/castv2/src/main/kotlin")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../core/storage/src/main/kotlin")
    kotlin.srcDir("../feature/find/src/main/java")
    kotlin.srcDir("../feature/beam/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../feature/launcher/src/main/kotlin")
    kotlin.include("**/ColLetters.kt", "**/SheetRange.kt", "**/R1c1Ref.kt", "**/CropBox.kt", "**/PdfRotate.kt", "**/PdfPageGeometry.kt", "**/ShlexJoin.kt", "**/DurationIso.kt", "**/DmsFormat.kt", "**/ColorHex.kt", "**/ReadEase.kt", "**/Ean13.kt", "**/DataUri.kt", "**/Trigram.kt", "**/TurnOrder.kt", "**/Ewma.kt", "**/KeyMigrate.kt", "**/CastNs.kt", "**/LineWidth.kt", "**/ShardPath.kt", "**/SpanMerge.kt", "**/PiecePlan.kt", "**/TabStops.kt", "**/GridPos.kt", "**/R41Checks.kt")
}
tasks.register<JavaExec>("r41Contracts") {
    dependsOn(r41.classesTaskName); classpath = r41.runtimeClasspath; mainClass.set("dev.fusion.testing.R41Checks")
    args(rootProject.file("tools/fixtures/r41").absolutePath)
}

// R37 kernels backported to CI execution (same bridge pattern).
val r37 by sourceSets.creating
kotlin.sourceSets.named("r37") {
    kotlin.srcDir("src/r37/kotlin")
    kotlin.srcDir("../feature/notes/src/main/java")
    kotlin.srcDir("../feature/files/src/main/kotlin")
    kotlin.srcDir("../feature/continuity/src/main/kotlin")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../cast/airplay/src/main/kotlin")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../feature/maps/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../feature/terminal/src/main/kotlin")
    kotlin.srcDir("../feature/find/src/main/kotlin")
    kotlin.srcDir("../core/commands/src/main/kotlin")
    kotlin.srcDir("../feature/lens/src/main/kotlin")
    kotlin.include("**/SlugHead.kt", "**/SizeBucket.kt", "**/VTodoParse.kt", "**/TaskTally.kt", "**/XzHead.kt", "**/PasteWrap.kt", "**/RtspReason.kt", "**/UtmStrip.kt", "**/HibpRange.kt", "**/PolyLine.kt", "**/Mp4Box.kt", "**/OscLink.kt", "**/RankFuse.kt", "**/ExitCode.kt", "**/FovMath.kt", "**/R37Checks.kt")
}
tasks.register<JavaExec>("r37Contracts") {
    dependsOn(r37.classesTaskName); classpath = r37.runtimeClasspath; mainClass.set("dev.fusion.testing.R37Checks")
    args(rootProject.file("tools/fixtures/r37").absolutePath)
}

// R42 kernels executed under real Gradle/AGP toolchain in CI (JVM lane).
val r42 by sourceSets.creating
kotlin.sourceSets.named("r42") {
    kotlin.srcDir("src/r42/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.srcDir("../feature/maps/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../feature/continuity/src/main/kotlin")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../feature/notes/src/main/kotlin")
    kotlin.srcDir("../feature/capture/src/main/kotlin")
    kotlin.srcDir("../core/perf/src/main/kotlin")
    kotlin.srcDir("../feature/terminal/src/main/kotlin")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.srcDir("../feature/vault/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../core/storage/src/main/kotlin")
    kotlin.srcDir("../feature/alerts/src/main/kotlin")
    kotlin.srcDir("../feature/calc/src/main/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
    kotlin.srcDir("../feature/messages/src/main/kotlin")
    kotlin.srcDir("../format/image/src/main/kotlin")
    kotlin.include("**/ColLetters.kt", "**/SheetRange.kt", "**/AbsRef.kt", "**/MergedCells.kt", "**/PdfDate.kt", "**/NmeaLat.kt", "**/HstsParse.kt", "**/PairPin.kt", "**/ArHead.kt", "**/CaseCycle.kt", "**/CommentToggle.kt", "**/UnitPrice.kt", "**/Rule3rds.kt", "**/Histo.kt", "**/CsiCursor.kt", "**/GpcHeader.kt", "**/PwPolicy.kt", "**/GainDb.kt", "**/HexBytes.kt", "**/SnoozeSeq.kt", "**/NiceStep.kt", "**/NextRun.kt", "**/Rfc2231.kt", "**/BmpHead.kt", "**/R42Checks.kt")
}
tasks.register<JavaExec>("r42Contracts") {
    dependsOn(r42.classesTaskName); classpath = r42.runtimeClasspath; mainClass.set("dev.fusion.testing.R42Checks")
    args(rootProject.file("tools/fixtures/r42").absolutePath)
}

// R43 kernels executed under real Gradle/AGP toolchain in CI (JVM lane).
val r43 by sourceSets.creating
kotlin.sourceSets.named("r43") {
    kotlin.srcDir("src/r43/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../core/capabilities/src/main/java")
    kotlin.srcDir("../feature/together/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../feature/beam/src/main/kotlin")
    kotlin.srcDir("../feature/lens/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../core/model/src/main/kotlin")
    kotlin.srcDir("../feature/find/src/main/java")
    kotlin.srcDir("../feature/vault/src/main/kotlin")
    kotlin.srcDir("../feature/files/src/main/kotlin")
    kotlin.srcDir("../core/perf/src/main/kotlin")
    kotlin.srcDir("../feature/calc/src/main/kotlin")
    kotlin.srcDir("../cast/airplay/src/main/kotlin")
    kotlin.srcDir("../feature/messages/src/main/kotlin")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.srcDir("../core/windowing/src/main/kotlin")
    kotlin.srcDir("../feature/alerts/src/main/kotlin")
    kotlin.include("**/ColLetters.kt", "**/SheetRange.kt", "**/SheetFreeze.kt", "**/BidiStrip.kt", "**/DupLines.kt", "**/PdfPerm.kt", "**/RefererPolicy.kt", "**/DebControl.kt", "**/ApiName.kt", "**/SeatColor.kt", "**/Bpm.kt", "**/TrackerTier.kt", "**/QrVersion.kt", "**/RpIdMatch.kt", "**/SemVer.kt", "**/VersionRange.kt", "**/SpanMerge.kt", "**/SnippetWindow.kt", "**/Bech32.kt", "**/AgeId.kt", "**/BreadcrumbTrim.kt", "**/Apdex.kt", "**/PercentChange.kt", "**/RtspTransport.kt", "**/MsgIdParse.kt", "**/TimeCoarse.kt", "**/AspectSnap.kt", "**/AlertKey.kt", "**/R43Checks.kt")
}
tasks.register<JavaExec>("r43Contracts") {
    dependsOn(r43.classesTaskName); classpath = r43.runtimeClasspath; mainClass.set("dev.fusion.testing.R43Checks")
    args(rootProject.file("tools/fixtures/r43").absolutePath)
}

// R44 kernels executed under real Gradle/AGP toolchain in CI (JVM lane).
val r44 by sourceSets.creating
kotlin.sourceSets.named("r44") {
    kotlin.srcDir("src/r44/kotlin")
    kotlin.srcDir("../feature/alerts/src/main/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../feature/lens/src/main/kotlin")
    kotlin.srcDir("../feature/terminal/src/main/kotlin")
    kotlin.srcDir("../core/index/src/main/kotlin")
    kotlin.srcDir("../core/settings/src/main/kotlin")
    kotlin.srcDir("../core/commands/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../feature/messages/src/main/kotlin")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.srcDir("../core/hw/src/main/kotlin")
    kotlin.srcDir("../feature/backup/src/main/kotlin")
    kotlin.srcDir("../feature/launcher/src/main/kotlin")
    kotlin.srcDir("../format/image/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../feature/notes/src/main/kotlin")
    kotlin.srcDir("../core/perf/src/main/kotlin")
    kotlin.srcDir("../feature/files/src/main/kotlin")
    kotlin.srcDir("../feature/together/src/main/kotlin")
    kotlin.include("**/AcceptLang.kt", "**/AlphaIndex.kt", "**/BackupName.kt", "**/ColLetters.kt", "**/CwdTilde.kt", "**/DebVersion.kt", "**/DisplayInch.kt", "**/Ean13.kt", "**/ExifOrient.kt", "**/Severity.kt", "**/GoToLine.kt", "**/GpsRational.kt", "**/HiddenDot.kt", "**/HistDedup.kt", "**/Issn.kt", "**/Levenshtein.kt", "**/ObjNum.kt", "**/PrefKeyLint.kt", "**/QuoteNest.kt", "**/SampleBytes.kt", "**/SessionCode.kt", "**/SheetHide.kt", "**/SheetRange.kt", "**/SriHash.kt", "**/SubjectNorm.kt", "**/TagExtract.kt", "**/UpcE.kt", "**/R44Checks.kt")
}
tasks.register<JavaExec>("r44Contracts") {
    dependsOn(r44.classesTaskName); classpath = r44.runtimeClasspath; mainClass.set("dev.fusion.testing.R44Checks")
    args(rootProject.file("tools/fixtures/r44").absolutePath)
}

// R34..R36 kernels backported to CI execution so R34..R45 form a contiguous 12-round Gradle lane.
val r34 by sourceSets.creating
kotlin.sourceSets.named("r34") {
    kotlin.srcDir("src/r34/kotlin")
    kotlin.srcDir("../cast/dlna/src/main/java")
    kotlin.srcDir("../core/commands/src/main/kotlin")
    kotlin.srcDir("../core/files/src/main/java")
    kotlin.srcDir("../feature/calc/src/main/kotlin")
    kotlin.srcDir("../feature/continuity/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../feature/notes/src/main/java")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../format/image/src/main/kotlin")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.include("**/BracketMatch.kt", "**/JpegSeg.kt", "**/MimeGuess.kt", "**/OutlineParse.kt", "**/PipePlan.kt", "**/Pseudonym.kt", "**/SoapFault.kt", "**/StatSummary.kt", "**/VTodoParse.kt", "**/ZlibHead.kt", "**/R34Checks.kt")
}
tasks.register<JavaExec>("r34Contracts") {
    dependsOn(r34.classesTaskName); classpath = r34.runtimeClasspath; mainClass.set("dev.fusion.testing.R34Checks")
    args(rootProject.file("tools/fixtures/r34").absolutePath)
}

val r35 by sourceSets.creating
kotlin.sourceSets.named("r35") {
    kotlin.srcDir("src/r35/kotlin")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../core/index/src/main/kotlin")
    kotlin.srcDir("../feature/continuity/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../feature/files/src/main/kotlin")
    kotlin.srcDir("../feature/lens/src/main/kotlin")
    kotlin.srcDir("../feature/maps/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../feature/vault/src/main/kotlin")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.include("**/DupGroup.kt", "**/EvShift.kt", "**/FieldMask.kt", "**/Id3Head.kt", "**/IndentDetect.kt", "**/SnippetCut.kt", "**/TileCover.kt", "**/TileMath.kt", "**/VCardParse.kt", "**/WordDiff.kt", "**/XorSplit.kt", "**/R35Checks.kt")
}
tasks.register<JavaExec>("r35Contracts") {
    dependsOn(r35.classesTaskName); classpath = r35.runtimeClasspath; mainClass.set("dev.fusion.testing.R35Checks")
    args(rootProject.file("tools/fixtures/r35").absolutePath)
}

val r36 by sourceSets.creating
kotlin.sourceSets.named("r36") {
    kotlin.srcDir("src/r36/kotlin")
    kotlin.srcDir("../cast/castv2/src/main/kotlin")
    kotlin.srcDir("../core/commands/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../feature/calc/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../feature/files/src/main/kotlin")
    kotlin.srcDir("../feature/lens/src/main/kotlin")
    kotlin.srcDir("../feature/maps/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../feature/vault/src/main/kotlin")
    kotlin.srcDir("../format/image/src/main/kotlin")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.include("**/AttemptWindow.kt", "**/CastState.kt", "**/DofNear.kt", "**/KAnon.kt", "**/NameClash.kt", "**/RedirPlan.kt", "**/RpnEval.kt", "**/ScaleBar.kt", "**/ShannonBits.kt", "**/TiffHead.kt", "**/WavHead.kt", "**/WrapCol.kt", "**/R36Checks.kt")
}
tasks.register<JavaExec>("r36Contracts") {
    dependsOn(r36.classesTaskName); classpath = r36.runtimeClasspath; mainClass.set("dev.fusion.testing.R36Checks")
    args(rootProject.file("tools/fixtures/r36").absolutePath)
}

// R45 kernels executed under real Gradle/AGP toolchain in CI (JVM lane).
val r45 by sourceSets.creating
kotlin.sourceSets.named("r45") {
    kotlin.srcDir("src/r45/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../format/lok/src/main/java")
    kotlin.srcDir("../format/native/src/main/kotlin")
    kotlin.srcDir("../feature/calc/src/main/kotlin")
    kotlin.srcDir("../feature/notes/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../core/hw/src/main/kotlin")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.srcDir("../app/src/main/java")
    kotlin.srcDir("../core/designsystem/src/main/kotlin")
    kotlin.srcDir("../core/windowing/src/main/kotlin")
    kotlin.srcDir("../core/files/src/main/kotlin")
    kotlin.srcDir("../feature/settings/src/main/kotlin")
    kotlin.srcDir("../format/psd/src/main/kotlin")
    kotlin.srcDir("../cast/dlna/src/main/kotlin")
    kotlin.srcDir("../cast/castv2/src/main/kotlin")
    kotlin.srcDir("../cast/airplay/src/main/kotlin")
    kotlin.srcDir("../feature/accessibility/src/main/kotlin")
    kotlin.srcDir("../feature/maps/src/main/kotlin")
    kotlin.srcDir("../feature/vault/src/main/kotlin")
    kotlin.srcDir("../feature/continuity/src/main/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
    kotlin.srcDir("../core/index/src/main/kotlin")
    kotlin.include(
        "**/BoundedXml.kt", "**/ColLetters.kt", "**/SheetRange.kt", "**/CellErr.kt",
        "**/FormulaEval.kt", "**/FormulaXlsx.kt", "**/DocxNumbering.kt", "**/DocxPackage.kt",
        "**/DashEducate.kt", "**/SmartQuote.kt", "**/ProseEducate.kt", "**/PieceTable.kt",
        "**/FindReplace.kt", "**/OdtExtract.kt", "**/RleBitset.kt", "**/FinMath.kt",
        "**/TocTree.kt", "**/CspHeader.kt", "**/PslGate.kt", "**/CtSctPolicy.kt",
        "**/DmaGuardAudit.kt", "**/HidReportAudit.kt", "**/TailsCadence.kt", "**/OverlayTouchGuard.kt",
        "**/TileLabelFit.kt", "**/SpringStep.kt", "**/WorkAreaFit.kt", "**/MerkleTree.kt",
        "**/EnvOverride.kt", "**/PsdLayerRecords.kt", "**/SsdpHeader.kt", "**/CastTxtProps.kt",
        "**/TxtRecord.kt", "**/KeyChord.kt", "**/UtmGrid.kt", "**/HkdfExpand.kt",
        "**/ClipMime.kt", "**/DagTopo.kt", "**/Bm25Rank.kt", "**/R45Checks.kt",
    )
}
tasks.register<JavaExec>("r45Contracts") {
    dependsOn(r45.classesTaskName); classpath = r45.runtimeClasspath; mainClass.set("dev.fusion.testing.R45Checks")
    args(rootProject.file("tools/fixtures/r45").absolutePath)
}

// R30..R33 backported so R30..R46 form a contiguous 17-round Gradle CI contract lane.
val r30 by sourceSets.creating
kotlin.sourceSets.named("r30") {
    kotlin.srcDir("src/r30/kotlin")
    kotlin.srcDir("../cast/airplay/src/main/kotlin")
    kotlin.srcDir("../core/model/src/main/kotlin")
    kotlin.srcDir("../feature/capture/src/main/kotlin")
    kotlin.srcDir("../feature/continuity/src/main/kotlin")
    kotlin.srcDir("../feature/lens/src/main/kotlin")
    kotlin.srcDir("../feature/messages/src/main/kotlin")
    kotlin.srcDir("../feature/settings/src/main/kotlin")
    kotlin.srcDir("../feature/together/src/main/kotlin")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../format/image/src/main/kotlin")
    kotlin.include("**/CalendarExport.kt", "**/DensitySelect.kt", "**/GifInfo.kt", "**/LamportClock.kt", "**/Models.kt", "**/PatchScope.kt", "**/RtspMessage.kt", "**/SettingsPatch.kt", "**/SmsSegments.kt", "**/TarHeader.kt", "**/Timecode.kt", "**/VEventParse.kt", "**/ZoomLadder.kt", "**/R30Checks.kt")
}
tasks.register<JavaExec>("r30Contracts") {
    dependsOn(r30.classesTaskName); classpath = r30.runtimeClasspath; mainClass.set("dev.fusion.testing.R30Checks")
    args(rootProject.file("tools/fixtures/r30").absolutePath)
}

val r31 by sourceSets.creating
kotlin.sourceSets.named("r31") {
    kotlin.srcDir("src/r31/kotlin")
    kotlin.srcDir("../core/capabilities/src/main/java")
    kotlin.srcDir("../core/hw/src/main/java")
    kotlin.srcDir("../core/index/src/main/kotlin")
    kotlin.srcDir("../core/model/src/main/kotlin")
    kotlin.srcDir("../core/perf/src/main/java")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../feature/vault/src/main/kotlin")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.srcDir("../format/psd/src/main/kotlin")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.include("**/CapabilityGate.kt", "**/CodecPreference.kt", "**/LogScrubber.kt", "**/Models.kt", "**/PdfTrailer.kt", "**/PhaseLedger.kt", "**/PsdResources.kt", "**/RobotsRules.kt", "**/SearchQuery.kt", "**/TextOffsets.kt", "**/VaultManifest.kt", "**/R31Checks.kt")
}
tasks.register<JavaExec>("r31Contracts") {
    dependsOn(r31.classesTaskName); classpath = r31.runtimeClasspath; mainClass.set("dev.fusion.testing.R31Checks")
    args(rootProject.file("tools/fixtures/r31").absolutePath)
}

val r32 by sourceSets.creating
kotlin.sourceSets.named("r32") {
    kotlin.srcDir("src/r32/kotlin")
    kotlin.srcDir("../cast/castv2/src/main/kotlin")
    kotlin.srcDir("../core/commands/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../feature/calc/src/main/kotlin")
    kotlin.srcDir("../feature/continuity/src/main/kotlin")
    kotlin.srcDir("../feature/maps/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../feature/vault/src/main/kotlin")
    kotlin.srcDir("../format/lok/src/main/java")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.include("**/ArgSplit.kt", "**/CastMediaMeta.kt", "**/CueSheet.kt", "**/FractionApprox.kt", "**/GeoUri.kt", "**/ICalDuration.kt", "**/LockoutPolicy.kt", "**/OfficeCapability.kt", "**/PinRules.kt", "**/RetentionSweep.kt", "**/R32Checks.kt")
}
tasks.register<JavaExec>("r32Contracts") {
    dependsOn(r32.classesTaskName); classpath = r32.runtimeClasspath; mainClass.set("dev.fusion.testing.R32Checks")
    args(rootProject.file("tools/fixtures/r32").absolutePath)
}

val r33 by sourceSets.creating
kotlin.sourceSets.named("r33") {
    kotlin.srcDir("src/r33/kotlin")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../feature/beam/src/main/kotlin")
    kotlin.srcDir("../feature/files/src/main/kotlin")
    kotlin.srcDir("../feature/find/src/main/kotlin")
    kotlin.srcDir("../feature/messages/src/main/kotlin")
    kotlin.srcDir("../feature/terminal/src/main/kotlin")
    kotlin.srcDir("../feature/together/src/main/kotlin")
    kotlin.srcDir("../feature/vault/src/main/kotlin")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../format/image/src/main/kotlin")
    kotlin.include("**/AnsiSgr.kt", "**/BmpInfo.kt", "**/ChunkPlan.kt", "**/DiffHunks.kt", "**/E164Format.kt", "**/FuzzyScore.kt", "**/GlobMatch.kt", "**/GzipHead.kt", "**/LineDiff.kt", "**/TextDocument.kt", "**/TextEncoding.kt", "**/TotpCode.kt", "**/VectorClock.kt", "**/R33Checks.kt")
}
tasks.register<JavaExec>("r33Contracts") {
    dependsOn(r33.classesTaskName); classpath = r33.runtimeClasspath; mainClass.set("dev.fusion.testing.R33Checks")
    args(rootProject.file("tools/fixtures/r33").absolutePath)
}

// R46 kernels executed under real Gradle/AGP toolchain in CI (JVM lane across 34 modules / 39 new kernels + FormulaEval).
val r46 by sourceSets.creating
kotlin.sourceSets.named("r46") {
    kotlin.srcDir("src/r46/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../format/lok/src/main/java")
    kotlin.srcDir("../core/capabilities/src/main/java")
    kotlin.srcDir("../core/perf/src/main/kotlin")
    kotlin.srcDir("../core/storage/src/main/kotlin")
    kotlin.srcDir("../core/commands/src/main/kotlin")
    kotlin.srcDir("../core/model/src/main/kotlin")
    kotlin.srcDir("../core/settings/src/main/kotlin")
    kotlin.srcDir("../format/native/src/main/kotlin")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.srcDir("../core/hw/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../app/src/main/java")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../feature/launcher/src/main/kotlin")
    kotlin.srcDir("../feature/find/src/main/kotlin")
    kotlin.srcDir("../feature/files/src/main/kotlin")
    kotlin.srcDir("../feature/terminal/src/main/kotlin")
    kotlin.srcDir("../feature/beam/src/main/kotlin")
    kotlin.srcDir("../feature/alerts/src/main/kotlin")
    kotlin.srcDir("../feature/capture/src/main/kotlin")
    kotlin.srcDir("../feature/backup/src/main/kotlin")
    kotlin.srcDir("../feature/together/src/main/kotlin")
    kotlin.srcDir("../feature/lens/src/main/kotlin")
    kotlin.srcDir("../feature/messages/src/main/kotlin")
    kotlin.srcDir("../format/image/src/main/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
    kotlin.srcDir("../feature/vault/src/main/kotlin")
    kotlin.srcDir("../feature/notes/src/main/kotlin")
    kotlin.include(
        "**/BoundedXml.kt", "**/ColLetters.kt", "**/SheetRange.kt", "**/CellErr.kt",
        "**/FormulaEval.kt", "**/MultiSheetBook.kt", "**/SheetPivot.kt", "**/CondFormat.kt",
        "**/PdfPageWriter.kt", "**/DocOutlineRender.kt", "**/TrackRevisions.kt",
        "**/CitationBib.kt", "**/OdsSheetExtract.kt", "**/WasmSandbox.kt", "**/QuotaBudget.kt",
        "**/WalFrame.kt", "**/PipeChain.kt", "**/SemVerRange.kt", "**/SchemaMigrate.kt",
        "**/ColumnarPage.kt", "**/TarPax.kt", "**/OnionGraterFilter.kt", "**/MetadataScrub.kt",
        "**/UsbGuardPolicy.kt", "**/BleHidPairingGate.kt", "**/MacRandomizer.kt", "**/KeystrokeJitter.kt",
        "**/PermissionsPolicyHeader.kt", "**/HlsPlaylist.kt", "**/DesktopEntryIni.kt",
        "**/TrigramIndex.kt", "**/PathConflict.kt", "**/VtScreenBuf.kt", "**/ChunkedTransfer.kt",
        "**/RateWindow.kt", "**/EdlCutList.kt", "**/RabinChunker.kt", "**/CrdtLwwMap.kt",
        "**/BarcodeGs1.kt", "**/QuotedPrintable.kt", "**/PngMetadataStrip.kt", "**/CronExpr.kt",
        "**/PassphraseDice.kt", "**/MarkdownTableFmt.kt", "**/R46Checks.kt",
    )
}
tasks.register<JavaExec>("r46Contracts") {
    dependsOn(r46.classesTaskName); classpath = r46.runtimeClasspath; mainClass.set("dev.fusion.testing.R46Checks")
    args(rootProject.file("tools/fixtures/r46").absolutePath)
}

// Close historical gap r26..r29 + register current R47 contracts for unbroken r26Contracts..r47Contracts (22 tasks).
val r26 by sourceSets.creating
kotlin.sourceSets.named("r26") {
    kotlin.srcDir("src/r26/kotlin")
    kotlin.srcDir("../core/settings/src/main/kotlin")
    kotlin.srcDir("../core/designsystem/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../feature/maps/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../format/native/src/main/kotlin")
    kotlin.include(
        "**/Appearance.kt", "**/Contrast.kt", "**/DesktopMetrics.kt", "**/ProfessionalPalettes.kt",
        "**/BeautyStandard.kt", "**/AmnesiaPolicy.kt", "**/TorPosture.kt", "**/UsbPolicy.kt",
        "**/KeypadLayout.kt", "**/ServiceLink.kt", "**/PlaylistParser.kt", "**/GpxTrack.kt",
        "**/SafeNavigation.kt", "**/SandboxJob.kt", "**/TsvTable.kt", "**/R26Checks.kt",
    )
}
tasks.register<JavaExec>("r26Contracts") {
    dependsOn(r26.classesTaskName); classpath = r26.runtimeClasspath; mainClass.set("dev.fusion.testing.R26Checks")
    args(rootProject.file("tools/fixtures/r26").absolutePath)
}

val r27 by sourceSets.creating
kotlin.sourceSets.named("r27") {
    kotlin.srcDir("src/r27/kotlin")
    kotlin.srcDir("../feature/launcher/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../feature/maps/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../core/settings/src/main/kotlin")
    kotlin.srcDir("../core/designsystem/src/main/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
    kotlin.include(
        "**/Tabletop.kt", "**/TabletopArrange.kt", "**/ServiceLink.kt", "**/PlaylistParser.kt",
        "**/PodcastFeed.kt", "**/StreamPolicy.kt", "**/GpxTrack.kt", "**/Gazetteer.kt",
        "**/SafeNavigation.kt", "**/ContentBlocker.kt", "**/MarkdownDoc.kt", "**/UsbPolicy.kt",
        "**/UsbInventory.kt", "**/FirewallPolicy.kt", "**/Appearance.kt", "**/Contrast.kt",
        "**/DesktopMetrics.kt", "**/ProfessionalPalettes.kt", "**/BeautyStandard.kt", "**/TypeScale.kt",
        "**/CronSpec.kt", "**/R27Checks.kt",
    )
}
tasks.register<JavaExec>("r27Contracts") {
    dependsOn(r27.classesTaskName); classpath = r27.runtimeClasspath; mainClass.set("dev.fusion.testing.R27Checks")
    args(rootProject.file("tools/fixtures/r27").absolutePath)
}

val r28 by sourceSets.creating
kotlin.sourceSets.named("r28") {
    kotlin.srcDir("src/r28/kotlin")
    kotlin.srcDir("../core/files/src/main/kotlin")
    kotlin.srcDir("../core/windowing/src/main/kotlin")
    kotlin.srcDir("../feature/find/src/main/kotlin")
    kotlin.srcDir("../feature/maps/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
    kotlin.srcDir("../feature/notes/src/main/kotlin")
    kotlin.srcDir("../feature/terminal/src/main/kotlin")
    kotlin.include(
        "**/TrashLedger.kt", "**/SnapZones.kt", "**/FrecencyLedger.kt", "**/TileMath.kt",
        "**/ChapterMarks.kt", "**/ConsentLedger.kt", "**/CookieJar.kt", "**/Recurrence.kt",
        "**/FrontMatter.kt", "**/Scrollback.kt", "**/R28Checks.kt",
    )
}
tasks.register<JavaExec>("r28Contracts") {
    dependsOn(r28.classesTaskName); classpath = r28.runtimeClasspath; mainClass.set("dev.fusion.testing.R28Checks")
    args(rootProject.file("tools/fixtures/r28").absolutePath)
}

val r29 by sourceSets.creating
kotlin.sourceSets.named("r29") {
    kotlin.srcDir("src/r29/kotlin")
    kotlin.srcDir("../feature/beam/src/main/kotlin")
    kotlin.srcDir("../core/storage/src/main/kotlin")
    kotlin.srcDir("../feature/alerts/src/main/kotlin")
    kotlin.srcDir("../feature/files/src/main/kotlin")
    kotlin.srcDir("../core/commands/src/main/kotlin")
    kotlin.srcDir("../feature/accessibility/src/main/kotlin")
    kotlin.srcDir("../core/settings/src/main/kotlin")
    kotlin.srcDir("../feature/backup/src/main/kotlin")
    kotlin.srcDir("../cast/dlna/src/main/java")
    kotlin.srcDir("../cast/dlna/src/main/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../app/src/main/java")
    kotlin.include(
        "**/ShareManifest.kt", "**/QuotaLedger.kt", "**/AlertThrottle.kt", "**/FileSort.kt",
        "**/AliasTable.kt", "**/ContrastRatio.kt", "**/LocaleMatch.kt", "**/RetentionPlan.kt",
        "**/UpnpParsing.kt", "**/DidlLite.kt", "**/CsvTable.kt", "**/LiteralWorkbook.kt",
        "**/CsvDialect.kt", "**/UsbAllowlist.kt", "**/AndroidHostStubs.kt", "**/R29Checks.kt",
    )
}
tasks.register<JavaExec>("r29Contracts") {
    dependsOn(r29.classesTaskName); classpath = r29.runtimeClasspath; mainClass.set("dev.fusion.testing.R29Checks")
    args(rootProject.file("tools/fixtures/r29").absolutePath)
}

// R47 kernels executed under real Gradle/AGP toolchain in CI (49 new pure-JVM kernels + FormulaEval 61-fn across 48/50 modules).
val r47 by sourceSets.creating
kotlin.sourceSets.named("r47") {
    kotlin.srcDir("src/r47/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.srcDir("../format/lok/src/main/java")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../feature/calc/src/main/kotlin")
    kotlin.srcDir("../feature/notes/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/java")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../core/capabilities/src/main/java")
    kotlin.srcDir("../core/model/src/main/kotlin")
    kotlin.srcDir("../core/perf/src/main/java")
    kotlin.srcDir("../core/storage/src/main/java")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../core/settings/src/main/java")
    kotlin.srcDir("../privacy/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/java")
    kotlin.srcDir("../core/hw/src/main/java")
    kotlin.srcDir("../feature/accessibility/src/main/java")
    kotlin.srcDir("../app/src/main/java")
    kotlin.srcDir("../core/designsystem/src/main/java")
    kotlin.srcDir("../feature/launcher/src/main/java")
    kotlin.srcDir("../feature/files/src/main/java")
    kotlin.srcDir("../feature/terminal/src/main/java")
    kotlin.srcDir("../feature/media/src/main/java")
    kotlin.srcDir("../feature/maps/src/main/java")
    kotlin.srcDir("../feature/backup/src/main/java")
    kotlin.srcDir("../feature/together/src/main/java")
    kotlin.srcDir("../feature/lens/src/main/java")
    kotlin.srcDir("../feature/messages/src/main/java")
    kotlin.srcDir("../feature/settings/src/main/java")
    kotlin.srcDir("../core/files/src/main/java")
    kotlin.srcDir("../format/native/src/main/kotlin")
    kotlin.srcDir("../format/image/src/main/java")
    kotlin.srcDir("../format/psd/src/main/java")
    kotlin.srcDir("../cast/dlna/src/main/java")
    kotlin.srcDir("../cast/airplay/src/main/kotlin")
    kotlin.srcDir("../cast/castv2/src/main/kotlin")
    kotlin.srcDir("../core/commands/src/main/kotlin")
    kotlin.srcDir("../core/index/src/main/kotlin")
    kotlin.srcDir("../core/windowing/src/main/kotlin")
    kotlin.srcDir("../feature/alerts/src/main/java")
    kotlin.srcDir("../feature/automation/src/main/java")
    kotlin.srcDir("../feature/beam/src/main/java")
    kotlin.srcDir("../feature/capture/src/main/java")
    kotlin.srcDir("../feature/continuity/src/main/java")
    kotlin.srcDir("../feature/find/src/main/java")
    kotlin.srcDir("../feature/vault/src/main/java")
    kotlin.srcDir("../benchmark/src/main/java")
    kotlin.include(
        "**/BoundedXml.kt", "**/ColLetters.kt", "**/SheetRange.kt", "**/CellErr.kt", "**/FormulaEval.kt",
        "**/DocxRichDocument.kt", "**/XlsxStyledBook.kt", "**/PdfOutlineBookmarks.kt", "**/OdtRichWriter.kt",
        "**/LatexMathSvg.kt", "**/DocumentDiffMerge.kt", "**/SvgChartPlot.kt", "**/WikiLinkGraph.kt",
        "**/HtmlSanitizer.kt", "**/WasiPreview1Host.kt", "**/AdblockFilterEngine.kt", "**/JsonPatchRfc6902.kt",
        "**/ArgvSpecParser.kt", "**/HdrLatencyHist.kt", "**/BTreePageIndex.kt", "**/ZipSafeExtract.kt",
        "**/TomlConfigCodec.kt", "**/TorSocksIsolation.kt", "**/ImuSideChannelGuard.kt",
        "**/SecureKeypadScrambler.kt", "**/ClipboardSensitiveGuard.kt", "**/ImeTrustGate.kt",
        "**/RamWipeSchedule.kt", "**/ScreenReaderRedactor.kt", "**/SecureSurfacePolicy.kt",
        "**/FluidTypeClamp.kt", "**/FuzzyAppSearch.kt", "**/BooleanQueryAst.kt", "**/ExtendedGlob.kt",
        "**/PromptOsc133.kt", "**/WebVttCueParser.kt", "**/GeoJsonFeature.kt", "**/ReedSolomonGf256.kt",
        "**/MaintenanceSilence.kt", "**/WavPcmHeader.kt", "**/PackIndexCompact.kt", "**/NoiseFraming.kt",
        "**/RgaTextSequence.kt", "**/MrzIcao9303.kt", "**/DkimHeaderCanon.kt", "**/RetryBackoff.kt",
        "**/VarIntZigZag.kt", "**/ArArchive.kt", "**/SvgSafeSubset.kt", "**/IccProfileHeader.kt",
        "**/SoapActionEnvelope.kt", "**/RtspRangeHeader.kt", "**/CastMediaStatus.kt", "**/MicrobenchStats.kt",
        "**/AirPlayTxtRecord.kt", "**/CastChannelFrame.kt", "**/UndoRedoStack.kt", "**/Bm25Scorer.kt",
        "**/TilingBinaryTree.kt", "**/EwmaAnomalyDetector.kt", "**/DagTaskRunner.kt", "**/ContentRangeHttp.kt",
        "**/SmpteTimecode.kt", "**/HandoffSessionTicket.kt", "**/BoyerMooreHorspool.kt", "**/ShamirSecretGf256.kt",
        "**/R47Checks.kt",
    )
}
tasks.register<JavaExec>("r47Contracts") {
    dependsOn(r47.classesTaskName); classpath = r47.runtimeClasspath; mainClass.set("dev.fusion.testing.R47Checks")
    args(rootProject.file("tools/fixtures/r47").absolutePath)
}

// R48 66 new pure-JVM kernels + 2 pure-JVM touched upgrades across all 50/50 Gradle modules.
val r48 by sourceSets.creating
kotlin.sourceSets.named("r48") {
    kotlin.srcDir("src/r48/kotlin")
    kotlin.srcDir("src/main/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.srcDir("../format/lok/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../feature/calc/src/main/kotlin")
    kotlin.srcDir("../feature/notes/src/main/kotlin")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.srcDir("../core/hw/src/main/java")
    kotlin.srcDir("../core/security/src/main/java")
    kotlin.srcDir("../feature/vault/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/java")
    kotlin.srcDir("../core/capabilities/src/main/java")
    kotlin.srcDir("../app/src/main/java")
    kotlin.srcDir("../core/windowing/src/main/kotlin")
    kotlin.srcDir("../core/files/src/main/kotlin")
    kotlin.srcDir("../core/model/src/main/kotlin")
    kotlin.srcDir("../core/commands/src/main/kotlin")
    kotlin.srcDir("../core/settings/src/main/kotlin")
    kotlin.srcDir("../core/perf/src/main/kotlin")
    kotlin.srcDir("../core/index/src/main/kotlin")
    kotlin.srcDir("../core/storage/src/main/kotlin")
    kotlin.srcDir("../core/designsystem/src/main/kotlin")
    kotlin.srcDir("../feature/launcher/src/main/kotlin")
    kotlin.srcDir("../feature/find/src/main/java")
    kotlin.srcDir("../feature/files/src/main/kotlin")
    kotlin.srcDir("../feature/terminal/src/main/kotlin")
    kotlin.srcDir("../feature/settings/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../feature/maps/src/main/kotlin")
    kotlin.srcDir("../feature/beam/src/main/kotlin")
    kotlin.srcDir("../feature/alerts/src/main/kotlin")
    kotlin.srcDir("../feature/capture/src/main/kotlin")
    kotlin.srcDir("../feature/backup/src/main/kotlin")
    kotlin.srcDir("../feature/continuity/src/main/kotlin")
    kotlin.srcDir("../feature/accessibility/src/main/kotlin")
    kotlin.srcDir("../feature/together/src/main/kotlin")
    kotlin.srcDir("../feature/lens/src/main/kotlin")
    kotlin.srcDir("../feature/messages/src/main/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
    kotlin.srcDir("../format/native/src/main/kotlin")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../format/image/src/main/kotlin")
    kotlin.srcDir("../format/psd/src/main/kotlin")
    kotlin.srcDir("../cast/dlna/src/main/kotlin")
    kotlin.srcDir("../cast/airplay/src/main/kotlin")
    kotlin.srcDir("../cast/castv2/src/main/kotlin")
    kotlin.srcDir("../benchmark/src/main/java")
    kotlin.srcDir("../baselineprofile/src/main/kotlin")
    kotlin.include(
        "**/ColLetters.kt", "**/SheetRange.kt", "**/CellErr.kt", "**/FormulaEval.kt",
        "**/SpillSheetEngine.kt", "**/PptxSlideDeck.kt", "**/PivotCubeSummary.kt", "**/XlsxDataValidationXml.kt",
        "**/Epub3Package.kt", "**/RtfCleanConverter.kt", "**/OpenSearchSuggestionJson.kt",
        "**/PdfAcroFormWriter.kt", "**/PdfPageLabelsAndOutline.kt", "**/OdsSpreadsheetXml.kt",
        "**/ReadabilityMetrics.kt", "**/MarkdownOfficeBridge.kt", "**/FinancialScheduleSolver.kt",
        "**/MatrixLinearAlgebra.kt", "**/ZettelkastenFolgezettel.kt", "**/BinaryHttpRfc9292.kt",
        "**/ObliviousHttpPolicy.kt", "**/OnionV3Address.kt", "**/AcousticKeystrokeShield.kt",
        "**/ExitTrafficPadBucket.kt", "**/HidUsageFirewall.kt", "**/ScreenCaptureGuard.kt",
        "**/ConstantTimePrimitives.kt", "**/HkdfRfc5869.kt", "**/PasskeyRpValidator.kt",
        "**/ArticleReaderExtractor.kt", "**/CrossOriginIsolationPolicy.kt", "**/CspHeaderDirective.kt",
        "**/WasmSandbox.kt", "**/WasiVirtualPipe.kt", "**/WorkspaceSessionSnapshot.kt",
        "**/SnapZoneMagneticRouter.kt", "**/ContentHashDeduplicator.kt", "**/VersionedSchemaEnvelope.kt",
        "**/KeybindingChordTrie.kt", "**/PolicyLockdownMerger.kt", "**/JankFrameClassifier.kt",
        "**/RoaringBitSetLite.kt", "**/BTreePageLayout.kt", "**/AdaptiveTypographyScale.kt",
        "**/FrecencyAppRanker.kt", "**/RegexSafeAutomaton.kt", "**/SafeBatchRenamePlanner.kt",
        "**/TmuxPaneLayoutCodec.kt", "**/PermissionRiskAuditor.kt", "**/WebVttCueTimeline.kt",
        "**/GeoFencePolygon.kt", "**/ReedSolomonQrEcc.kt", "**/CronExpressionSchedule.kt",
        "**/PiiRedactionScanner.kt", "**/ChunkedRestoreVerifier.kt", "**/VectorClockCausalOrder.kt",
        "**/ReadingOrderLinearizer.kt", "**/YataRgbaSequenceCrdt.kt", "**/PerspectiveHomography3x3.kt",
        "**/DoubleRatchetHeaderChain.kt", "**/WorkflowPetriNet.kt", "**/Elf16KbPageAudit.kt",
        "**/SevenZipSignatureHeader.kt", "**/NetpbmPpmCodec.kt", "**/PsdColorModeResources.kt",
        "**/DidlLiteMetadataXml.kt", "**/RtpAudioPacketHeader.kt", "**/CastMediaStatusFrame.kt",
        "**/WelchTTestRegression.kt", "**/ArtProfileRuleCodec.kt", "**/DeterministicShrinker.kt",
        "**/R48Checks.kt",
    )
}
tasks.register<JavaExec>("r48Contracts") {
    dependsOn(r48.classesTaskName); classpath = r48.runtimeClasspath; mainClass.set("dev.fusion.testing.R48Checks")
    args(rootProject.file("testing/build/r48/artifacts").absolutePath)
}

// R49 kernels (75 new + 3 upgraded pure-JVM engines across 50/50 modules) executed under real Gradle/AGP toolchain in CI (JVM lane).
val r49 by sourceSets.creating
kotlin.sourceSets.named("r49") {
    kotlin.srcDir("src/r49/kotlin")
    kotlin.srcDir("src/main/kotlin")
    kotlin.srcDir("../app/src/main/java")
    kotlin.srcDir("../core/windowing/src/main/kotlin")
    kotlin.srcDir("../core/files/src/main/java")
    kotlin.srcDir("../core/model/src/main/kotlin")
    kotlin.srcDir("../core/commands/src/main/kotlin")
    kotlin.srcDir("../core/settings/src/main/java")
    kotlin.srcDir("../core/perf/src/main/java")
    kotlin.srcDir("../core/hw/src/main/java")
    kotlin.srcDir("../core/security/src/main/java")
    kotlin.srcDir("../core/index/src/main/kotlin")
    kotlin.srcDir("../core/storage/src/main/java")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../core/designsystem/src/main/java")
    kotlin.srcDir("../core/capabilities/src/main/java")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.srcDir("../feature/launcher/src/main/java")
    kotlin.srcDir("../feature/find/src/main/java")
    kotlin.srcDir("../feature/files/src/main/java")
    kotlin.srcDir("../feature/terminal/src/main/java")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../feature/notes/src/main/java")
    kotlin.srcDir("../feature/calc/src/main/java")
    kotlin.srcDir("../feature/browser/src/main/java")
    kotlin.srcDir("../feature/vault/src/main/java")
    kotlin.srcDir("../feature/media/src/main/java")
    kotlin.srcDir("../feature/maps/src/main/java")
    kotlin.srcDir("../feature/beam/src/main/java")
    kotlin.srcDir("../feature/alerts/src/main/java")
    kotlin.srcDir("../feature/capture/src/main/java")
    kotlin.srcDir("../feature/backup/src/main/java")
    kotlin.srcDir("../feature/settings/src/main/java")
    kotlin.srcDir("../feature/continuity/src/main/java")
    kotlin.srcDir("../feature/accessibility/src/main/java")
    kotlin.srcDir("../feature/together/src/main/java")
    kotlin.srcDir("../feature/lens/src/main/java")
    kotlin.srcDir("../feature/messages/src/main/java")
    kotlin.srcDir("../feature/automation/src/main/java")
    kotlin.srcDir("../format/native/src/main/kotlin")
    kotlin.srcDir("../format/lok/src/main/java")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.srcDir("../format/image/src/main/java")
    kotlin.srcDir("../format/psd/src/main/java")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../cast/dlna/src/main/java")
    kotlin.srcDir("../cast/airplay/src/main/kotlin")
    kotlin.srcDir("../cast/castv2/src/main/kotlin")
    kotlin.srcDir("../benchmark/src/main/java")
    kotlin.srcDir("../baselineprofile/src/main/kotlin")
    kotlin.include(
        "**/ColLetters.kt", "**/SheetRange.kt", "**/CellErr.kt", "**/FormulaEval.kt", "**/SpillSheetEngine.kt",
        "**/KnuthPlassLineBreaker.kt", "**/LiangHyphenationEngine.kt", "**/MultiColumnPagePaginator.kt", "**/OdfTrackedChangesCodec.kt",
        "**/MailMergeTemplateEngine.kt", "**/DocumentCompareDiff.kt", "**/BibliographyCitationEngine.kt",
        "**/StickyNoteBoardLayout.kt", "**/MarkdownSlideExporter.kt",
        "**/DrawingMlChartBuilder.kt", "**/SheetSparklineRenderer.kt", "**/SheetSolverSimplex.kt", "**/OoxmlMacroVbaInspector.kt",
        "**/FormatsRegistryCatalog.kt", "**/ExportFidelityAdvisor.kt", "**/ZipAesExtraFieldCodec.kt", "**/FormatSandboxWatchdog.kt",
        "**/LokTileRenderCache.kt", "**/HeicIsomBoxInspector.kt", "**/AiPdfCompatibilityBridge.kt",
        "**/PdfFormAcroFieldInspector.kt", "**/PdfPageTextSearchHighlighter.kt", "**/PdfPkcs7SignatureVerifier.kt",
        "**/WebRtcIceCandidateScrubber.kt", "**/BrowserSplitTabWorkspace.kt", "**/SitePermissionVault.kt", "**/FindInPageMatchEngine.kt",
        "**/WasmSandbox.kt", "**/WasiSocketCapabilityGate.kt", "**/AppFunctionsSchemaRegistry.kt",
        "**/TlsEchConfigCodec.kt", "**/ChoiceJackingUsbGuard.kt", "**/PixnappingSurfaceShield.kt", "**/TailsCaTrustPinBlocklist.kt",
        "**/VpnAppFirewallPolicy.kt", "**/PowerDvfsTimingMitigator.kt", "**/ModuleAppLockGate.kt",
        "**/ExternalMouseButtonMapper.kt", "**/UsbDataSignalingController.kt", "**/TinkAeadKeysetEnvelope.kt",
        "**/BlackTopSessionCoordinator.kt", "**/MultiDisplayWindowBroker.kt", "**/JournaledFileOpsPlanner.kt", "**/ArchiveDocumentsProviderTree.kt",
        "**/BillingEntitlementLedger.kt", "**/ThirdPartyIntentCommandGate.kt", "**/ManagedRestrictionsSchema.kt",
        "**/AdpfThermalRefreshPolicy.kt", "**/IndexChurnQuarantineMonitor.kt", "**/ConfigExportBundleCodec.kt", "**/FocusRingPointerTokens.kt",
        "**/WidgetHostIsolationSupervisor.kt", "**/PaletteInlineAnswerEngine.kt", "**/DuplicateFileClusterer.kt",
        "**/OemSettingsDeepLinkCatalog.kt", "**/VtTrueColorSafePasteEngine.kt", "**/BaseRadixBitfieldInspector.kt",
        "**/AudioFocusVolumeMatrix.kt", "**/OfflineRouteTurnSynthesizer.kt", "**/CastPrivacyExclusionGate.kt",
        "**/NotificationDiagnosticLedger.kt", "**/CaptureBitrateBudgetPlanner.kt", "**/BackupHealthDrillScheduler.kt",
        "**/KdeConnectPacketCodec.kt", "**/WebVttCaptionCueTimeline.kt", "**/SharedClockSyncCoordinator.kt",
        "**/LensThermalQualityLadder.kt", "**/VirtualRelightNormalMap.kt", "**/RcsHandoffUriBuilder.kt", "**/CalendarIcsRecurrenceEngine.kt",
        "**/SsdpDiscoveryResponseParser.kt", "**/RaopRtpSyncPacketizer.kt", "**/CastWebReceiverSessionModel.kt",
        "**/StartupFrameP99Analyzer.kt", "**/BaselineProfileRuleSynthesizer.kt", "**/AdversarialMutationCorpus.kt",
        "**/R49Checks.kt",
    )
}
tasks.register<JavaExec>("r49Contracts") {
    dependsOn(r49.classesTaskName); classpath = r49.runtimeClasspath; mainClass.set("dev.fusion.testing.R49Checks")
    args(rootProject.file("testing/build/r49/artifacts").absolutePath)
}

// R50 kernels (80 new + 4 upgraded pure-JVM engines across 50/50 modules) executed under real Gradle/AGP toolchain in CI (JVM lane).
val r50 by sourceSets.creating
kotlin.sourceSets.named("r50") {
    kotlin.srcDir("src/r50/kotlin")
    kotlin.srcDir("src/main/kotlin")
    kotlin.srcDir("../app/src/main/java")
    kotlin.srcDir("../baselineprofile/src/main/kotlin")
    kotlin.srcDir("../benchmark/src/main/java")
    kotlin.srcDir("../cast/airplay/src/main/kotlin")
    kotlin.srcDir("../cast/castv2/src/main/kotlin")
    kotlin.srcDir("../cast/dlna/src/main/java")
    kotlin.srcDir("../core/capabilities/src/main/java")
    kotlin.srcDir("../core/commands/src/main/kotlin")
    kotlin.srcDir("../core/designsystem/src/main/java")
    kotlin.srcDir("../core/documents/src/main/java")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../core/files/src/main/java")
    kotlin.srcDir("../core/hw/src/main/java")
    kotlin.srcDir("../core/index/src/main/kotlin")
    kotlin.srcDir("../core/model/src/main/kotlin")
    kotlin.srcDir("../core/perf/src/main/java")
    kotlin.srcDir("../core/security/src/main/java")
    kotlin.srcDir("../core/settings/src/main/java")
    kotlin.srcDir("../core/storage/src/main/java")
    kotlin.srcDir("../core/windowing/src/main/kotlin")
    kotlin.srcDir("../feature/accessibility/src/main/java")
    kotlin.srcDir("../feature/alerts/src/main/java")
    kotlin.srcDir("../feature/automation/src/main/java")
    kotlin.srcDir("../feature/backup/src/main/java")
    kotlin.srcDir("../feature/beam/src/main/java")
    kotlin.srcDir("../feature/browser/src/main/java")
    kotlin.srcDir("../feature/calc/src/main/java")
    kotlin.srcDir("../feature/capture/src/main/java")
    kotlin.srcDir("../feature/continuity/src/main/java")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../feature/files/src/main/java")
    kotlin.srcDir("../feature/find/src/main/java")
    kotlin.srcDir("../feature/launcher/src/main/java")
    kotlin.srcDir("../feature/lens/src/main/java")
    kotlin.srcDir("../feature/maps/src/main/java")
    kotlin.srcDir("../feature/media/src/main/java")
    kotlin.srcDir("../feature/messages/src/main/java")
    kotlin.srcDir("../feature/notes/src/main/java")
    kotlin.srcDir("../feature/settings/src/main/java")
    kotlin.srcDir("../feature/terminal/src/main/java")
    kotlin.srcDir("../feature/together/src/main/java")
    kotlin.srcDir("../feature/vault/src/main/java")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../format/image/src/main/java")
    kotlin.srcDir("../format/lok/src/main/java")
    kotlin.srcDir("../format/native/src/main/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.srcDir("../format/psd/src/main/java")
    kotlin.srcDir("../privacy/src/main/java")
    kotlin.include(
        "**/BoundedXml.kt", "**/ColLetters.kt", "**/SheetRange.kt", "**/CellErr.kt", "**/FormulaEval.kt", "**/SpillSheetEngine.kt", "**/MultiSheetBook.kt", "**/WasmSandbox.kt",
        "**/PivotCacheXmlExporter.kt", "**/ConditionalDataBarScaleEngine.kt", "**/OpenTypeKerningTableLayout.kt", "**/MasterSlideTransitionDeck.kt",
        "**/OutlineTocCrossRefEngine.kt", "**/MermaidFlowchartSvgLayout.kt", "**/NewFileTemplateCatalog.kt", "**/Mat2RecursiveMetadataScrubber.kt",
        "**/ThumbnailMetadataIndexer.kt", "**/OklchPaletteContrastPicker.kt", "**/PsdBlendAdjustmentCompositor.kt", "**/PdfIncrementalSaveRedactor.kt",
        "**/EasyListTrackerFilterEngine.kt", "**/BrowserProfileDirectoryIsolator.kt", "**/BrowserTabShortcutBatchBroker.kt", "**/WebAppWindowManifestHost.kt",
        "**/TorPluggableTransportConfig.kt", "**/BluetoothBluffsSessionGuard.kt", "**/WifiProbeSequenceScrambler.kt", "**/PrivacyPostureDashboardEngine.kt",
        "**/NfcRelayDistanceBoundingGate.kt", "**/ImeIncognitoClipboardFirewall.kt", "**/TempestDisplayJitterFilter.kt", "**/GamepadPointerModeMapper.kt",
        "**/HardwareKeyboardImeSwitcher.kt", "**/ShamirSecretShareSplitter.kt", "**/AppInventoryLifecycleAdvisor.kt", "**/WorkspaceContextProfileEngine.kt",
        "**/ExternalAppBrokerDensityPin.kt", "**/KeyToTouchOverlayMapper.kt", "**/RemoteMountReconnectPlanner.kt", "**/CrossWindowClipUriGrantor.kt",
        "**/ContactsVcard4PickerModel.kt", "**/ShortcutPresetConflictDetector.kt", "**/DefaultAppsRoleGuideEngine.kt", "**/MeteredBatteryLowRamScheduler.kt",
        "**/OptInWebAppSearchFederation.kt", "**/ImapEnvelopeTriageStore.kt", "**/OsdQuickPanelTileTokens.kt", "**/PinnedWebShortcutShelfModel.kt",
        "**/CommandPaletteFuzzyActionRanker.kt", "**/DualPaneContextMenuModel.kt", "**/KeyboardShortcutProfileEditor.kt", "**/SshTransportKexFrameCodec.kt",
        "**/UnitMatrixSolverWorkbench.kt", "**/FocusModeContextEvaluator.kt", "**/VoiceRecorderWavWaveformCodec.kt", "**/Mp4KeyframeTrimPlanner.kt",
        "**/Fsr1EasuRcasUpscaler.kt", "**/Cube3dLutColorGrader.kt", "**/NlmsAcousticEchoGate.kt", "**/PerspectiveDeskewBinarizer.kt",
        "**/FoldPosturePresenterStudio.kt", "**/VirtualWebcamMjpegStream.kt", "**/MatrixMegolmGroupRatchet.kt", "**/XmppOmemoStanzaSeal.kt",
        "**/UnifiedPushSmsFallbackRouter.kt", "**/SharedFolderDeltaSyncPlanner.kt", "**/RemoteDesktopRfbTileCodec.kt", "**/TogetherCoWatchSyncClock.kt",
        "**/PresentationSpeakerNotesDualView.kt", "**/CastSinkSrpPinVerifier.kt", "**/DlnaDidlLitePlaylistQueue.kt", "**/WorkflowJsonImportExportSandbox.kt",
        "**/ColorBlindnessDaltonizeMatrix.kt", "**/FocusRingDwellClickController.kt", "**/EasySimpleModeLayoutScaler.kt", "**/OfflinePmtilesHilbertDirectory.kt",
        "**/BeamChunkedResumeReedSolomon.kt", "**/ScrollingCaptureSeamStitcher.kt", "**/ResticCdcChunkDeduplicator.kt", "**/R50LatencyPercentileBudgetGate.kt",
        "**/ArtStartupProfileRuleSynthesizer.kt", "**/R50CrossKernelTranscriptVerifier.kt", "**/OoxmlSparklineSvgRenderer.kt", "**/WriterTrackChangesDiffMerger.kt",
        "**/PdfPageBookmarkSplitMerger.kt", "**/DnsOverHttpsWireQueryCodec.kt", "**/ClearUrlTrackingParamStripper.kt", "**/UsbBadHidKeystrokeRateGuard.kt",
        "**/R50Checks.kt",
    )
}
tasks.register<JavaExec>("r50Contracts") {
    dependsOn(r50.classesTaskName); classpath = r50.runtimeClasspath; mainClass.set("dev.fusion.testing.R50Checks")
    args(rootProject.file("testing/build/r50/artifacts").absolutePath)
}

// R51 kernels executed under real Gradle/AGP toolchain in CI (JVM lane, 50/50 modules, 85 new + 5 touched pure-JVM kernels).
val r51 by sourceSets.creating
kotlin.sourceSets.named("r51") {
    kotlin.srcDir("src/r51/kotlin")
    kotlin.srcDir("src/main/kotlin")
    kotlin.srcDir("../app/src/main/java")
    kotlin.srcDir("../baselineprofile/src/main/kotlin")
    kotlin.srcDir("../benchmark/src/main/java")
    kotlin.srcDir("../cast/airplay/src/main/kotlin")
    kotlin.srcDir("../cast/castv2/src/main/kotlin")
    kotlin.srcDir("../cast/dlna/src/main/kotlin")
    kotlin.srcDir("../core/capabilities/src/main/java")
    kotlin.srcDir("../core/commands/src/main/kotlin")
    kotlin.srcDir("../core/designsystem/src/main/kotlin")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../core/files/src/main/kotlin")
    kotlin.srcDir("../core/hw/src/main/kotlin")
    kotlin.srcDir("../core/index/src/main/kotlin")
    kotlin.srcDir("../core/model/src/main/kotlin")
    kotlin.srcDir("../core/perf/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../core/settings/src/main/kotlin")
    kotlin.srcDir("../core/storage/src/main/kotlin")
    kotlin.srcDir("../core/windowing/src/main/kotlin")
    kotlin.srcDir("../feature/accessibility/src/main/kotlin")
    kotlin.srcDir("../feature/alerts/src/main/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
    kotlin.srcDir("../feature/backup/src/main/kotlin")
    kotlin.srcDir("../feature/beam/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../feature/calc/src/main/kotlin")
    kotlin.srcDir("../feature/capture/src/main/kotlin")
    kotlin.srcDir("../feature/continuity/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../feature/files/src/main/kotlin")
    kotlin.srcDir("../feature/find/src/main/kotlin")
    kotlin.srcDir("../feature/launcher/src/main/kotlin")
    kotlin.srcDir("../feature/lens/src/main/kotlin")
    kotlin.srcDir("../feature/maps/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../feature/messages/src/main/kotlin")
    kotlin.srcDir("../feature/notes/src/main/kotlin")
    kotlin.srcDir("../feature/settings/src/main/kotlin")
    kotlin.srcDir("../feature/terminal/src/main/kotlin")
    kotlin.srcDir("../feature/together/src/main/kotlin")
    kotlin.srcDir("../feature/vault/src/main/kotlin")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../format/image/src/main/kotlin")
    kotlin.srcDir("../format/lok/src/main/java")
    kotlin.srcDir("../format/native/src/main/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.srcDir("../format/psd/src/main/kotlin")
    kotlin.srcDir("../privacy/src/main/kotlin")
    kotlin.include(
        "**/BoundedXml.kt", "**/ColLetters.kt", "**/SheetRange.kt", "**/CellErr.kt",
        "**/FormulaEval.kt", "**/SpillSheetEngine.kt", "**/DocxRichDocument.kt", "**/OdtRichWriter.kt", "**/WasmSandbox.kt",
        "**/StructuredTableRefEngine.kt", "**/R1C1FormulaTranslator.kt", "**/CalcChainDependencyGraph.kt", "**/PptxMasterLayoutAnimationEngine.kt",
        "**/GoalSeekSimplexSolver.kt", "**/PivotSlicerCrossFilterEngine.kt", "**/WidowOrphanGalleyPaginator.kt", "**/CitationBibliographyFormatter.kt",
        "**/OpenTypeKerningLineJustifier.kt", "**/SpreadsheetNumberMaskFormatter.kt", "**/PdfUa1TaggedStructureWriter.kt", "**/PdfLinearizedHintValidator.kt",
        "**/OdsSpreadsheetFormulaBridge.kt", "**/ColumnarArrowIpcBatchCodec.kt", "**/CssFlexBlockBoxLayouter.kt", "**/ChipsPartitionedCookieJar.kt",
        "**/PermissionsPolicyHeaderEvaluator.kt", "**/DeclarativeNetRequestTrieEngine.kt", "**/WasmSimdModuleVerifier.kt", "**/WitComponentCanonicalAbiCodec.kt",
        "**/RaopAlacAudioPacketizer.kt", "**/AirPlayFairPlayStubRefusalGate.kt", "**/CastV2MediaQueueController.kt", "**/CastVirtualDisplayBoundsScaler.kt",
        "**/DidlLiteXmlMetadataBuilder.kt", "**/WebVttCueTimelineEngine.kt", "**/EbuR128LoudnessNormalizer.kt", "**/ExifGpsPrivacyScrubber.kt",
        "**/OklabPerceptualPaletteQuantizer.kt", "**/PsdSmartPlacedLayerInspector.kt", "**/TorVanguardsLiteMesh.kt", "**/AmnesicSessionShutdownOrchestrator.kt",
        "**/PanicDeadManSwitchEngine.kt", "**/MetadataCleanRoomExportPipeline.kt", "**/Luks2Argon2idHeaderAuditor.kt", "**/AgeV1StreamCipherEnvelope.kt",
        "**/DramRowhammerZeroizeGuard.kt", "**/OpticalReflectionSideChannelGuard.kt", "**/CapacitiveGhostTouchFilter.kt", "**/Usb4PcieIommuDmaGuard.kt",
        "**/HallMagneticCoverTamperGuard.kt", "**/DuressDecoyVaultRouter.kt", "**/ShamirSecretVaultSplitter.kt", "**/DeterministicReproducibleTarGzWriter.kt",
        "**/ArchiveBombQuotaInspector.kt", "**/RestorableChunkDedupManifest.kt", "**/CrossDeviceClipboardSyncEngine.kt", "**/AudioOutputHandoffCoordinator.kt",
        "**/DocumentDewarpPerspectiveEngine.kt", "**/BarcodeSymbologyDecoder.kt", "**/DoubleRatchetSessionEngine.kt", "**/SealedSenderPaddingEnvelope.kt",
        "**/StartupColdWarmRegressionGate.kt", "**/MemoryPressureTrimBudgeter.kt", "**/FusionClassAReadinessAggregator.kt", "**/WorkspaceDeepLinkIntentSanitizer.kt",
        "**/SnapTileMagneticLayoutSolver.kt", "**/FocusZOrderModalStack.kt", "**/AtomicSafeWriteJournal.kt", "**/ExtendedMimeMagicSniffer.kt",
        "**/Bm25FtsPositionalIndex.kt", "**/TrigramFuzzySpellSuggester.kt", "**/Vt100AlternateScreenScrollback.kt", "**/ShellPipelineTokenizer.kt",
        "**/SpacedRepetitionFsrsScheduler.kt", "**/MarkdownTableFormatterEngine.kt", "**/VectorClockCausalRevision.kt", "**/FuzzyChordCommandPaletteEngine.kt",
        "**/TransactionalSettingsMigrationEngine.kt", "**/LsmWriteAheadCompactionSim.kt", "**/ApcaContrastTypographyScale.kt", "**/PinnedWorkspaceDockLayoutEngine.kt",
        "**/BooleanQueryAstEvaluator.kt", "**/BatchRenamePatternPreviewEngine.kt", "**/PrivacyPostureScorecardCalculator.kt", "**/ReedSolomonQrChunkFountain.kt",
        "**/DndPriorityScheduleEvaluator.kt", "**/ScrollingStitchSeamMatcher.kt", "**/WcagTouchTargetSemanticAuditor.kt", "**/YjsRgaTextCrdtEngine.kt",
        "**/CronDagWorkflowScheduler.kt", "**/RTreeSpatialGeofenceIndex.kt", "**/SimdSpreadsheetThroughputHarness.kt", "**/ArtStartupMethodRuleOptimizer.kt",
        "**/DeterministicPropertyShrinker.kt", "**/R51Checks.kt",
    )
}
tasks.register<JavaExec>("r51Contracts") {
    dependsOn(r51.classesTaskName); classpath = r51.runtimeClasspath; mainClass.set("dev.fusion.testing.R51Checks")
    args(rootProject.file("testing/build/r51/artifacts").absolutePath)
}

// R52 kernels executed under real Gradle/AGP toolchain in CI (JVM lane, 50/50 modules, 91 new + 6 touched pure-JVM kernels).
val r52 by sourceSets.creating
kotlin.sourceSets.named("r52") {
    kotlin.srcDir("src/r52/kotlin")
    kotlin.srcDir("src/main/kotlin")
    kotlin.srcDir("../app/src/main/java")
    kotlin.srcDir("../core/files/src/main/kotlin")
    kotlin.srcDir("../core/index/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../feature/capture/src/main/kotlin")
    kotlin.srcDir("../feature/files/src/main/kotlin")
    kotlin.srcDir("../core/windowing/src/main/kotlin")
    kotlin.srcDir("../core/designsystem/src/main/kotlin")
    kotlin.srcDir("../core/commands/src/main/kotlin")
    kotlin.srcDir("../feature/files/src/main/java")
    kotlin.srcDir("../feature/launcher/src/main/kotlin")
    kotlin.srcDir("../core/model/src/main/kotlin")
    kotlin.srcDir("../core/settings/src/main/kotlin")
    kotlin.srcDir("../feature/settings/src/main/kotlin")
    kotlin.srcDir("../core/perf/src/main/kotlin")
    kotlin.srcDir("../core/storage/src/main/kotlin")
    kotlin.srcDir("../feature/accessibility/src/main/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../format/lok/src/main/java")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.srcDir("../format/native/src/main/kotlin")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../format/image/src/main/kotlin")
    kotlin.srcDir("../format/psd/src/main/kotlin")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../feature/calc/src/main/kotlin")
    kotlin.srcDir("../feature/notes/src/main/kotlin")
    kotlin.srcDir("../feature/terminal/src/main/kotlin")
    kotlin.srcDir("../core/capabilities/src/main/java")
    kotlin.srcDir("../privacy/src/main/kotlin")
    kotlin.srcDir("../core/hw/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../feature/vault/src/main/kotlin")
    kotlin.srcDir("../feature/find/src/main/kotlin")
    kotlin.srcDir("../feature/maps/src/main/kotlin")
    kotlin.srcDir("../feature/beam/src/main/kotlin")
    kotlin.srcDir("../feature/alerts/src/main/kotlin")
    kotlin.srcDir("../feature/backup/src/main/kotlin")
    kotlin.srcDir("../feature/continuity/src/main/kotlin")
    kotlin.srcDir("../feature/together/src/main/kotlin")
    kotlin.srcDir("../feature/lens/src/main/kotlin")
    kotlin.srcDir("../feature/messages/src/main/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
    kotlin.srcDir("../cast/dlna/src/main/kotlin")
    kotlin.srcDir("../cast/airplay/src/main/kotlin")
    kotlin.srcDir("../cast/castv2/src/main/kotlin")
    kotlin.srcDir("../benchmark/src/main/java")
    kotlin.srcDir("../baselineprofile/src/main/kotlin")
    kotlin.include(
        "**/RecallSessionCoordinator.kt", "**/ShellKeyboardShortcutRegistry.kt", "**/RecallFolderDelineator.kt", "**/RecallPlaylistSidecarWriter.kt",
        "**/RecallMediaTranscriptSearchIndex.kt", "**/KeysetCursorPaginator.kt", "**/RecallTier1DirectAssetHarvester.kt", "**/RecallTier2HlsDashAssembler.kt",
        "**/RecallDashMpdSegmentResolver.kt", "**/RecallMethodEscalationRouter.kt", "**/RecallHarArchiveExporter.kt", "**/BounceTrackingRedirectPurger.kt",
        "**/CleanUrlTrackingParamStripper.kt", "**/RecallIsoBmffFragmentMuxer.kt", "**/RecallTier3CanvasWebAudioSiphoner.kt", "**/RecallWebVttCaptionArchiver.kt",
        "**/Id3v24MusicTagEmbedder.kt", "**/FlacStreamInfoVorbisInspector.kt", "**/RecallTier4NativeScreenAudioRecorder.kt", "**/RecallCaptureRolloverPlanner.kt",
        "**/RecallArchiveCatalog.kt", "**/BatchFileRenameCollisionPlanner.kt", "**/RightClickContextMenuGeometry.kt", "**/PipCornerOcclusionPlacementSolver.kt",
        "**/SecondaryClickGestureClassifier.kt", "**/FusionViewModeVectorIcons.kt", "**/AnalyticalSpringMotionSolver.kt", "**/ShoulderSurfingPrivacyVeil.kt",
        "**/StackedUndoNotificationDeck.kt", "**/UndoRedoStack.kt", "**/ReversibleBatchCommandJournal.kt", "**/MillerColumnsTreeNavigator.kt",
        "**/DualPaneContextMenuModel.kt", "**/LauncherViewModeShelfPlanner.kt", "**/LauncherFrecencyRanker.kt", "**/Rfc8785JsonCanonicalizer.kt",
        "**/HumanByteSizeCodec.kt", "**/DatastoreSchemaMigrationChain.kt", "**/PredictiveBackOverlayStack.kt", "**/LocalFrameJankHistogram.kt",
        "**/SegmentedLruStorageQuotaGovernor.kt", "**/SqliteWalFrameIntegrityVerifier.kt", "**/WcagTargetSpacingFocusAuditor.kt", "**/BoundedXml.kt",
        "**/ColLetters.kt", "**/SheetRange.kt", "**/CellErr.kt", "**/FormulaEval.kt",
        "**/SpillSheetEngine.kt", "**/PptxSlideDeck.kt", "**/OoxmlSparklineEngine.kt", "**/DocxContentControlBinder.kt",
        "**/XlsxAutoFilterEvaluator.kt", "**/SlideShapeSnapDistributor.kt", "**/OdfMasterPageHeaderFooterEngine.kt", "**/OdfTrackedChangesRedlineEngine.kt",
        "**/LokCommandStateSynchronizer.kt", "**/PdfBookmarkOutlineWriter.kt", "**/PdfAcroFormFieldFiller.kt", "**/PdfBookletImpositionPlanner.kt",
        "**/IsoHeifAvifContainerProbe.kt", "**/Epub33PackageManifestBuilder.kt", "**/TextureAtlasShelfPacker.kt", "**/PsdSeparableBlendCompositor.kt",
        "**/DocsBibliographyCiteFormatter.kt", "**/DocumentCrossRefResolver.kt", "**/WriterMailMergeEngine.kt", "**/WriterFootnotePaginator.kt",
        "**/MultiCaretBlockEditor.kt", "**/SheetSolverSensitivityEngine.kt", "**/ConditionalFormattingScaleEvaluator.kt", "**/NotesFlashcardSrsScheduler.kt",
        "**/ZettelkastenBacklinkGraph.kt", "**/MarkdownPipeTablePrettyFormatter.kt", "**/WasmSandbox.kt", "**/WasmTailCallVerifier.kt",
        "**/CapabilityDelegationAttenuator.kt", "**/TorOnionPowIntroQueue.kt", "**/TailsAmnesicSwapCoreAuditor.kt", "**/ChargerSurfingPowerMasker.kt",
        "**/UltrasonicDolphinAttackFilter.kt", "**/ThermalResidueWipeAdvisor.kt", "**/ImuKeystrokeMotionZeroizer.kt", "**/Rfc5869HkdfSha256.kt",
        "**/VaultPassphraseEntropyAuditor.kt", "**/LocalBpeSubwordTokenizer.kt", "**/MapsElevationTravelEstimator.kt", "**/BeamMerkleChunkVerifier.kt",
        "**/HubWorkspaceFeedAggregator.kt", "**/GearRollingChunkSlicer.kt", "**/ContinuityHybridLogicalClock.kt", "**/ThreeWayDocumentLineMerger.kt",
        "**/LensQrPayloadSemanticGuard.kt", "**/MessagingSymmetricChainRatchet.kt", "**/OfflineMmrExtractiveSummarizer.kt", "**/CastSinkCodecNegotiator.kt",
        "**/SlideRehearsalPacingCoach.kt", "**/HttpRangeResumePlanner.kt", "**/BenchmarkWelchRegressionGate.kt", "**/BaselineProfileRuleConsolidator.kt",
        "**/DeterministicFuzzShrinker.kt", "**/R52Checks.kt",
    )
}
tasks.register<JavaExec>("r52Contracts") {
    dependsOn(r52.classesTaskName); classpath = r52.runtimeClasspath; mainClass.set("dev.fusion.testing.R52Checks")
    args(rootProject.file("testing/build/r52/artifacts").absolutePath)
}

// R53 kernels executed under real Gradle/AGP toolchain in CI (JVM lane, 50/50 modules, 96 new + 5 touched pure-JVM kernels).
val r53 by sourceSets.creating
kotlin.sourceSets.named("r53") {
    kotlin.srcDir("src/r53/kotlin")
    kotlin.srcDir("src/main/kotlin")
    kotlin.srcDir("../app/src/main/java")
    kotlin.srcDir("../core/files/src/main/kotlin")
    kotlin.srcDir("../core/index/src/main/kotlin")
    kotlin.srcDir("../feature/browser/src/main/kotlin")
    kotlin.srcDir("../feature/media/src/main/kotlin")
    kotlin.srcDir("../feature/capture/src/main/kotlin")
    kotlin.srcDir("../feature/files/src/main/kotlin")
    kotlin.srcDir("../core/windowing/src/main/kotlin")
    kotlin.srcDir("../core/designsystem/src/main/kotlin")
    kotlin.srcDir("../core/commands/src/main/kotlin")
    kotlin.srcDir("../feature/files/src/main/java")
    kotlin.srcDir("../feature/launcher/src/main/kotlin")
    kotlin.srcDir("../core/model/src/main/kotlin")
    kotlin.srcDir("../core/settings/src/main/kotlin")
    kotlin.srcDir("../feature/settings/src/main/kotlin")
    kotlin.srcDir("../core/perf/src/main/kotlin")
    kotlin.srcDir("../core/storage/src/main/kotlin")
    kotlin.srcDir("../feature/accessibility/src/main/kotlin")
    kotlin.srcDir("../format/ooxml/src/main/kotlin")
    kotlin.srcDir("../format/lok/src/main/java")
    kotlin.srcDir("../format/pdf/src/main/java")
    kotlin.srcDir("../format/native/src/main/kotlin")
    kotlin.srcDir("../format/archive/src/main/kotlin")
    kotlin.srcDir("../format/image/src/main/kotlin")
    kotlin.srcDir("../format/psd/src/main/kotlin")
    kotlin.srcDir("../core/documents/src/main/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.srcDir("../feature/calc/src/main/kotlin")
    kotlin.srcDir("../feature/notes/src/main/kotlin")
    kotlin.srcDir("../feature/terminal/src/main/kotlin")
    kotlin.srcDir("../core/capabilities/src/main/java")
    kotlin.srcDir("../privacy/src/main/kotlin")
    kotlin.srcDir("../core/hw/src/main/kotlin")
    kotlin.srcDir("../core/security/src/main/kotlin")
    kotlin.srcDir("../feature/vault/src/main/kotlin")
    kotlin.srcDir("../feature/find/src/main/kotlin")
    kotlin.srcDir("../feature/maps/src/main/kotlin")
    kotlin.srcDir("../feature/beam/src/main/kotlin")
    kotlin.srcDir("../feature/alerts/src/main/kotlin")
    kotlin.srcDir("../feature/backup/src/main/kotlin")
    kotlin.srcDir("../feature/continuity/src/main/kotlin")
    kotlin.srcDir("../feature/together/src/main/kotlin")
    kotlin.srcDir("../feature/lens/src/main/kotlin")
    kotlin.srcDir("../feature/messages/src/main/kotlin")
    kotlin.srcDir("../feature/automation/src/main/kotlin")
    kotlin.srcDir("../cast/dlna/src/main/kotlin")
    kotlin.srcDir("../cast/airplay/src/main/kotlin")
    kotlin.srcDir("../cast/castv2/src/main/kotlin")
    kotlin.srcDir("../benchmark/src/main/java")
    kotlin.srcDir("../baselineprofile/src/main/kotlin")
    kotlin.include(
        "**/RecallSessionCoordinator.kt", "**/RecallAutoArchivalPipelineOrchestrator.kt", "**/ShellMultiMonitorDpiScaleRouter.kt",
        "**/CleanUrlTrackingParamStripper.kt", "**/RecallMethodEscalationRouter.kt", "**/RecallTier25WebRtcRtpDepacketizer.kt",
        "**/RecallTier5WarcIso28500Archiver.kt", "**/RecallMhtmlMultipartSnapshotter.kt", "**/RecallSrcsetPictureDensitySelector.kt",
        "**/BrowserStorageAccessPartitionGuard.kt", "**/ClearSiteDataHeaderExecutor.kt", "**/RecallAudioSilenceTrackSplitter.kt",
        "**/Mp3MpegFrameDurationXingScanner.kt", "**/OggVorbisCommentChapterEmbedder.kt", "**/MatroskaWebmEbmlClusterProbe.kt",
        "**/LoudnessEbuR128MomentaryMeter.kt", "**/RecallViewportCursorAnnotationCompositor.kt", "**/CaptureSegmentConcatManifestBuilder.kt",
        "**/AudioCapturePolicyClassifier.kt", "**/RecallFolderDelineator.kt", "**/RecallVaultQuotaLifecyclePruner.kt",
        "**/RecallCdxjReplayIndexCodec.kt", "**/RecallPerceptualDHashDeduplicator.kt", "**/PositionalPhraseInvertedIndex.kt",
        "**/ContentAddressedBlobCasStore.kt", "**/RingBufferLockFreeTelemetry.kt", "**/Rfc8785JsonCanonicalizer.kt",
        "**/DeterministicUlidGenerator.kt", "**/RecallUserPreferencesSchema.kt", "**/BoundedXml.kt",
        "**/ColLetters.kt", "**/SheetRange.kt", "**/CellErr.kt", "**/FormulaEval.kt", "**/SpillSheetEngine.kt",
        "**/DocxTableLayoutGridEngine.kt", "**/DocxEquationOmmlBuilder.kt", "**/XlsxPivotCacheRecordsWriter.kt",
        "**/PptxMasterThemeColorSchemeWriter.kt", "**/OdfPresentationOdpBuilder.kt", "**/OdfSpreadsheetFormulaTranslator.kt",
        "**/LokTileInvalidationCoalescer.kt", "**/PdfDigitalSignatureByteRangePlanner.kt", "**/PdfTextColumnReflowExtractor.kt",
        "**/PdfOptionalContentLayersWriter.kt", "**/PngChunkSanitizerAndCrcCodec.kt", "**/JpegExifAppMarkerSanitizer.kt",
        "**/WarcGzipConcatMemberInspector.kt", "**/EpubCfiFragmentLocator.kt", "**/Gltf2BinaryGlbChunkInspector.kt",
        "**/PsdImageResourceIccExtractor.kt", "**/WriterSectionColumnsPageLayout.kt", "**/WriterTableOfFiguresIndexBuilder.kt",
        "**/WriterTrackChangesAcceptRejectEngine.kt", "**/SyntaxHighlightLexicalScanner.kt", "**/SpreadsheetGoalSeekBrentSolver.kt",
        "**/MonteCarloSensitivityTableRunner.kt", "**/NotesCanvasInfiniteGraphLayout.kt", "**/MarkdownFootnoteAndCalloutParser.kt",
        "**/AnsiSgrTruecolorScrollbackBuffer.kt", "**/TerminalOsc52ClipboardSanitizer.kt", "**/WasmSandbox.kt",
        "**/WasmMultiMemoryIsolationVerifier.kt", "**/SeccompBpfSyscallAllowlistPlanner.kt", "**/TorConfluxMultipathScheduler.kt",
        "**/TailsMetadataAnonymizer.kt", "**/OnionServiceClientAuthDescriptorCodec.kt", "**/TempestSubpixelEmsecDitherer.kt",
        "**/BleHogpKeystrokeFirewall.kt", "**/VerifiedBootVbmetaDigestAuditor.kt", "**/AmbientLightKeystrokeShadowGuard.kt",
        "**/DramRowhammerRefreshMonitor.kt", "**/Slip39MnemonicShareChecksumCodec.kt", "**/ChaCha20Rfc8439StreamBlock.kt",
        "**/VaultEmergencyDuressDecoyPlanner.kt", "**/TrigramFuzzyCommandMatcher.kt", "**/DouglasPeuckerTrackSimplifier.kt",
        "**/MdnsServiceTxtPrivacySanitizer.kt", "**/SensitiveOtpNotificationRedactor.kt", "**/RabinKarpDeltaPatchGenerator.kt",
        "**/ClipboardAutoExpireSanitizer.kt", "**/CausalTreeAwarenessPresenceRoster.kt", "**/DocumentSkewQuadRectifier.kt",
        "**/SealedCertificateBucketEnvelope.kt", "**/CronNextFireScheduleEvaluator.kt", "**/UpnpSoapActionEnvelopeSanitizer.kt",
        "**/RaopRtpTimingSyncCalculator.kt", "**/CastV2FramingHeaderCodec.kt", "**/MannWhitneyNonParametricGate.kt",
        "**/StartupDexLayoutHotnessScorer.kt", "**/DeterministicFuzzShrinker.kt", "**/DifferentialBytecodeMutationOracle.kt",
        "**/RightClickContextMenuGeometry.kt", "**/ContextMenuSubmenuCascadeGeometry.kt", "**/PointerSafeTriangleSubmenuFilter.kt",
        "**/DesktopWorkAreaInsetPartitioner.kt", "**/FusionViewModeVectorIcons.kt", "**/ViewModePixelGridCrispHinting.kt",
        "**/TranslucentGlassBackdropContrastGuard.kt", "**/UndoRedoStack.kt", "**/StackedUndoNotificationDeck.kt",
        "**/UndoDeckInteractiveFanController.kt", "**/ContextMenuMnemonicKeyNavigator.kt", "**/DualPaneContextMenuModel.kt",
        "**/MillerColumnsTreeNavigator.kt", "**/DualPaneViewModeSyncStore.kt", "**/ContextMenuSelectionVerbResolver.kt",
        "**/ShelfAppCategoryColumnsModel.kt", "**/UiErgonomicsPreferencesValidator.kt", "**/ReducedTransparencyAndMotionAdapter.kt",
        "**/BrowserLinkMediaContextMenuPlanner.kt", "**/MediaCollectionViewModeGrouper.kt", "**/VaultItemViewModeOrganizer.kt",
        "**/NotesCollectionViewModeSorter.kt", "**/EditorSelectionContextMenuPlanner.kt", "**/R53Checks.kt",
    )
}
tasks.register<JavaExec>("r53Contracts") {
    dependsOn(r53.classesTaskName); classpath = r53.runtimeClasspath; mainClass.set("dev.fusion.testing.R53Checks")
    args(rootProject.file("testing/build/r53/artifacts").absolutePath)
}

// R54: actual bounded office authoring and copy contracts. Android/provider gates are separate.
val r54 by sourceSets.creating
kotlin.sourceSets.named("r54") {
    kotlin.srcDir("src/r54/kotlin")
    kotlin.srcDir("../feature/editor/src/main/java")
    kotlin.include("**/R54Checks.kt", "**/MarkdownOfficeBridge.kt")
}
dependencies {
    add(r54.implementationConfigurationName, project(":format:ooxml"))
    add(r54.implementationConfigurationName, project(":core:documents"))
}
tasks.register<JavaExec>("r54Contracts") {
    dependsOn(r54.classesTaskName)
    classpath = r54.runtimeClasspath
    mainClass.set("dev.fusion.testing.R54Checks")
    args(rootProject.file("testing/build/r54/artifacts").absolutePath)
}

// R55: typed blank-reference behavior and a finite independent-comparison corpus.
val r55 by sourceSets.creating
kotlin.sourceSets.named("r55") { kotlin.srcDir("src/r55/kotlin") }
dependencies { add(r55.implementationConfigurationName, project(":format:ooxml")) }
tasks.register<JavaExec>("r55Contracts") {
    dependsOn(r55.classesTaskName)
    classpath = r55.runtimeClasspath
    mainClass.set("dev.fusion.testing.R55Checks")
    args(rootProject.file("testing/build/r55/artifacts").absolutePath)
}

// R56: finite ROUND and typed authoring/cached-XLSX contracts; independent checks are separate.
val r56 by sourceSets.creating
kotlin.sourceSets.named("r56") { kotlin.srcDir("src/r56/kotlin") }
dependencies { add(r56.implementationConfigurationName, project(":format:ooxml")) }
tasks.register<JavaExec>("r56Contracts") {
    dependsOn(r56.classesTaskName)
    classpath = r56.runtimeClasspath
    mainClass.set("dev.fusion.testing.R56Checks")
    args(rootProject.file("testing/build/r56/artifacts").absolutePath)
}

// R57: bounded opt-in Writer conversion. Independent reader and device tests remain separate.
val r57 by sourceSets.creating
kotlin.sourceSets.named("r57") { kotlin.srcDir("src/r57/kotlin") }
dependencies { add(r57.implementationConfigurationName, project(":format:ooxml")) }
tasks.register<JavaExec>("r57Contracts") {
    dependsOn(r57.classesTaskName)
    classpath = r57.runtimeClasspath
    mainClass.set("dev.fusion.testing.R57Checks")
    args(rootProject.file("testing/build/r57/artifacts").absolutePath)
}
