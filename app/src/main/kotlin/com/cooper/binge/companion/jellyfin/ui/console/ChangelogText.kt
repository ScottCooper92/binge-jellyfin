package com.cooper.binge.companion.jellyfin.ui.console

/**
 * A catalog changelog, which repositories write as Markdown, as plain text: headings lose their marks, list items get a bullet, and
 * emphasis, code and links keep their words only. Anything else reads as it was written. It is a reading aid, not a Markdown renderer.
 */
internal fun plainChangelog(markdown: String): String =
    markdown
        .lines()
        .joinToString("\n") { line ->
            line
                .replace(HEADING, "")
                .replace(BULLET) { "${it.groupValues[1]}• " }
                .replace(LINK) { it.groupValues[1] }
                .replace(EMPHASIS) { it.groupValues[UNDERSCORED] + it.groupValues[STARRED] }
                .replace("`", "")
        }.replace(BLANK_RUNS, "\n\n")
        .trim()

private val HEADING = Regex("^\\s{0,3}#{1,6}\\s+")
private val BULLET = Regex("^(\\s*)[*+-]\\s+")
private val LINK = Regex("!?\\[([^]]*)]\\([^)]*\\)")
private val EMPHASIS = Regex("(?<!\\w)(__|_)(?=\\S)(.+?)(?<=\\S)\\1(?!\\w)|(\\*\\*|\\*)(?=\\S)(.+?)(?<=\\S)\\3")
private val BLANK_RUNS = Regex("\n{3,}")

/** The groups of [EMPHASIS] that hold the words: those inside underscores, and those inside asterisks. */
private const val UNDERSCORED = 2
private const val STARRED = 4
