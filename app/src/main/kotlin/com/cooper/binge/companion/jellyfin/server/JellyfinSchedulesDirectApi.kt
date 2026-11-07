package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

// Schedules Direct is set up in three server calls, because the server reads a lineup list with the account it has saved: the account
// goes first (`validateLogin` only checks that the fields are not empty, the login itself happens at the lineup step), then the countries
// and the lineups for a postal code, then the provider is saved whole with the lineup chosen and `validateListings`, which adds the
// lineup to the Schedules Direct account. A lineup list is empty when the login fails or the lookup throws, so an empty list does not say
// which. The password goes only in the body of the save, hashed: the server hashes `pw` itself but that is a URL parameter. Countries and
// lineups are `LiveTvAccess`, which a manager may not have. Read from the server's source at 10.8.13, 10.9.11, 10.10.7 and 10.11.0.

private const val COUNTRIES = "LiveTv/ListingProviders/SchedulesDirect/Countries"
private const val LINEUPS = "LiveTv/ListingProviders/Lineups"

const val GUIDE_TYPE_SCHEDULES_DIRECT = "SchedulesDirect"
const val GUIDE_USERNAME = "Username"
const val GUIDE_PASSWORD = "Password"
const val GUIDE_LISTINGS_ID = "ListingsId"
const val GUIDE_COUNTRY = "Country"
const val GUIDE_ZIP_CODE = "ZipCode"

private const val HEX_RADIX = 16
private const val BYTE_MASK = 0xff
private const val HEX_DIGITS_PER_BYTE = 2

/** A country Schedules Direct serves, by its code (`USA`) and its name, under the region it lists it in. */
data class SdCountry(
    val code: String,
    val name: String,
    val region: String,
)

/** A lineup for a postal code: [id] is what the provider's `ListingsId` holds. */
data class SdLineup(
    val id: String,
    val name: String,
)

/**
 * The countries Schedules Direct serves. The server passes its answer on as it came, an object of regions each holding a list of
 * countries, so a shape this app does not know reads as no countries, and the page says so.
 */
suspend fun JellyfinClient.schedulesDirectCountries(): List<SdCountry> = (get(COUNTRIES) as? JsonObject).orEmpty().sdCountries()

internal fun Map<String, JsonElement>.sdCountries(): List<SdCountry> =
    flatMap { (region, countries) ->
        (countries as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { country ->
            val code = country.string("shortName") ?: return@mapNotNull null
            SdCountry(code, country.string("fullName") ?: code, region)
        }
    }.sortedBy { it.name }

/**
 * The lineups for [postalCode] in [country], read with the saved account [providerId]. Empty when the account cannot log in, the postal
 * code has none, or the lookup failed, which the server does not tell apart.
 */
suspend fun JellyfinClient.schedulesDirectLineups(
    providerId: String,
    country: String,
    postalCode: String,
): List<SdLineup> =
    (get(LINEUPS, mapOf("id" to providerId, "country" to country, "location" to postalCode)) as? JsonArray)
        .orEmpty()
        .mapNotNull { it as? JsonObject }
        .mapNotNull { lineup -> lineup.string("Id")?.let { SdLineup(it, lineup.string("Name") ?: it) } }

/** The password as Schedules Direct wants it: its SHA-1, in lowercase hex. Sent to the user's own server in a body, and held nowhere. */
internal fun sha1Hex(password: String): String =
    MessageDigest
        .getInstance("SHA-1")
        .digest(password.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(HEX_DIGITS_PER_BYTE, '0') }

/** A new Schedules Direct account: the lineup is chosen in the next step, when the server can read lineups for it. */
fun newSchedulesDirectProvider(
    username: String,
    password: String,
): JsonObject =
    JsonObject(
        mapOf(
            GUIDE_TYPE to JsonPrimitive(GUIDE_TYPE_SCHEDULES_DIRECT),
            GUIDE_USERNAME to JsonPrimitive(username),
            GUIDE_PASSWORD to JsonPrimitive(sha1Hex(password)),
        ),
    )

/** [provider] with its lineup chosen, for the country and postal code it was found by. */
fun JsonObject.withLineup(
    listingsId: String,
    country: String,
    postalCode: String,
): JsonObject =
    JsonObject(
        this + (GUIDE_LISTINGS_ID to JsonPrimitive(listingsId)) + (GUIDE_COUNTRY to JsonPrimitive(country)) +
            (GUIDE_ZIP_CODE to JsonPrimitive(postalCode)),
    )

/** [provider] with a new login: the username as typed and the password hashed, everything else, the lineup included, as it was. */
fun JsonObject.withLogin(
    username: String,
    password: String,
): JsonObject = JsonObject(this + (GUIDE_USERNAME to JsonPrimitive(username)) + (GUIDE_PASSWORD to JsonPrimitive(sha1Hex(password))))
