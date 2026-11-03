package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable

/*
 * What is typed in a sheet form, saved so it survives the activity being recreated (a rotation).
 *
 * A sheet is a window, and what its content saves is not found again when the sheet reopens after a recreation. So a sheet
 * calls these above `BingeAdaptiveSheet` and hands the drafts down to its form, as `NewKeySheet` holds its name. A form takes
 * its drafts as parameters that default to the same call under the same key, so a frame or a test that draws the form
 * without a sheet needs nothing.
 */

@Composable
internal fun rememberDraft(
    key: String,
    initial: String,
): MutableState<String> = rememberSaveable(key = key) { mutableStateOf(initial) }

@Composable
internal fun rememberDraft(
    key: String,
    initial: Boolean,
): MutableState<Boolean> = rememberSaveable(key = key) { mutableStateOf(initial) }

@Composable
internal fun rememberDraft(
    key: String,
    initial: Int,
): MutableIntState = rememberSaveable(key = key) { mutableIntStateOf(initial) }

@Composable
internal fun rememberDraft(
    key: String,
    initial: Long,
): MutableLongState = rememberSaveable(key = key) { mutableLongStateOf(initial) }
