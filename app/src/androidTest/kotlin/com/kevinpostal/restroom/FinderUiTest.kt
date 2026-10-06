package com.kevinpostal.restroom

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/// Drives the app on the emulator with the `uitest` fixtures (no network, no real location).
@RunWith(AndroidJUnit4::class)
class FinderUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val wait = 5_000L

    private fun launch(vararg flags: String) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra("uitest", true)
        flags.forEach { intent.putExtra(it, true) }
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun tearDown() { scenario?.close() }

    private fun waitForTag(tag: String) = compose.waitUntil(wait) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun hasTestTag(tag: String) = SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag)
    private fun hasState(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun rowsListedWithCodeAndDistance() {
        launch()
        waitForTag("row.1")
        compose.onNodeWithTag("row.1")
            .assert(hasContentDescription("Happy Lemon", substring = true))
            .assert(hasContentDescription("0.2 miles", substring = true))
            .assert(hasContentDescription("Door code 2 5 8 0", substring = true))
        compose.onNodeWithTag("row.-5").performScrollTo()
            .assert(hasContentDescription("Memorial Park", substring = true))
            .assert(hasContentDescription("Park, usually has restrooms", substring = true))
    }

    @Test
    fun tapRowShowsCard() {
        launch()
        waitForTag("row.2")
        compose.onNodeWithTag("row.2").performClick()
        waitForTag("detail.code")
        compose.onNodeWithTag("detail.code").assertIsDisplayed().assert(hasText("Ask staff for code"))
        compose.onNodeWithTag("detail.name").assert(hasText("Kaiser Hospital"))
    }

    @Test
    fun refreshShowsSearchingTile() {
        launch("uitest_slow")
        waitForTag("map.loading")
        compose.onNodeWithTag("header.refresh").assert(hasState("Loading"))
        waitForTag("row.1")
        compose.waitUntil(wait) { compose.onAllNodes(hasTestTag("map.loading")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("header.refresh").performClick()
        waitForTag("map.loading")
        compose.onNodeWithTag("row.1").assertIsDisplayed()   // rows stay while refreshing
    }
}
