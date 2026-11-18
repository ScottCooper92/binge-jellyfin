package com.cooper.homelab.ephyra.ui.console

import android.content.Context
import com.cooper.homelab.core.presentation.appsettings.LicenceEntry
import com.cooper.homelab.core.presentation.appsettings.LicenceSource
import com.mikepenz.aboutlibraries.Libs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.mikepenz.aboutlibraries.util.withContext as withAboutLibrariesContext

/**
 * The licences of the libraries the app ships, for the shared licences page. The build writes the list (the AboutLibraries plugin
 * puts `raw/aboutlibraries.json` in the app's resources) and this reads it, on [dispatcher]. AboutLibraries is a vendor library, so
 * it stays here and the shared page takes only the entries, which it cleans and sorts.
 */
internal class AboutLibrariesLicenceSource(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher,
) : LicenceSource {
    override suspend fun load(): List<LicenceEntry> =
        withContext(dispatcher) {
            Libs
                .Builder()
                .withAboutLibrariesContext(context)
                .build()
                .libraries
                .map { library ->
                    LicenceEntry(
                        id = library.uniqueId,
                        name = library.name,
                        version = library.artifactVersion,
                        licences = library.licenses.map { it.name },
                        website = library.website,
                    )
                }
        }
}
