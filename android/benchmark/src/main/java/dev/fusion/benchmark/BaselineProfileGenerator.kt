package dev.fusion.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual startup + read-only Files open/close, NOT the unexercised Settings/Notes journeys. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()
    @Before fun consent() = LabJourney.requireDisposableEmulator()
    // [U] verify on API 34/36/37; this experiment does not certify ART behavior on every OEM.
    @Test fun generate() = rule.collect(packageName = LabJourney.TARGET) {
        pressHome()
        startActivityAndWait()
        LabJourney.filesOpenClose(device)
    }
}
