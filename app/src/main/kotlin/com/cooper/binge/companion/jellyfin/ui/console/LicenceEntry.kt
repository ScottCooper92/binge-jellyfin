package com.cooper.binge.companion.jellyfin.ui.console

/** One shipped library as the licences page lists it: its [name], [version], the names of its [licences] and its [website]. */
data class LicenceEntry(
    val id: String,
    val name: String,
    val version: String?,
    val licences: List<String>,
    val website: String?,
)

/** A library as AboutLibraries reports it, reduced to plain fields so the mapping needs none of its types. */
internal data class RawLibrary(
    val id: String,
    val name: String,
    val version: String?,
    val licenceNames: List<String>,
    val website: String?,
)

/** The libraries of [this], by name (ignoring case), each with its licence names. Blank fields read as absent. */
internal fun List<RawLibrary>.toLicenceEntries(): List<LicenceEntry> =
    map { library ->
        LicenceEntry(
            id = library.id,
            name = library.name.ifBlank { library.id },
            version = library.version?.takeIf { it.isNotBlank() },
            licences = library.licenceNames.filter { it.isNotBlank() }.distinct(),
            website = library.website?.takeIf { it.isNotBlank() },
        )
    }.sortedBy { it.name.lowercase() }
