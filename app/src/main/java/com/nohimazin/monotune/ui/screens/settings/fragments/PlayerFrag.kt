package com.nohimazin.monotune.ui.screens.settings.fragments

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.nohimazin.monotune.R
import com.nohimazin.monotune.constants.AudioNormalizationKey
import com.nohimazin.monotune.constants.AudioQuality
import com.nohimazin.monotune.constants.AudioQualityKey
import com.nohimazin.monotune.constants.AutoLoadMoreKey
import com.nohimazin.monotune.constants.KeepAliveKey
import com.nohimazin.monotune.constants.SeekIncrement
import com.nohimazin.monotune.constants.SeekIncrementKey
import com.nohimazin.monotune.constants.SkipOnErrorKey
import com.nohimazin.monotune.constants.SkipSilenceKey
import com.nohimazin.monotune.constants.StopMusicOnTaskClearKey
import com.nohimazin.monotune.constants.minPlaybackDurKey
import com.nohimazin.monotune.ui.component.EnumListPreference
import com.nohimazin.monotune.ui.component.PreferenceEntry
import com.nohimazin.monotune.ui.component.SwitchPreference
import com.nohimazin.monotune.ui.dialog.CounterDialog
import com.nohimazin.monotune.utils.rememberEnumPreference
import com.nohimazin.monotune.utils.rememberPreference

@Composable
fun PlayerGeneralFrag() {
    val (autoLoadMore, onAutoLoadMoreChange) = rememberPreference(AutoLoadMoreKey, defaultValue = true)

    val context = LocalContext.current
    val (seekIncrement, onSeekIncrementChange) = rememberEnumPreference(
        key = SeekIncrementKey,
        defaultValue = SeekIncrement.OFF
    )

    SwitchPreference(
        title = { Text(stringResource(R.string.auto_load_more)) },
        description = stringResource(R.string.auto_load_more_desc),
        icon = { Icon(Icons.Rounded.Autorenew, null) },
        checked = autoLoadMore,
        onCheckedChange = onAutoLoadMoreChange
    )
    EnumListPreference(
        title = { Text(stringResource(R.string.seek_increment))},
        icon = { Icon(Icons.Rounded.FastForward, null) },
        selectedValue = seekIncrement,
        onValueSelected = onSeekIncrementChange,
        valueText = {
            seekIncrement -> SeekIncrement.getString(context, seekIncrement)
        }
    )
}

@Composable
fun PlayerServiceFrag() {

}

@Composable
fun AudioQualityFrag() {
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
        key = AudioQualityKey,
        defaultValue = AudioQuality.AUTO
    )

    EnumListPreference(
        title = { Text(stringResource(R.string.audio_quality)) },
        icon = { Icon(Icons.Rounded.GraphicEq, null) },
        selectedValue = audioQuality,
        onValueSelected = onAudioQualityChange,
        valueText = {
            when (it) {
                AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                AudioQuality.LOSSLESS -> stringResource(R.string.audio_quality_lossless)
                AudioQuality.HI_RES_LOSSLESS -> stringResource(R.string.audio_quality_hi_res)
            }
        }
    )

}

@Composable
fun AudioEffectsFrag() {
    val (skipSilence, onSkipSilenceChange) = rememberPreference(key = SkipSilenceKey, defaultValue = false)

    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(
        key = AudioNormalizationKey,
        defaultValue = true
    )

    SwitchPreference(
        title = { Text(stringResource(R.string.audio_normalization)) },
        icon = { Icon(Icons.AutoMirrored.Rounded.VolumeUp, null) },
        checked = audioNormalization,
        onCheckedChange = onAudioNormalizationChange
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.skip_silence)) },
        icon = { Icon(painterResource(R.drawable.skip_next), null) },
        checked = skipSilence,
        onCheckedChange = onSkipSilenceChange
    )

}

@Composable
fun PlaybackBehaviourFrag() {
    val keepAlive by rememberPreference(key = KeepAliveKey, defaultValue = false)
    val (minPlaybackDur, onMinPlaybackDurChange) = rememberPreference(minPlaybackDurKey, defaultValue = 30)
    val (skipOnErrorKey, onSkipOnErrorChange) = rememberPreference(key = SkipOnErrorKey, defaultValue = false)
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) = rememberPreference(
        key = StopMusicOnTaskClearKey,
        defaultValue = false
    )

    var showMinPlaybackDur by remember {
        mutableStateOf(false)
    }

    PreferenceEntry(
        title = { Text(stringResource(R.string.min_playback_duration)) },
        icon = { Icon(Icons.Rounded.Sync, null) },
        onClick = { showMinPlaybackDur = true }
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.auto_skip_next_on_error)) },
        description = stringResource(R.string.auto_skip_next_on_error_desc),
        icon = { Icon(Icons.Rounded.SkipNext, null) },
        checked = skipOnErrorKey,
        onCheckedChange = onSkipOnErrorChange
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.stop_music_on_task_clear)) },
        icon = { Icon(Icons.Rounded.ClearAll, null) },
        isEnabled = !keepAlive,
        checked = stopMusicOnTaskClear,
        onCheckedChange = onStopMusicOnTaskClearChange,
    )

    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */


    if (showMinPlaybackDur) {
        CounterDialog(
            title = stringResource(R.string.min_playback_duration),
            description = stringResource(R.string.min_playback_duration_description),
            initialValue = minPlaybackDur,
            upperBound = 100,
            lowerBound = 0,
            unitDisplay = "%",
            onDismiss = { showMinPlaybackDur = false },
            onConfirm = {
                showMinPlaybackDur = false
                onMinPlaybackDurChange(it)
            },
            onCancel = {
                showMinPlaybackDur = false
            }
        )
    }
}
