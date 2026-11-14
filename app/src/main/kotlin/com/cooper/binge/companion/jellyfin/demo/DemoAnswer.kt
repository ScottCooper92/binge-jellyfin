package com.cooper.binge.companion.jellyfin.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private const val HTTP_OK = 200
private const val HTTP_NO_CONTENT = 204
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404
private const val HTTP_NOT_IMPLEMENTED = 501

/** What the demo server says to one call: a status and a JSON body (empty for no content). */
internal class DemoAnswer(
    val code: Int,
    val body: String = "",
)

/** One call to the demo server: the method, the path, one query parameter at a time, and the body. */
internal class DemoCall(
    val method: String,
    val path: String,
    private val query: (String) -> String?,
    private val readBody: () -> String,
) {
    val segments: List<String> = path.split('/').filter { it.isNotEmpty() }
    val body: String get() = readBody()

    /** A query parameter by name, ignoring case: Jellyfin reads them that way. */
    fun query(name: String): String? = query.invoke(name)
}

internal fun ok(value: JsonElement) = DemoAnswer(HTTP_OK, value.toString())

internal fun ok(values: List<JsonObject>) = ok(JsonArray(values))

internal val noContent = DemoAnswer(HTTP_NO_CONTENT)

internal val demoNotFound = DemoAnswer(HTTP_NOT_FOUND, """{"title":"Not found."}""")

internal val demoUnauthorized = DemoAnswer(HTTP_UNAUTHORIZED, """{"title":"Not signed in."}""")

/**
 * What every endpoint the demo does not serve answers. It is a server error on purpose: the screens already have a
 * way to show one, and the message says why, so a reviewer who reaches it is told rather than left to guess.
 */
internal val notInTheDemo = DemoAnswer(HTTP_NOT_IMPLEMENTED, """{"title":"This is not available in the demo."}""")

/** One call the demo serves: its method and path, where `{}` matches any one segment, and what answers it. */
internal class DemoRoute(
    val verb: String,
    val pattern: List<String>,
    val handle: (DemoCall, List<String>) -> DemoAnswer,
)

internal fun route(
    verb: String,
    vararg pattern: String,
    handle: (DemoCall, List<String>) -> DemoAnswer,
) = DemoRoute(verb, pattern.toList(), handle)

/** The first route that serves [call], in list order, so a literal path comes before a `{}` that would also match it. */
internal fun List<DemoRoute>.answer(call: DemoCall): DemoAnswer? =
    firstNotNullOfOrNull { route -> call.match(route.verb, route.pattern)?.let { route.handle(call, it) } }

/** The segments `{}` matched, in order, when [call] is `{verb} {pattern}`; null when it is not that call. */
internal fun DemoCall.match(
    verb: String,
    pattern: List<String>,
): List<String>? {
    if (!method.equals(verb, ignoreCase = true) || segments.size != pattern.size) return null
    val captured = mutableListOf<String>()
    for ((segment, expected) in segments.zip(pattern)) {
        if (expected == "{}") {
            captured += segment
        } else if (!segment.equals(expected, ignoreCase = true)) {
            return null
        }
    }
    return captured
}
