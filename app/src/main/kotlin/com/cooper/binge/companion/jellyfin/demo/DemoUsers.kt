package com.cooper.binge.companion.jellyfin.demo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal const val DEMO_USER_ID = "d3m00000000000000000000000000001"
internal const val DEMO_USER_NAME = "Alex"
internal const val DEMO_SAM_ID = "d3m00000000000000000000000000002"
internal const val DEMO_KIDS_ID = "d3m00000000000000000000000000003"

private const val DEFAULT_PROVIDER = "Jellyfin.Server.Implementations.Users.DefaultAuthenticationProvider"
private const val HTTP_BAD_REQUEST = 400
private const val DEFAULT_CONFIGURATION =
    """
    {"AudioLanguagePreference":"eng","SubtitleLanguagePreference":"eng","SubtitleMode":"Default","PlayDefaultAudioTrack":true,
     "RememberAudioSelections":true,"RememberSubtitleSelections":true,"EnableNextEpisodeAutoPlay":true,
     "OrderedViews":[],"MyMediaExcludes":[],"LatestItemsExcludes":[],"GroupedFolders":[],"HidePlayedInLatest":true,
     "DisplayMissingEpisodes":false,"DisplayCollectionsView":false}
    """
private const val SAM_MINUTES_AGO = 12L
private const val KIDS_DAYS_AGO = 3L

/** The server's users, who can be added, changed and removed while the demo is open. */
internal class DemoUsers : DemoState {
    private val seen = Instant.now()
    private val users: MutableList<JsonObject> = seed()

    private fun seed() =
        mutableListOf(
            user(DEMO_USER_ID, DEMO_USER_NAME, admin = true, lastSeen = seen),
            user(DEMO_SAM_ID, "Sam", admin = false, lastSeen = seen.minus(Duration.ofMinutes(SAM_MINUTES_AGO))),
            user(DEMO_KIDS_ID, "Kids", admin = false, lastSeen = seen.minus(Duration.ofDays(KIDS_DAYS_AGO)), hasPassword = false),
        )

    override fun reset() {
        users.clear()
        users.addAll(seed())
    }

    fun administrator(): JsonObject = users.first { it.string(ID) == DEMO_USER_ID }

    fun names(): List<String> = users.mapNotNull { it.string("Name") }

    fun idOf(name: String): String? = users.firstOrNull { it.string("Name") == name }?.string(ID)

    override fun routes(): List<DemoRoute> =
        listOf(
            route("GET", "Users", "Me") { _, _ -> ok(administrator()) },
            route("GET", "Users") { _, _ -> ok(users.toList()) },
            route("POST", "Users", "New") { call, _ -> create(call.jsonBody()) },
            route("POST", "Users", "Password") { call, _ -> setPassword(call) },
            route("POST", "Users", "Configuration") { call, _ -> saveConfiguration(call) },
            route("GET", "Users", "{}") { _, (id) -> find(id)?.let(::ok) ?: demoNotFound },
            route("POST", "Users", "{}", "Policy") { call, (id) -> savePolicy(id, call.jsonBody()) },
            route("DELETE", "Users", "{}") { _, (id) -> delete(id) },
            route("GET", "Users", "{}", "Images", "Primary") { _, _ -> demoNotFound },
            route("GET", "UserImage") { _, _ -> demoNotFound },
            route("POST", "UserImage") { _, _ -> noContent },
            route("DELETE", "UserImage") { _, _ -> noContent },
        )

    private fun find(id: String) = users.firstOrNull { it.string(ID).equals(id, ignoreCase = true) }

    private fun create(body: JsonObject): DemoAnswer {
        val name = body.string("Name")?.trim().orEmpty()
        if (name.isEmpty() ||
            users.any { it.string("Name").equals(name, ignoreCase = true) }
        ) {
            return DemoAnswer(HTTP_BAD_REQUEST, """{"title":"That name is taken."}""")
        }
        val created =
            user(
                UUID.randomUUID().toString().replace("-", ""),
                name,
                admin = false,
                lastSeen = null,
                hasPassword = !body.string("Password").isNullOrEmpty(),
            )
        users += created
        return ok(created)
    }

    private fun setPassword(call: DemoCall): DemoAnswer {
        val id = call.query("userId") ?: return demoNotFound
        val index = users.indexOfFirst { it.string(ID).equals(id, ignoreCase = true) }
        if (index < 0) return demoNotFound
        users[index] = users[index].with("HasPassword", JsonPrimitive(!call.jsonBody().string("NewPw").isNullOrEmpty()))
        return noContent
    }

    private fun saveConfiguration(call: DemoCall): DemoAnswer {
        val index = users.indexOfFirst { it.string(ID).equals(call.query("userId"), ignoreCase = true) }
        if (index < 0) return demoNotFound
        users[index] = users[index].with("Configuration", call.jsonBody())
        return noContent
    }

    private fun savePolicy(
        id: String,
        policy: JsonObject,
    ): DemoAnswer {
        val index = users.indexOfFirst { it.string(ID).equals(id, ignoreCase = true) }
        if (index < 0) return demoNotFound
        val others = users.filterIndexed { i, _ -> i != index }
        val leavesNoAdministrator = policy.bool("IsAdministrator") != true && others.none { it.policy().bool("IsAdministrator") == true }
        if (leavesNoAdministrator) return DemoAnswer(HTTP_BAD_REQUEST, """{"title":"There must be at least one administrator."}""")
        users[index] = users[index].with("Policy", policy)
        return noContent
    }

    private fun delete(id: String): DemoAnswer {
        if (id.equals(
                DEMO_USER_ID,
                ignoreCase = true,
            )
        ) {
            return DemoAnswer(HTTP_BAD_REQUEST, """{"title":"The signed-in user cannot be deleted."}""")
        }
        return if (users.removeAll { it.string(ID).equals(id, ignoreCase = true) }) noContent else demoNotFound
    }

    private fun user(
        id: String,
        name: String,
        admin: Boolean,
        lastSeen: Instant?,
        hasPassword: Boolean = true,
    ) = buildJsonObject {
        put(ID, id)
        put("Name", name)
        put("ServerId", DEMO_SERVER_ID)
        put("HasPassword", hasPassword)
        lastSeen?.let { put("LastActivityDate", it.toString()) }
        put("Configuration", jsonObject(DEFAULT_CONFIGURATION))
        put(
            "Policy",
            buildJsonObject {
                put("IsAdministrator", admin)
                put("IsDisabled", false)
                put("EnableAllFolders", true)
                put("EnableUserPreferenceAccess", true)
                put("EnableRemoteControlOfOtherUsers", admin)
                put("EnableSharedDeviceControl", admin)
                put("EnableLiveTvManagement", admin)
                put("EnableLiveTvAccess", true)
                put("AuthenticationProviderId", DEFAULT_PROVIDER)
            },
        )
    }

    private companion object {
        const val ID = "Id"
    }
}

internal fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.bool(key: String): Boolean? = (get(key) as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

internal fun JsonObject.policy(): JsonObject = get("Policy") as? JsonObject ?: JsonObject(emptyMap())

internal fun JsonObject.with(
    key: String,
    value: kotlinx.serialization.json.JsonElement,
): JsonObject = JsonObject(this + (key to value))
