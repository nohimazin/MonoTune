package com.nohimazin.monotune.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.core.net.toUri
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.nohimazin.monotune.constants.AudioQuality
import com.nohimazin.monotune.constants.AudioQualityKey
import com.nohimazin.monotune.constants.DownloadAudioQualityKey
import com.nohimazin.monotune.constants.DownloadExtraPathKey
import com.nohimazin.monotune.constants.DownloadPathKey
import com.nohimazin.monotune.constants.TranscodeBitrateKey
import com.nohimazin.monotune.constants.TranscodeEnabledKey
import com.nohimazin.monotune.constants.TranscodeFormatKey
import com.nohimazin.monotune.db.MusicDatabase
import com.nohimazin.monotune.db.entities.PlaylistSong
import com.nohimazin.monotune.db.entities.Song
import com.nohimazin.monotune.db.entities.SongEntity
import com.nohimazin.monotune.di.AppModule.PlayerCache
import com.nohimazin.monotune.di.DownloadCache
import com.nohimazin.monotune.models.MediaMetadata
import com.nohimazin.monotune.models.toMediaMetadata
import com.nohimazin.monotune.playback.downloadManager.DownloadEvent
import com.nohimazin.monotune.playback.DownloadUtil.Companion.STATE_DOWNLOADING
import com.nohimazin.monotune.playback.DownloadUtil.Companion.STATE_INVALID
import com.nohimazin.monotune.playback.downloadManager.DownloadDirectoryManagerOt
import com.nohimazin.monotune.playback.downloadManager.DownloadManagerOt
import com.nohimazin.monotune.utils.dataStore
import com.nohimazin.monotune.utils.dlCoroutine
import com.nohimazin.monotune.utils.get
import com.nohimazin.monotune.utils.reportException

import com.nohimazin.monotune.monochrome.MonochromeClientApi
import com.nohimazin.monotune.monochrome.MonochromeResult
import com.nohimazin.monotune.utils.fileFromUri
import com.nohimazin.monotune.utils.uriListFromString
import com.nohimazin.monotune.extensions.toEnum
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    val databaseProvider: DatabaseProvider,
    @DownloadCache val downloadCache: SimpleCache,
    @PlayerCache val playerCache: SimpleCache,
    val monochromeClient: MonochromeClientApi,
    val monochromeSearchMatcher: com.nohimazin.monotune.monochrome.MonochromeSearchMatcher,
    val audioTranscoder: AudioTranscoder,
) {
    val TAG = DownloadUtil::class.simpleName.toString()

    private val dataSourceFactory = ResolvingDataSource.Factory(
        CacheDataSource.Factory()
            .setCache(playerCache)
            .setUpstreamDataSourceFactory(
                OkHttpDataSource.Factory(OkHttpClient.Builder().build())
            )
    ) { dataSpec ->
        val mediaId = dataSpec.key ?: error("No media id")
        val length = if (dataSpec.length >= 0) dataSpec.length else 1
        // 1. Resolve Monochrome ID - playback/download is forbidden without it.
        var monochromeId: String? = runBlocking(Dispatchers.IO) {
            val manual = database.getManualCorrection(mediaId)
            when {
                manual != null -> manual.correctedMonochromeId
                else -> database.getTrackMatch(mediaId)?.monochromeId
            }
        }

        // 2. Just-in-time matching if no metadata link exists
        if (monochromeId == null) {
            Log.d(TAG, "DOWNLOAD: No Monochrome ID for mediaId=$mediaId, attempting just-in-time match")
            val song = runBlocking(Dispatchers.IO) {
                database.song(mediaId).first()?.toMediaMetadata()
            }
            if (song != null) {
                val match = runBlocking(Dispatchers.IO) {
                    monochromeSearchMatcher.matchSong(song)
                }
                if (match != null) {
                    monochromeId = match.monochromeId
                    runBlocking(Dispatchers.IO) {
                        database.upsertTrackMatch(
                            com.nohimazin.monotune.db.entities.MonochromeTrackMatch(
                                ytmId = mediaId,
                                monochromeId = match.monochromeId,
                                confidence = 0.7f,
                                matchedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }

        if (monochromeId == null) {
            Log.w(TAG, "DOWNLOAD: No Monochrome ID for mediaId=$mediaId, download unavailable")
            throw IOException("No Monochrome stream available for $mediaId")
        }

        // 3. Check cache only AFTER confirming we have a valid Monochrome match.
        if (playerCache.isCached(mediaId, dataSpec.position, length)) {
            return@Factory dataSpec
        }

        // 4. Resolve the Monochrome stream URL with user-selected quality.
        val quality = runBlocking(Dispatchers.IO) {
            context.dataStore.data.first()[DownloadAudioQualityKey]?.toEnum(AudioQuality.AUTO) ?: AudioQuality.AUTO
        }
        val qualityTokens = quality.toMonochromeQualityTokens()
        var lastResolveError: String? = null

        for (qualityToken in qualityTokens) {
            try {
                val monochromeResult = runBlocking(Dispatchers.IO) {
                    monochromeClient.resolveStreamUrl(monochromeId!!, quality = qualityToken)
                }
                when (monochromeResult) {
                    is MonochromeResult.Success -> {
                        if (qualityToken != qualityTokens.first()) {
                            Log.w(TAG, "DOWNLOAD: quality fallback applied ${qualityTokens.first()} -> $qualityToken")
                        }
                        Log.d(TAG, "DOWNLOAD: Monochrome stream resolved for monochromeId=$monochromeId")
                        try {
                            return@Factory dataSpec.withUri(monochromeResult.data.toUri())
                        } catch (e: IllegalArgumentException) {
                            lastResolveError = "Invalid stream URI: ${e.message}"
                            Log.w(TAG, "DOWNLOAD: URI parsing failed for stream URL: $lastResolveError", e)
                        }
                    }
                    is MonochromeResult.Error -> {
                        lastResolveError = monochromeResult.message
                        Log.w(
                            TAG,
                            "DOWNLOAD: Monochrome stream resolution failed for monochromeId=$monochromeId (quality=$qualityToken): ${monochromeResult.message}"
                        )
                    }
                }
            } catch (e: IOException) {
                lastResolveError = "IO error during stream resolution: ${e.message}"
                Log.w(TAG, "DOWNLOAD: IOException in stream resolution loop (quality=$qualityToken)", e)
            } catch (e: Exception) {
                lastResolveError = "Unexpected error during stream resolution: ${e.message}"
                Log.e(TAG, "DOWNLOAD: Unexpected exception in stream resolution loop (quality=$qualityToken)", e)
            }
        }

        throw IOException("Monochrome stream resolution failed after all quality fallbacks. Last error: $lastResolveError")
    }
    val downloadNotificationHelper = DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)
    val downloadManager: DownloadManager =
        DownloadManager(context, databaseProvider, downloadCache, dataSourceFactory, Executor(Runnable::run)).apply {
            maxParallelDownloads = 3
            addListener(
                ExoDownloadService.TerminalStateNotificationHelper(
                    context = context,
                    notificationHelper = downloadNotificationHelper,
                    nextNotificationId = ExoDownloadService.NOTIFICATION_ID + 1
                )
            )
        }
    val downloads = MutableStateFlow<Map<String, LocalDateTime>>(emptyMap())

    var localMgr = DownloadDirectoryManagerOt(
        context,
        context.dataStore.get(DownloadPathKey, "").toUri(),
        uriListFromString(context.dataStore.get(DownloadExtraPathKey, ""))
    )
    val downloadMgr = DownloadManagerOt(localMgr)
    var isProcessingDownloads = MutableStateFlow(false)

    fun getDownload(songId: String): Flow<LocalDateTime?> = downloads.map { it[songId] }

    fun download(songs: List<MediaMetadata>) {
        songs.forEach { song -> downloadSong(song.id, song.title) }
    }

    fun download(song: MediaMetadata) {
        downloadSong(song.id, song.title)
    }

    fun download(song: SongEntity) {
        downloadSong(song.id, song.title)
    }

    private fun downloadSong(id: String, title: String) {
        if (downloads.value[id] != null) return
        val downloadRequest = DownloadRequest.Builder(id, id.toUri())
            .setCustomCacheKey(id)
            .setData(title.toByteArray())
            .build()
        DownloadService.sendAddDownload(
            context,
            ExoDownloadService::class.java,
            downloadRequest,
            false
        )
    }

    fun resumeDownloadsOnStart() {
        DownloadService.sendResumeDownloads(
            context,
            ExoDownloadService::class.java,
            false
        )
    }


// Deletes from custom dl

    fun delete(song: PlaylistSong) = deleteSong(song.song.id)

    fun delete(song: SongItem) = deleteSong(song.id)

    fun delete(song: Song) = deleteSong(song.song.id)

    fun delete(song: SongEntity) = deleteSong(song.id)

    fun delete(song: MediaMetadata) = deleteSong(song.id)

    private fun deleteSong(id: String): Boolean {
        val deleted = localMgr.deleteFile(id)
        if (!deleted) return false
        downloads.update { map ->
            map.toMutableMap().apply {
                remove(id)
            }
        }

        runBlocking {
            database.song(id).first()?.song?.copy(localPath = null)
            database.updateDownloadStatus(id, null)
        }
        return true
    }

    /**
     * Retrieve song from cache, and delete it from cache afterwards
     */
    fun getFromCache(cache: SimpleCache, mediaId: String): ByteArray? {
        val spans: Set<CacheSpan> = cache.getCachedSpans(mediaId)
        if (spans.isEmpty()) return null

        val output = ByteArrayOutputStream()
        try {
            for (span in spans) {
                val file: File? = span.file
                FileInputStream(file).use { fis ->
                    fis.copyTo(output)
                }
            }
            return output.toByteArray()
        } catch (e: IOException) {
            reportException(e)
        } finally {
            output.close()
        }
        return null
    }

    /**
     * Migrated existing downloads from the download cache to the new system in external storage
     */
    suspend fun migrateDownloads() {
        if (isProcessingDownloads.value) return
        isProcessingDownloads.value = true

        var runs = 0
        try {
            // "skeleton" of old download manager to access old download data
            val dataSourceFactory = ResolvingDataSource.Factory(
                CacheDataSource.Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        OkHttpDataSource.Factory(
                            OkHttpClient.Builder()
                                .proxy(YouTube.proxy)
                                .build()
                        )
                    )
            ) { dataSpec ->
                return@Factory dataSpec
            }

            val downloadManager: DownloadManager = DownloadManager(
                context,
                databaseProvider,
                downloadCache,
                dataSourceFactory,
                Executor(Runnable::run)
            ).apply {
                maxParallelDownloads = 3
            }

            // actual migration code
            val downloadedSongs = mutableMapOf<String, Download>()
            val cursor = downloadManager.downloadIndex.getDownloads()
            while (cursor.moveToNext()) {
                downloadedSongs[cursor.download.request.id] = cursor.download
            }

            // copy all completed downloads
            val toMigrate = downloadedSongs.filter { it.value.state == Download.STATE_COMPLETED }
            toMigrate.forEach { s ->
                if (runs++ % 10 == 0) {
                    Log.d(TAG, "Migrating download: $runs/${toMigrate.size}")
                    if (runs % 20 == 0) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "$runs/${toMigrate.size}", LENGTH_SHORT).show()
                        }
                    }
                }
                val songFromCache = getFromCache(downloadCache, s.key)
                if (songFromCache != null) {
                    downloadCache.removeResource(s.key)
                    downloadMgr.enqueue(
                        mediaId = s.key,
                        data = songFromCache,
                        displayName = runBlocking { database.song(s.key).first()?.title ?: "" })
                }
            }
            scanDownloads()
        } catch (e: Exception) {
            reportException(e)
        } finally {
            isProcessingDownloads.value = false
        }
    }


    fun cd() {
        localMgr.doInit(
            context,
            context.dataStore.get(DownloadPathKey, "").toUri(),
            uriListFromString(context.dataStore.get(DownloadExtraPathKey, ""))
        )
    }

    /**
     * Rescan download directory and updates songs
     */
    suspend fun rescanDownloads() {
        Log.i(TAG, "+rescanDownloads()")
        isProcessingDownloads.value = true
        val dbDownloads = database.downloadedOrQueuedSongs().first()
        val result = mutableMapOf<String, LocalDateTime>()

        // get missing files not in custom downloads or in internal downloads, remove them
        val missingFiles =
            localMgr.getMissingFiles(dbDownloads.filterNot { it.song.dateDownload == null }).toMutableList()
        Log.d(TAG, "Found ${missingFiles.size}/${dbDownloads.size} songs not in custom download directories")
        val cursor = downloadManager.downloadIndex.getDownloads()
        while (cursor.moveToNext()) {
            missingFiles.removeIf { it.id == cursor.download.request.id }
        }
        Log.d(
            TAG,
            "Found ${missingFiles.size}/${dbDownloads.size} song not in custom download directories + internal cache. Removing these files now"
        )

        database.transaction {
            missingFiles.forEach {
                Log.v(TAG, "Shedding: [${it.id}] ${it.song.title}")
                removeDownloadSong(it.song.id)
            }
        }

        // new files
        val availableDownloads = dbDownloads.minus(missingFiles)
        availableDownloads.forEach { s ->
            result[s.song.id] = s.song.dateDownload!! // sql should cover our butts
        }

        downloads.value = result
        isProcessingDownloads.value = false
        Log.i(TAG, "-rescanDownloads()")
    }


    /**
     * Scan and import downloaded songs from main and extra directories.
     *
     * This is intended for re-importing existing songs (ex. songs get moved, after restoring app backup), thus all
     * songs will already need to exist in the database.
     */
    suspend fun scanDownloads() {
        Log.i(TAG, "+scanDownloads()")
        if (isProcessingDownloads.value) {
            Log.i(TAG, "-scanDownloads()")
            return
        }
        isProcessingDownloads.value = true

//            val scanner = LocalMediaScanner.getScanner(context, ScannerImpl.TAGLIB, SCANNER_OWNER_DL)
        database.removeAllDownloadedSongs()
        val timeNow = LocalDateTime.now()

        // add custom downloads
        val availableFiles = localMgr.getAvailableFiles(false)
        database.transaction {
            availableFiles.forEach { f ->
                try {
                    val file = fileFromUri(context, f.value)
                    if (file == null) throw (IOException("Cannot resolve download file path"))
                    // TODO: validate files in download folder
//                        val format: FormatEntity? = scanner.advancedScan(f.value).format
//                        if (format != null) {
//                            database.upsert(format)
//                        }
                    registerDownloadSong(f.key, timeNow, file.absolutePath)

                } catch (e: IOException) {
                    reportException(e)
                }
            }
        }
//            LocalMediaScanner.destroyScanner(SCANNER_OWNER_DL)
        Log.d(TAG, "Registered ${availableFiles.size} files from custom downloads")

        // add internal downloads
        val cursor = downloadManager.downloadIndex.getDownloads()
        var count = 0
        database.transaction {
            while (cursor.moveToNext()) {
                updateDownloadStatus(cursor.download.request.id, stateToLocalDateTime(cursor.download))
                count ++
            }
        }
        Log.d(TAG, "Registered $count files from internal downloads")
        isProcessingDownloads.value = false
        Log.d(TAG, "Database registration complete, triggering map registry rebuild")
        rescanDownloads()
        Log.i(TAG, "-scanDownloads()")
    }

    companion object {
        val STATE_DOWNLOADING: LocalDateTime = Instant.ofEpochMilli(1).atZone(ZoneOffset.UTC).toLocalDateTime()
        val STATE_INVALID: LocalDateTime = Instant.ofEpochMilli(0).atZone(ZoneOffset.UTC).toLocalDateTime()
    }


    init {
        Log.i(TAG, "DownloadUtil init")
        // TODO: make sure db is update when download is queued
        CoroutineScope(dlCoroutine).launch {
            rescanDownloads()
        }

        CoroutineScope(dlCoroutine).launch {
            downloadMgr.events.collect { event ->
                if (event is DownloadEvent.Success) {
                    handlePostDownload(event.mediaId, event.file)
                }
            }
        }

        downloadManager.addListener(
            object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) {
                    downloads.update { map ->
                        map.toMutableMap().apply {
                            val state = stateToLocalDateTime(download)
                            if (state == STATE_INVALID) {
                                Log.w(TAG, "Invalid download state for ${download.request.id}. Removing download")
                                remove(download.request.id)
                            } else {
                                set(download.request.id, state)
                            }
                        }
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        if (download.state == Download.STATE_COMPLETED) {
                            val updateTime =
                                Instant.ofEpochMilli(download.updateTimeMs).atZone(ZoneOffset.UTC).toLocalDateTime()
                            database.updateDownloadStatus(download.request.id, updateTime)
                        } else {
                            database.updateDownloadStatus(download.request.id, null)
                        }
                    }
                }
            }
        )
    }

    private suspend fun handlePostDownload(mediaId: String, fileUri: Uri) {
        val enabled = context.dataStore.data.first()[TranscodeEnabledKey] ?: false
        if (!enabled) return

        val format = context.dataStore.data.first()[TranscodeFormatKey] ?: "AAC"
        val bitrate = context.dataStore.data.first()[TranscodeBitrateKey] ?: 128

        withContext(Dispatchers.IO) {
            val sourceFile = fileFromUri(context, fileUri) ?: return@withContext
            val sourceMetadataSnapshot = TranscodeMetadataVerifier.snapshotFrom(sourceFile)
            val formatProfile = AudioTranscoder.profileForFormat(format)
            // Use a temporary file for transcoding
            val tempFile = File(sourceFile.parent, "${sourceFile.nameWithoutExtension}.transcoded.${formatProfile.extension}")

            val success = audioTranscoder.transcode(sourceFile, tempFile, format, bitrate)

            if (success && tempFile.exists()) {
                val replaced = replaceSourceWithTemp(sourceFile, tempFile)
                if (!replaced) {
                    Log.e(TAG, "Transcoding output could not replace source file for $mediaId")
                    if (tempFile.exists()) tempFile.delete()
                    return@withContext
                }

                val transcodedMetadataSnapshot = TranscodeMetadataVerifier.snapshotFrom(sourceFile)
                if (sourceMetadataSnapshot != null && transcodedMetadataSnapshot != null) {
                    val verificationResult = TranscodeMetadataVerifier.verify(sourceMetadataSnapshot, transcodedMetadataSnapshot)
                    if (!verificationResult.isSuccess) {
                        Log.w(TAG, "Metadata mismatch after transcoding for $mediaId: ${verificationResult.describe()}")
                    }
                } else {
                    Log.d(TAG, "Skipped metadata verification for $mediaId because snapshot extraction was unavailable")
                }
                Log.d(TAG, "Transcoding successful for $mediaId. Format: $format, Bitrate: ${bitrate}k")
            } else {
                Log.e(TAG, "Transcoding failed for $mediaId")
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }

    private fun replaceSourceWithTemp(sourceFile: File, tempFile: File): Boolean {
        if (!sourceFile.delete()) {
            Log.e(TAG, "Failed to delete original file before replacement: ${sourceFile.absolutePath}")
            return false
        }

        if (tempFile.renameTo(sourceFile)) {
            return true
        }

        return try {
            tempFile.copyTo(sourceFile, overwrite = true)
            tempFile.delete()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to replace original file with transcoded one: ${e.message}")
            false
        }
    }

    suspend fun batchTranscode(onProgress: (Int, Int) -> Unit) {
        val downloadedSongs = database.downloadedOrQueuedSongs().first().filter { it.song.localPath != null }
        val total = downloadedSongs.size
        downloadedSongs.forEachIndexed { index, song ->
            onProgress(index + 1, total)
            song.song.localPath?.let { path ->
                handlePostDownload(song.song.id, File(path).toUri())
            }
        }
    }
}

fun stateToLocalDateTime(download: Download): LocalDateTime {
    return when (download.state) {
        Download.STATE_COMPLETED -> {
            Instant.ofEpochMilli(download.updateTimeMs).atZone(ZoneOffset.UTC).toLocalDateTime()
        }

        Download.STATE_DOWNLOADING, Download.STATE_QUEUED -> STATE_DOWNLOADING
        else -> STATE_INVALID
    }
}
