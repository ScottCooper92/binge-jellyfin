package com.cooper.homelab.ephyra.demo

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val DEFAULT_WIDTH = 300
private const val MIN_WIDTH = 16
private const val MAX_WIDTH = 1_920
private const val POSTER_HEIGHT_NUMERATOR = 3
private const val POSTER_HEIGHT_DENOMINATOR = 2
private const val WIDE_HEIGHT_NUMERATOR = 9
private const val WIDE_HEIGHT_DENOMINATOR = 16
private const val HUE_DEGREES = 360
private const val HUE_SHIFT = 28
private const val FNV_OFFSET = -0x7ee3623b
private const val FNV_PRIME = 0x01000193
private const val HASH_MIX_SHIFT = 15
private const val SATURATION_TOP = 0.55f
private const val SATURATION_BOTTOM = 0.62f
private const val VALUE_TOP = 0.44f
private const val VALUE_BOTTOM = 0.30f
private const val SIXTH = 60f
private const val OPAQUE = 0xFF
private const val BYTE = 255
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val SEGMENT_PAIR = 2
private const val INITIALS = 2
private val SKIPPED_WORDS = setOf("the", "a", "an")

/** How a demo picture is laid out: a poster, a wide backdrop or thumb, an episode still, or a person's square. */
enum class DemoPictureShape { Poster, Wide, Still, Person }

/**
 * What the demo draws for one picture, as plain values: the shape and size, the two colours of its gradient, the big text on it
 * (initials, or an episode's number) and the title under it. The app draws it; nothing here touches the screen, so it is checked
 * without a device. Colours come from the item's id, so a title looks the same on every run and no two neighbours match, and both
 * are dark enough for white text to read on them whichever theme the screen around it is in.
 */
data class DemoPicture(
    val shape: DemoPictureShape,
    val width: Int,
    val height: Int,
    /** ARGB of the top left corner of the gradient. */
    val topColor: Int,
    /** ARGB of the bottom right corner. */
    val bottomColor: Int,
    val label: String,
    val caption: String?,
)

/** Whether [this] is an address on the demo server, whatever it asks for. */
fun String.isDemoUrl(): Boolean = toHttpUrlOrNull()?.isDemo() == true

/**
 * The picture the demo draws for [url], or null when it is not the demo's, or when the demo deliberately has no such picture (a
 * person with no photo, a hand-made box set's backdrop), so those cases still show their placeholder.
 */
fun demoPictureFor(url: String): DemoPicture? {
    val address = url.toHttpUrlOrNull()?.takeIf { it.isDemo() } ?: return null
    val segments = address.pathSegments
    if (segments.size != SEGMENTS || segments[0] != "Items" || segments[2] != "Images") return null
    return pictureFor(segments[1], segments[SEGMENTS - 1], width(address), address.queryParameter("tag"))
}

private const val SEGMENTS = 4

private fun width(address: HttpUrl): Int =
    (address.queryParameter("maxWidth")?.toIntOrNull() ?: DEFAULT_WIDTH).coerceIn(MIN_WIDTH, MAX_WIDTH)

/** One item the demo can draw: its name, and what it is. */
private class Subject(
    val name: String,
    val shape: DemoPictureShape,
    val label: String,
    /** Which picture kinds it has. */
    val kinds: Set<String>,
)

private val SUBJECTS: Map<String, Subject> by lazy {
    buildMap {
        seedTitles().forEach { title -> put(title.id, subject(title)) }
        PERSONS.forEach { person ->
            put(
                person.id,
                Subject(person.name, DemoPictureShape.Person, initials(person.name), if (person.photo) setOf("Primary") else emptySet()),
            )
        }
    }
}

private fun subject(title: DemoTitle): Subject {
    val label =
        when {
            title.type == "Episode" && title.episode != null -> "E${title.episode}"
            title.type == "Season" -> if (title.season == 0) "SP" else "S${title.season}"
            else -> initials(title.name)
        }
    val shape = if (title.type == "Episode") DemoPictureShape.Still else DemoPictureShape.Poster
    val kinds =
        buildSet {
            add("Primary")
            if (title.hasBackdrop) {
                add("Backdrop")
                add("Thumb")
            }
        }
    return Subject(title.name, shape, label, kinds)
}

/**
 * A library, known only by its picture's tag: `p-`, `t-` or `b-`, then the library's id, then its current name. A library's name can
 * change and a library can be added, so the tag carries the name and nothing here keeps a list of them.
 */
private fun library(
    id: String,
    kind: String,
    tag: String?,
): Subject? {
    val prefix = LIBRARY_TAG_PREFIXES[kind] ?: return null
    val name = tag?.takeIf { it.startsWith("$prefix$id-") }?.removePrefix("$prefix$id-")?.takeIf { it.isNotBlank() } ?: return null
    return Subject(name, DemoPictureShape.Wide, initials(name), setOf(kind))
}

private val LIBRARY_TAG_PREFIXES = mapOf("Primary" to "p-", "Thumb" to "t-", "Backdrop" to "b-")

private fun pictureFor(
    id: String,
    kind: String,
    width: Int,
    tag: String?,
): DemoPicture? {
    val subject = (SUBJECTS[id] ?: library(id, kind, tag))?.takeIf { kind in it.kinds } ?: return null
    // Backdrops and thumbs are wide whatever they belong to; a Primary takes its subject's own shape.
    val shape = if (kind == "Primary") subject.shape else DemoPictureShape.Wide
    val height =
        when (shape) {
            DemoPictureShape.Poster -> width * POSTER_HEIGHT_NUMERATOR / POSTER_HEIGHT_DENOMINATOR
            DemoPictureShape.Wide, DemoPictureShape.Still -> width * WIDE_HEIGHT_NUMERATOR / WIDE_HEIGHT_DENOMINATOR
            DemoPictureShape.Person -> width
        }
    val hue = hueOf(id)
    return DemoPicture(
        shape = shape,
        width = width,
        height = height,
        topColor = hsv(hue, SATURATION_TOP, VALUE_TOP),
        bottomColor = hsv((hue + HUE_SHIFT) % HUE_DEGREES, SATURATION_BOTTOM, VALUE_BOTTOM),
        label = subject.label,
        // A backdrop and a thumb sit under a title the screen writes itself, and a person's is a disc: only a poster, a still and a
        // library's own picture carry the name.
        caption = subject.name.takeIf { kind == "Primary" && shape != DemoPictureShape.Person },
    )
}

/** A hue from the id alone: FNV-1a, mixed once more, so ids that differ in one character land far apart. */
private fun hueOf(id: String): Float {
    val hash = id.fold(FNV_OFFSET) { hash, char -> (hash xor char.code) * FNV_PRIME }
    return Math.floorMod(hash xor (hash ushr HASH_MIX_SHIFT), HUE_DEGREES).toFloat()
}

/** The first letters of the first two words that are not an article: "The Long Quiet" is "LQ". */
internal fun initials(name: String): String {
    val words = name.split(' ', '-').filter { it.isNotBlank() }
    val significant = words.filter { it.lowercase() !in SKIPPED_WORDS }.ifEmpty { words }
    return significant.take(INITIALS).joinToString("") { it.first().uppercase() }
}

/** An opaque ARGB colour from hue (0 to 360), saturation and value (0 to 1). */
internal fun hsv(
    hue: Float,
    saturation: Float,
    value: Float,
): Int {
    val sector = (hue / SIXTH).toInt() % (HUE_DEGREES / SIXTH.toInt())
    val fraction = hue / SIXTH - (hue / SIXTH).toInt()
    val p = value * (1 - saturation)
    val q = value * (1 - saturation * fraction)
    val t = value * (1 - saturation * (1 - fraction))
    val (r, g, b) =
        when (sector) {
            0 -> Triple(value, t, p)
            1 -> Triple(q, value, p)
            SEGMENT_PAIR -> Triple(p, value, t)
            SEGMENT_PAIR + 1 -> Triple(p, q, value)
            SEGMENT_PAIR + SEGMENT_PAIR -> Triple(t, p, value)
            else -> Triple(value, p, q)
        }
    return (OPAQUE shl (RED_SHIFT + GREEN_SHIFT)) or
        ((r * BYTE).toInt() shl RED_SHIFT) or
        ((g * BYTE).toInt() shl GREEN_SHIFT) or
        (b * BYTE).toInt()
}
