package com.cooper.homelab.ephyra.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A test that builds a view model makes it through [ViewModelTeardown], so its scope is stopped and finished before Main is reset.
 * One that does not leaves coroutines running into the next test in the same JVM, and a random test after it fails with
 * `UncaughtExceptionsBeforeTest` (#493, #816). This reads the test sources, so the next such test fails here, by name, rather than
 * as a flake somewhere else.
 */
class ViewModelTeardownConventionTest {
    /** Tests that stop their own view models, and why. Nothing else belongs here. */
    private val ownTeardown = setOf("JellyfinViewModelTest.kt")

    private val viewModelBuilt = Regex("""\b[A-Za-z]+ViewModel\(""")

    @Test
    fun `every test that builds a view model uses the shared teardown`() {
        val root = File("src/test/kotlin")
        // Unit tests run from the module's directory. From anywhere else there would be nothing to read, and the rule would pass unchecked.
        assertTrue("No test sources at ${root.absolutePath}", root.isDirectory)
        val offenders =
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name !in ownTeardown && it.name != "ViewModelTeardownConventionTest.kt" }
                .filter { file ->
                    val text = file.readText()
                    viewModelBuilt.containsMatchIn(text) && "ViewModelTeardown" !in text
                }.map { it.name }
                .sorted()
                .toList()
        assertEquals("These build a view model without ViewModelTeardown: $offenders", emptyList<String>(), offenders)
    }
}
