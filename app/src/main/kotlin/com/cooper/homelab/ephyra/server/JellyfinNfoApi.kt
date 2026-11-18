package com.cooper.homelab.ephyra.server

import kotlinx.serialization.json.JsonObject

// The server-wide half of NFO (Kodi) metadata: `XbmcMetadataOptions`, a named configuration (`/System/Configuration/xbmcmetadata`).
// The five keys below carry the same names from 10.8 to 10.11 (the web-UI parity audit of #525; not re-read in the server source by
// this change), so the page has no version gate.
// The per-library NFO reader and saver switches are in the library options. The page changes one key at a time and sends the
// whole object back, so a key this app doesn't know returns as the server sent it.

private const val XBMC_METADATA = "System/Configuration/xbmcmetadata"

/** The user whose watch state is written into NFO files, or null (JSON null, or no key) for none. */
const val NFO_USER_ID = "UserId"

/** The date format of a release date in an NFO file, as a .NET format string such as `yyyy-MM-dd`. */
const val NFO_RELEASE_DATE_FORMAT = "ReleaseDateFormat"
const val NFO_SAVE_IMAGE_PATHS = "SaveImagePathsInNfo"
const val NFO_PATH_SUBSTITUTION = "EnablePathSubstitution"
const val NFO_EXTRA_THUMBS = "EnableExtraThumbsDuplication"

/** The server's whole NFO configuration, kept whole so a save sends back what it didn't change. */
suspend fun JellyfinClient.nfoConfiguration(): JsonObject = getObject(XBMC_METADATA)

suspend fun JellyfinClient.saveNfoConfiguration(configuration: JsonObject) {
    post(XBMC_METADATA, configuration)
}
