package com.cooper.binge.companion.jellyfin.ui.search

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.template.FormSaveFailureSnackbar
import com.binge.designsystem.template.formSaveStatus
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.ALLOW_CLIENT_LOG_UPLOAD
import com.cooper.binge.companion.jellyfin.server.AUTOPLAY_NEXT_EPISODE
import com.cooper.binge.companion.jellyfin.server.DISPLAY_COLLECTIONS_VIEW
import com.cooper.binge.companion.jellyfin.server.DISPLAY_MISSING_EPISODES
import com.cooper.binge.companion.jellyfin.server.ENABLE_LEGACY_AUTHORIZATION
import com.cooper.binge.companion.jellyfin.server.ENABLE_METRICS
import com.cooper.binge.companion.jellyfin.server.ENABLE_SLOW_RESPONSE_WARNING
import com.cooper.binge.companion.jellyfin.server.HIDE_PLAYED_IN_LATEST
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.PLAY_DEFAULT_AUDIO_TRACK
import com.cooper.binge.companion.jellyfin.server.QUICK_CONNECT_AVAILABLE
import com.cooper.binge.companion.jellyfin.server.REMEMBER_AUDIO
import com.cooper.binge.companion.jellyfin.server.REMEMBER_SUBTITLES
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_HW_ACCELERATION
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_HW_ENCODING
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_KEY_FRAME_ONLY
import com.cooper.binge.companion.jellyfin.server.TRICKPLAY_OPTIONS
import com.cooper.binge.companion.jellyfin.server.advancedSettings
import com.cooper.binge.companion.jellyfin.server.homeLayout
import com.cooper.binge.companion.jellyfin.server.playbackPreferences
import com.cooper.binge.companion.jellyfin.server.trickplaySettings
import com.cooper.binge.companion.jellyfin.server.withTrickplayOption
import com.cooper.binge.companion.jellyfin.ui.console.RefusedMessage
import com.cooper.homelab.core.uiandroid.search.SearchToggle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One switch a search result can carry: which configuration it lives in, where it is offered ([gate]), how it reads from that
 * configuration (null where the configuration does not have it, as the pages hide a row the server did not send), and the change
 * it makes. The pages' own keys and decoders are used, so the search and the page cannot read one setting two ways.
 */
internal class SwitchSpec(
    val kind: ConfigKind,
    val gate: (JellyfinServerProfile) -> Boolean = { true },
    val read: (JsonObject) -> Boolean?,
    val change: (Boolean) -> ConfigChange,
)

private fun topLevel(key: String): (Boolean) -> ConfigChange = { on -> ConfigChange(key) { key to JsonPrimitive(on) } }

/** A trickplay option lives inside the options object, so the change is that object, made from the copy as it is. */
private fun trickplay(key: String): (Boolean) -> ConfigChange =
    { on ->
        ConfigChange("$TRICKPLAY_OPTIONS.$key") { configuration ->
            TRICKPLAY_OPTIONS to configuration.withTrickplayOption(key, JsonPrimitive(on))
        }
    }

/** A switch of the Advanced page, offered only where the server sent its key, as the page does. */
private fun JsonObject.advanced(
    key: String,
    value: (kotlinx.serialization.json.JsonObject) -> Boolean,
): Boolean? = if (key in advancedSettings().present) value(this) else null

internal val SWITCH_SPECS: Map<String, SwitchSpec> =
    mapOf(
        SettingRows.PLAYBACK_DEFAULT_AUDIO to
            SwitchSpec(
                ConfigKind.User,
                read = { it.playbackPreferences().playDefaultAudioTrack },
                change = topLevel(PLAY_DEFAULT_AUDIO_TRACK),
            ),
        SettingRows.PLAYBACK_REMEMBER_AUDIO to
            SwitchSpec(ConfigKind.User, read = { it.playbackPreferences().rememberAudio }, change = topLevel(REMEMBER_AUDIO)),
        SettingRows.PLAYBACK_REMEMBER_SUBTITLES to
            SwitchSpec(ConfigKind.User, read = { it.playbackPreferences().rememberSubtitles }, change = topLevel(REMEMBER_SUBTITLES)),
        SettingRows.PLAYBACK_AUTOPLAY to
            SwitchSpec(ConfigKind.User, read = { it.playbackPreferences().autoplayNextEpisode }, change = topLevel(AUTOPLAY_NEXT_EPISODE)),
        SettingRows.HOME_HIDE_PLAYED to
            SwitchSpec(ConfigKind.User, read = { it.homeLayout().hidePlayedInLatest }, change = topLevel(HIDE_PLAYED_IN_LATEST)),
        SettingRows.HOME_MISSING_EPISODES to
            SwitchSpec(ConfigKind.User, read = { it.homeLayout().displayMissingEpisodes }, change = topLevel(DISPLAY_MISSING_EPISODES)),
        SettingRows.HOME_COLLECTIONS to
            SwitchSpec(ConfigKind.User, read = { it.homeLayout().displayCollectionsView }, change = topLevel(DISPLAY_COLLECTIONS_VIEW)),
        // The server's configuration, for an administrator.
        SettingRows.SERVER_QUICK_CONNECT to
            SwitchSpec(
                ConfigKind.Server,
                read = { (it[QUICK_CONNECT_AVAILABLE] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() != false },
                change = topLevel(QUICK_CONNECT_AVAILABLE),
            ),
        SettingRows.TRICKPLAY_HW_ACCELERATION to
            SwitchSpec(ConfigKind.Server, read = { it.trickplaySettings().hwAcceleration }, change = trickplay(TRICKPLAY_HW_ACCELERATION)),
        SettingRows.TRICKPLAY_HW_ENCODING to
            SwitchSpec(ConfigKind.Server, read = { it.trickplaySettings().hwEncoding }, change = trickplay(TRICKPLAY_HW_ENCODING)),
        SettingRows.TRICKPLAY_KEY_FRAMES to
            SwitchSpec(
                ConfigKind.Server,
                gate = { it.hasKeyFrameOnlyTrickplay },
                read = { it.trickplaySettings().keyFrameOnly },
                change = trickplay(TRICKPLAY_KEY_FRAME_ONLY),
            ),
        SettingRows.ADVANCED_SLOW_WARNING to
            SwitchSpec(
                ConfigKind.Server,
                read = { it.advanced(ENABLE_SLOW_RESPONSE_WARNING) { c -> c.advancedSettings().slowResponseWarning } },
                change = topLevel(ENABLE_SLOW_RESPONSE_WARNING),
            ),
        SettingRows.ADVANCED_CLIENT_LOGS to
            SwitchSpec(
                ConfigKind.Server,
                read = { it.advanced(ALLOW_CLIENT_LOG_UPLOAD) { c -> c.advancedSettings().allowClientLogUpload } },
                change = topLevel(ALLOW_CLIENT_LOG_UPLOAD),
            ),
        SettingRows.ADVANCED_METRICS to
            SwitchSpec(
                ConfigKind.Server,
                read = { it.advanced(ENABLE_METRICS) { c -> c.advancedSettings().metrics } },
                change = topLevel(ENABLE_METRICS),
            ),
        SettingRows.ADVANCED_LEGACY_AUTH to
            SwitchSpec(
                ConfigKind.Server,
                gate = { it.hasCacheAndLegacyAuth },
                read = { it.advanced(ENABLE_LEGACY_AUTHORIZATION) { c -> c.advancedSettings().legacyAuthorization } },
                change = topLevel(ENABLE_LEGACY_AUTHORIZATION),
            ),
    )

/** The search's switches over one view model, made when the first one is shown. [onModel] tells the route which one there is. */
@Composable
internal fun searchSwitchesModel(
    graph: AppGraph,
    onModel: (SearchSwitchesViewModel) -> Unit,
): SearchSwitchesViewModel {
    val model: SearchSwitchesViewModel =
        viewModel(factory = viewModelFactory { initializer { SearchSwitchesViewModel(graph.connection) } })
    // The route reads each copy afresh, once, each time the search comes to the front: it is told which model there is.
    LaunchedEffect(model) { onModel(model) }
    return model
}

/**
 * The switch for a result whose setting is one of [SWITCH_SPECS], or null where [row] is none. The configuration is read, once, the
 * first time one of its switches is shown. The server's configuration is only offered to an administrator, and read for no one else.
 */
@Composable
internal fun configSwitchToggle(
    row: String?,
    graph: AppGraph,
    profile: JellyfinServerProfile,
    account: JellyfinAccount,
    onModel: (SearchSwitchesViewModel) -> Unit,
): SearchToggle? {
    val spec = SWITCH_SPECS[row] ?: return null
    if (!spec.gate(profile) || (spec.kind == ConfigKind.Server && !account.isAdministrator)) return null
    val model = searchSwitchesModel(graph, onModel)
    val copy = model.copy(spec.kind)
    LaunchedEffect(copy) { copy.start() }
    val state by copy.state.collectAsStateWithLifecycle()
    return configToggle(spec.read, state) { on -> copy.change(spec.change(on)) }
}

/**
 * What a result's switch says, from the one copy of its configuration: the value and the setter of its row, a spinner until the
 * configuration has been read, while it is read again (the copy may be older than the server's) and while a write is in flight, and
 * no switch at all if it could not be read or the server did not send the setting (the page opens on its own failure and Retry).
 */
internal fun configToggle(
    read: (JsonObject) -> Boolean?,
    state: SearchConfigState,
    set: (Boolean) -> Unit,
): SearchToggle? {
    val ready = state as? SearchConfigState.Ready
    val checked = ready?.let { read(it.configuration) }
    return when {
        state is SearchConfigState.Failed -> null
        ready == null -> SearchToggle(checked = false, onCheckedChange = {}, enabled = false, loading = true)
        checked == null -> null
        ready.reading -> SearchToggle(checked, onCheckedChange = {}, enabled = false, loading = true)
        else -> SearchToggle(checked, set, enabled = true, loading = ready.save.status == SaveStatus.Saving)
    }
}

/**
 * What the pages say about a save, which the search must say too, since the user does not leave it: a refusal (the server put the
 * change back) as the page's own message, and a change kept unsent as the page's snackbar with Retry. Both go to [host].
 */
@Composable
internal fun SearchServerMessages(
    model: SearchSwitchesViewModel?,
    host: SnackbarHostState,
) {
    model ?: return
    CopyMessages(model.user, host)
    CopyMessages(model.server, host)
}

@Composable
private fun CopyMessages(
    copy: SearchConfigCopy,
    host: SnackbarHostState,
) {
    val state by copy.state.collectAsStateWithLifecycle()
    (state as? SearchConfigState.Ready)?.let {
        RefusedMessage(it.refused, host)
        FormSaveFailureSnackbar(formSaveStatus(it.save.status, it.save.failed, copy::retry), host)
    }
}
