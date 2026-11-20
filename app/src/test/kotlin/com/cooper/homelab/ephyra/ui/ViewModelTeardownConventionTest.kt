package com.cooper.homelab.ephyra.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A test that builds a view model makes it through [ViewModelTeardown], so its scope is stopped and finished before Main is reset.
 * One that does not leaves coroutines running into the next test in the same JVM, and a random test after it fails with
 * `UncaughtExceptionsBeforeTest` (#493, #816). This reads the test sources and checks each construction on its own, so the next such
 * test fails here, by file and line, rather than as a flake somewhere else. A file that makes one view model and builds another
 * directly is caught as well (#821).
 */
class ViewModelTeardownConventionTest {
    /** Tests that stop their own view models, and why. Nothing else belongs here. */
    private val ownTeardown = setOf("JellyfinViewModelTest.kt")

    @Test
    fun `every view model a test builds is made through the shared teardown`() {
        val root = File("src/test/kotlin")
        // Unit tests run from the module's directory. From anywhere else there would be nothing to read, and the rule would pass unchecked.
        assertTrue("No test sources at ${root.absolutePath}", root.isDirectory)
        val offenders =
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name !in ownTeardown && it.name != "ViewModelTeardownConventionTest.kt" }
                .flatMap { file -> unmadeViewModels(file.readText()).map { "${file.name}:${it.line} ${it.name}" } }
                .sorted()
                .toList()
        assertEquals("These build a view model outside made(...): $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun `a file that makes one view model and builds another directly is caught, at the one it built directly`() {
        val source =
            """
            class SomeTest {
                private val teardown = ViewModelTeardown()
                private fun made() = teardown.made(HomeTabViewModel(connection, Dispatchers.IO, profile))

                @Test
                fun direct() {
                    val vm = HomeTabViewModel(connection, Dispatchers.IO, profile)
                }
            }
            """.trimIndent()
        assertEquals(listOf(Unmade("HomeTabViewModel", 7)), unmadeViewModels(source))
    }

    @Test
    fun `a view model made on several lines, or through a helper that calls made, is clean`() {
        val source =
            """
            private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

            fun build() =
                teardown.made(
                    SomeViewModel(
                        scope = scope,
                        other = OtherThing(a, b),
                    ),
                )

            fun again() = made(OtherViewModel(x)).also { it.start() }
            """.trimIndent()
        assertEquals(emptyList<Unmade>(), unmadeViewModels(source))
    }

    @Test
    fun `a declaration, a supertype, a comment and a mention are not constructions`() {
        val source =
            """
            // HomeViewModel(a, b) is built in the real code
            /* and SomeViewModel(x) here */
            class FakeViewModel(val x: Int) : ViewModel()
            val fake = object : ViewModel() {}
            val name = "http://host/FooViewModel"
            fun f() = made(FooViewModel(1))
            """.trimIndent()
        assertEquals(emptyList<Unmade>(), unmadeViewModels(source))
    }
}

/** A view model [name] built at [line] (from 1) outside a `made(...)` call. */
internal data class Unmade(
    val name: String,
    val line: Int,
)

private val construction = Regex("""\b([A-Za-z0-9]+ViewModel)\(""")
private val madeCall = Regex("""\bmade\(""")
private val blockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
private val lineComment = Regex("""(?m)(^|\s)//[^\n]*""")

/**
 * The `...ViewModel(` constructions in [source] that are not inside the parentheses of a `made(...)` call, in order. Comments are
 * blanked first (keeping the line numbers), and a class declaration or a function named like one is not a construction.
 */
internal fun unmadeViewModels(source: String): List<Unmade> {
    val code = blank(source)
    val madeSpans = madeCall.findAll(code).map { it.range.last until closing(code, it.range.last) }.toList()
    return construction
        .findAll(code)
        .filterNot { code.substring(0, it.range.first).trimEnd().let { before -> before.endsWith("class") || before.endsWith("fun") } }
        .filterNot { match -> madeSpans.any { match.range.first in it } }
        .map { Unmade(it.groupValues[1], code.substring(0, it.range.first).count { c -> c == '\n' } + 1) }
        .toList()
}

/** [source] with its comments replaced by spaces, so no offset or line moves. */
private fun blank(source: String): String {
    val spaced = { m: MatchResult -> m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("") }
    val noBlocks = blockComment.replace(source, spaced)
    return lineComment.replace(noBlocks) { m -> m.value.takeWhile { it.isWhitespace() } + " ".repeat(m.value.trimStart().length) }
}

/** The index of the parenthesis that closes the one at [open], or the end of [code] if it never does. */
private fun closing(
    code: String,
    open: Int,
): Int {
    var depth = 0
    for (i in open until code.length) {
        when (code[i]) {
            '(' -> depth++
            ')' -> if (--depth == 0) return i
        }
    }
    return code.length
}
