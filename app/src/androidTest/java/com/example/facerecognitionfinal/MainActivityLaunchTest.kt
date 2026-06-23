package com.example.facerecognitionfinal

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test

class MainActivityLaunchTest {

    @get:Rule
    val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    fun launchesMainConsoleWithPrimaryActions() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            onView(withId(R.id.librarySummaryText)).check(matches(isDisplayed()))
            onView(withId(R.id.enrollButton)).check(matches(isDisplayed()))
            onView(withId(R.id.recognizeButton)).check(matches(isDisplayed()))
            onView(withId(R.id.liveRecognitionButton)).check(matches(isDisplayed()))
            onView(withId(R.id.settingsButton)).check(matches(isDisplayed()))
        } finally {
            scenario.close()
        }
    }
}
