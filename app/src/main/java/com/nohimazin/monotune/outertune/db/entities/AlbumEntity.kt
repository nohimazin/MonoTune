package com.nohimazin.monotune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nohimazin.monotune.utils.syncCoroutine
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.apache.commons.lang3.RandomStringUtils
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "album")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val playlistId: String? = null,
    val title: String,
    val year: Int? = null,
    val thumbnailUrl: String? = null,
    val themeColor: Int? = null,
    val songCount: Int,
    val duration: Int,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    val bookmarkedAt: LocalDateTime? = null,
) {

    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now()
    )

    fun toggleLike() = localToggleLike().also {
        CoroutineScope(syncCoroutine).launch {
            if (playlistId != null)
                YouTube.likePlaylist(playlistId, bookmarkedAt == null)
            this.cancel()
        }
    }

    companion object {
        fun generateAlbumId() = "LB" + RandomStringUtils.insecure().next(8, true, false)
    }
}