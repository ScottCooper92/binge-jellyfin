package com.cooper.binge.companion.jellyfin

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A test that leaves work pending on Compose's shared UI dispatcher when its rule disposes breaks later tests in the same
 * JVM, depending on order (binge-design-system#1067, binge-jellyfin#308): Robolectric discards the pending work and the
 * dispatcher never runs anything again, so an activity recreation, a state restoration or a paged list fails later, or reads
 * stale state, in a test that has nothing wrong with it. The take-down rules drain it. So a plain compose rule in these tests
 * is a hazard that CI cannot see until some other test falls over.
 */
class ComposeRuleGuardTest {
    /**
     * This file, which names the rules it forbids, and the one test that must not use a rule that owns the Activity: it recreates
     * the Activity under an `ActivityController` and drains the looper itself (see its KDoc).
     */
    private val allowed = setOf("ComposeRuleGuardTest.kt", "AddRepositoryRecreationTest.kt")

    private val plainRule =
        Regex("""\b(createComposeRule|createAndroidComposeRule|createEmptyComposeRule|createKeyboardComposeRule)\b""")

    @Test
    fun `no test builds a plain compose rule`() {
        val repoRoot = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val offenders =
            File(repoRoot, "app/src/test")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name !in allowed }
                .filter { plainRule.containsMatchIn(it.readText()) }
                .map { it.relativeTo(repoRoot).path }
                .toList()

        assertTrue(
            "Use createTakeDownAndroidComposeRule<ComponentActivity>() (its `scenario` recreates the Activity), not a plain " +
                "compose rule (binge-jellyfin#308), in: $offenders",
            offenders.isEmpty(),
        )
    }
}
