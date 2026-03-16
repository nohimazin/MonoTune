package com.nohimazin.monotune

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavController
import com.nohimazin.monotune.constants.LastVersionKey
import com.nohimazin.monotune.constants.OOBE_VERSION
import com.nohimazin.monotune.constants.OobeStatusKey
import com.nohimazin.monotune.constants.UpdateAvailableKey
import com.nohimazin.monotune.db.MusicDatabase
import com.nohimazin.monotune.models.toMediaMetadata
import com.nohimazin.monotune.playback.DownloadUtil
import com.nohimazin.monotune.playback.PlayerConnection
import com.nohimazin.monotune.playback.queues.ListQueue
import com.nohimazin.monotune.utils.dataStore
import com.nohimazin.monotune.utils.get
import com.nohimazin.monotune.utils.reportException
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Directly navigate to a YouTube page given an YouTube url
 */
fun youtubeNavigator(
    context: Context,
    navController: NavController,
    coroutineScope: CoroutineScope,
    playerConnection: PlayerConnection?,
    snackbarHostState: SnackbarHostState,
    uri: Uri
): Boolean {
    when (val path = uri.pathSegments.firstOrNull()) {
        "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
            if (playlistId.startsWith("OLAK5uy_")) {
                coroutineScope.launch {
                    YouTube.albumSongs(playlistId).onSuccess { songs ->
                        songs.firstOrNull()?.album?.id?.let { browseId ->
                            navController.navigate("album/$browseId")
                        }
                    }.onFailure {
                        reportException(it)
                    }
                }
            } else {
                navController.navigate("online_playlist/$playlistId")
            }
        }

        "channel", "c" -> uri.lastPathSegment?.let { artistId ->
            navController.navigate("artist/$artistId")
        }

        else -> when {
            path == "watch" -> uri.getQueryParameter("v")
            uri.host == "youtu.be" -> path
            else -> return false
        }?.let { videoId ->
            val playlistId = uri.getQueryParameter("list")
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    YouTube.queue(listOf(videoId), playlistId)
                }.onSuccess {
                    val s = it.firstOrNull()
                    if (s == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.err_invalid_ytm_song),
                                withDismissAction = true,
                                duration = SnackbarDuration.Long
                            )
                        }
                    } else {
                        playerConnection?.playQueue(
                            queue = ListQueue(
                                title = s.title,
                                items = listOf(s.toMediaMetadata())
                            )
                        )
                    }
                }.onFailure {
                    reportException(it)
                }
            }
        }
    }

    return true
}

/**
 * Initialises the downloads scanner on app startup.
 */
suspend fun scanInit(
    context: Context,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    coroutineScope: CoroutineScope,
    playerConnection: PlayerConnection?,
    snackbarHostState: SnackbarHostState
) {
    val MAIN_TAG = "MainOtActivity"
    val oobeStatus = context.dataStore.get(OobeStatusKey, defaultValue = 0)

    if (oobeStatus < OOBE_VERSION) {
        Log.i(MAIN_TAG, "User has not completed OOBE, skipping startup scan")
        return
    }

    Log.i(MAIN_TAG, "Starting downloads scan")
    try {
        withContext(Dispatchers.IO) {
            downloadUtil.scanDownloads()
            downloadUtil.resumeDownloadsOnStart()
        }
        Log.i(MAIN_TAG, "Downloads scan complete")
    } catch (e: Exception) {
        Log.e(MAIN_TAG, "Downloads scan failed", e)
        reportException(e)
    }
    playerConnection?.service?.initQueue()
}
