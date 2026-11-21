package com.cooper.homelab.ephyra.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A test that drives thread-confined state does not make `Dispatchers.Unconfined` its Main. The save engine behind a page that
 * saves as made, and the search's copies of a configuration, run everything but the write on the view model's scope thread, and
 * with Unconfined as Main that work resumes on whichever thread finished the last step. A stale write then lands late and the test
 * waits for a state it can no longer reach (#848, #918). Such a test sets [MainLooperDispatcher], waits with `idling { }`, and closes
 * through `ViewModelTeardown.close(jellyfin, idle = ::idleMain)`.
 *
 * Tests that touch no such class may keep Unconfined, so nothing here is an allowlist: the classes it guards are read from the
 * production sources, and a new settings view model that saves as made joins them by itself.
 */
class MainDispatcherConventionTest {
    @Test
    fun `no test that drives confined state sets Unconfined as Main`() {
        val repoRoot = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val main = File(repoRoot, "app/src/main/kotlin")
        val tests = File(repoRoot, "app/src/test/kotlin")
        // Unit tests run from the module's directory. From anywhere else there would be nothing to read, and the rule would pass unchecked.
        assertTrue("No sources at ${main.absolutePath}", main.isDirectory && tests.isDirectory)
        val confined =
            main
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { confinedClasses(it.readText()) }
                .toSet()
        // The set is read from the sources, so a change to how they spell the engine would empty it and the rule would pass unchecked.
        assertTrue("No confined classes found in the production sources: $confined", confined.size >= MIN_CONFINED)
        val offenders =
            tests
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "MainDispatcherConventionTest.kt" }
                .flatMap { file -> unconfinedMains(file.readText(), confined).map { "${file.name}:${it.line} drives ${it.driving}" } }
                .sorted()
                .toList()
        assertEquals(
            "These set Dispatchers.Unconfined as Main and drive thread-confined state. Use MainLooperDispatcher with idling { } " +
                "and ViewModelTeardown.close(jellyfin, idle = ::idleMain) (#848): $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `a test with Unconfined as Main that names a confined class is caught, at the line that sets it`() {
        val source =
            """
            class SomeTest {
                @Before
                fun main() {
                    Dispatchers.setMain(Dispatchers.Unconfined)
                }

                private fun vm() = made(NetworkViewModel(connection))
            }
            """.trimIndent()
        assertEquals(listOf(Unconfined("NetworkViewModel", 4)), unconfinedMains(source, setOf("NetworkViewModel", "SearchConfigCopy")))
    }

    @Test
    fun `looper Main, or Unconfined without a confined class, is clean`() {
        val confined = setOf("NetworkViewModel")
        val looper = "fun main() { Dispatchers.setMain(MainLooperDispatcher) }\nval vm = NetworkViewModel::class"
        val unrelated = "fun main() { Dispatchers.setMain(Dispatchers.Unconfined) }\nval vm = HomeTabViewModel::class"
        assertEquals(emptyList<Unconfined>(), unconfinedMains(looper, confined))
        assertEquals(emptyList<Unconfined>(), unconfinedMains(unrelated, confined))
    }

    @Test
    fun `a mention in a comment or a longer name is not a use of the class`() {
        val source =
            """
            // NetworkViewModel is the one this does not drive
            fun main() { Dispatchers.setMain(Dispatchers.Unconfined) }
            val other = SubNetworkViewModelFactory()
            """.trimIndent()
        assertEquals(emptyList<Unconfined>(), unconfinedMains(source, setOf("NetworkViewModel")))
    }

    @Test
    fun `the classes a file declares are its view models and the engines, when it names a save engine`() {
        val source =
            """
            internal class NetworkViewModel(private val saves: ConfigurationSaves)
            sealed interface NetworkUiState
            class SearchConfigCopy
            """.trimIndent()
        assertEquals(setOf("NetworkViewModel", "SearchConfigCopy", "ConfigurationSaves"), confinedClasses(source))
        assertEquals(emptySet<String>(), confinedClasses("class PlainViewModel(val x: Int)"))
    }

    private companion object {
        /** Fewer than this many found means the sources are no longer read the way this expects. */
        const val MIN_CONFINED = 5
    }
}

/** A test source that sets Unconfined as Main at [line] (from 1) while naming the confined class [driving]. */
internal data class Unconfined(
    val driving: String,
    val line: Int,
)

private val setsUnconfined = Regex("""setMain\(\s*Dispatchers\.Unconfined\s*\)""")
private val saveEngines = Regex("""\b(ConfigurationSaves|SaveAsMade|SearchConfigCopy)\b""")
private val declaredViewModel = Regex("""(?m)^\s*(?:[a-z]+\s+)*(?:class|object)\s+([A-Za-z0-9]+ViewModel)\b""")
private val blockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
private val lineComment = Regex("""(?m)(^|\s)//[^\n]*""")

/**
 * The confined classes a production [source] holds: the engines it names, and, when it names one, the view models it declares.
 * Only view models are listed, not their state or dialog types, so a test that merely reads one of those is not guarded.
 */
internal fun confinedClasses(source: String): Set<String> {
    val code = blank(source)
    val engines =
        saveEngines
            .findAll(code)
            .map { it.value }
            .filter { it != "SaveAsMade" }
            .toSet()
    if (saveEngines.find(code) == null) return emptySet()
    return engines + declaredViewModel.findAll(code).map { it.groupValues[1] }
}

/** The `setMain(Dispatchers.Unconfined)` lines of a test [source] that also names a class in [confined], one per line. */
internal fun unconfinedMains(
    source: String,
    confined: Set<String>,
): List<Unconfined> {
    val code = blank(source)
    val driving = confined.firstOrNull { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(code) } ?: return emptyList()
    return setsUnconfined
        .findAll(code)
        .map { Unconfined(driving, code.substring(0, it.range.first).count { c -> c == '\n' } + 1) }
        .toList()
}

/** [source] with its comments replaced by spaces, so no offset or line moves. */
private fun blank(source: String): String {
    val spaced = { m: MatchResult -> m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("") }
    val noBlocks = blockComment.replace(source, spaced)
    return lineComment.replace(noBlocks) { m -> m.value.takeWhile { it.isWhitespace() } + " ".repeat(m.value.trimStart().length) }
}
