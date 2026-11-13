package com.cooper.binge.companion.jellyfin.ui.console

import android.content.Context
import com.mikepenz.aboutlibraries.Libs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.mikepenz.aboutlibraries.util.withContext as withAboutLibrariesContext

/**
 * The licences of the libraries the app ships. The build writes the list (the AboutLibraries plugin puts
 * `raw/aboutlibraries.json` in the app's resources) and this reads it, on [dispatcher]. Only the data side of
 * AboutLibraries is used; the page is drawn with the design system's rows.
 */
class LicenceSource(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher,
) {
    suspend fun load(): List<LicenceEntry> =
        withContext(dispatcher) {
            Libs
                .Builder()
                .withAboutLibrariesContext(context)
                .build()
                .libraries
                .map { library ->
                    RawLibrary(
                        id = library.uniqueId,
                        name = library.name,
                        version = library.artifactVersion,
                        licenceNames = library.licenses.map { it.name },
                        website = library.website,
                    )
                }.toLicenceEntries()
        }
}
