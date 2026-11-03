package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.binge.designsystem.testing.TakeDownComposeRule

/**
 * The design system's take-down rule for a test that makes its Activity again, as a rotation does. The design system's own
 * activity rule keeps the scenario to itself; a test that recreates the Activity needs it, so this one hands it out. It
 * takes its content down before the rule disposes it, like [TakeDownComposeRule], so nothing is left pending on Compose's
 * shared dispatcher for a later test.
 */
class RecreatableTakeDownRule private constructor(
    private val inner: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) : ComposeContentTestRule by TakeDownComposeRule(inner) {
    constructor() : this(createAndroidComposeRule<ComponentActivity>())

    val activity: ComponentActivity get() = inner.activity

    /** The scenario the Activity runs in: `scenario.recreate()` makes the Activity again, with what it saved. */
    val scenario get() = inner.activityRule.scenario
}
