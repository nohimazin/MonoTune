import com.zionhuang.kugou.KuGou
import com.zionhuang.kugou.KuGou.generateKeyword
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
 
class KugouNetworkTest {
    private fun assumeNetworkTestsEnabled() {
        val enabled = (System.getenv("KUGOU_NETWORK_TESTS") ?: System.getProperty("kugouNetworkTests"))
            ?.equals("true", ignoreCase = true) == true
        assumeTrue("Set KUGOU_NETWORK_TESTS=true or -DkugouNetworkTests=true to run network tests.", enabled)
    }

    @Test
    fun fetchLyricsForChineseSongs() = runBlocking {
        assumeNetworkTestsEnabled()
        val candidates = KuGou.getLyricsCandidate(
            generateKeyword("千年以後 (After A Thousand Years)", "陳零九"),
            285
        )
        assertTrue(candidates != null)
        val downloadedLyrics = KuGou.getLyrics("楊丞琳", "點水", 259)
        assertTrue(downloadedLyrics.isSuccess)
    }

    @Test
    fun searchAlanWalkerSong() = runBlocking {
        assumeNetworkTestsEnabled()
        val songName = "Faded"
        val artistName = "Alan Walker"

        val keyword = generateKeyword(songName, artistName)
        val song = KuGou.searchSongs(keyword)

        assertTrue(song.data.info.isNotEmpty())

        val candidates = KuGou.getLyricsCandidate(
            keyword,
            song.data.info.first().duration
        )

        assertTrue(candidates != null)

        val downloadedLyrics = KuGou.getLyrics(songName, artistName, song.data.info.first().duration)
        assertTrue(downloadedLyrics.isSuccess)
    }
}
