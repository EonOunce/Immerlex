package com.immerlex.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 下载是"把整台机器的库换成文件里那份"，所以判定必须算得准：
 * 报太松会静默丢掉用户刚写的记录，报太紧用户天天被问，两头都是产品事故。
 */
class SyncGuardTest {

    private fun state(words: Int = 10, chapters: Int = 3, reviews: Int = 20, last: Long = 20) =
        LocalState(words, chapters, reviews, last)

    private fun lineageAt(s: LocalState, name: String = "immerlex-2026-10-28T080503-phone-v14.db.gz") =
        Lineage(device = "phone", syncedName = name, synced = s)

    @Test
    fun anEmptyDeviceHasNothingToLose() {
        assertEquals(
            DownloadVerdict.NothingToLose,
            SyncGuard.verdict(LocalState(0, 0, 0, 0), Lineage.empty("pad")),
        )
    }

    @Test
    fun aBankImportedButNeverStudiedIsStillLocalWork() {
        // 平板自己导入了 800 个词、一个还没学过：拉下来那一份里没有它们，就是本机丢东西，照样要问。
        assertEquals(
            DownloadVerdict.LocalUnsynced(800, 0, 0),
            SyncGuard.verdict(LocalState(800, 0, 0, 0), Lineage.empty("pad")),
        )
        // 已经和云端对齐过、之后一个词没加没减，就放行。
        val bank = state(words = 800, chapters = 0, reviews = 0, last = 0)
        assertEquals(DownloadVerdict.InSync, SyncGuard.verdict(bank, lineageAt(bank)))
    }

    @Test
    fun aDeviceThatNeverUploadedIsAskedFirst() {
        // 没有血缘就没有依据，宁可问一句也不猜"你应该没动过"。
        val verdict = SyncGuard.verdict(state(), Lineage.empty("pad"))
        assertEquals(DownloadVerdict.LocalUnsynced(10, 3, 20), verdict)
    }

    @Test
    fun nothingWrittenSinceTheLastSyncDownloadsSilently() {
        val s = state()
        assertEquals(DownloadVerdict.InSync, SyncGuard.verdict(s, lineageAt(s)))
    }

    @Test
    fun recordsWrittenAfterTheLastSyncAreCounted() {
        val verdict = SyncGuard.verdict(state(reviews = 27, last = 27), lineageAt(state()))
        assertEquals(DownloadVerdict.LocalUnsynced(words = 0, chapters = 0, reviews = 7), verdict)
    }

    @Test
    fun newWordsAndChaptersCountToo() {
        // 只学了几个词不算改动，但往词库里加了词、写了新章一样会被整库覆盖掉。
        val verdict = SyncGuard.verdict(state(words = 40, chapters = 5, reviews = 20), lineageAt(state()))
        assertEquals(DownloadVerdict.LocalUnsynced(words = 30, chapters = 2, reviews = 0), verdict)
    }

    @Test
    fun undoingIsStillAChangeButNeverANegativeCount() {
        val verdict = SyncGuard.verdict(state(reviews = 18, last = 27), lineageAt(state(reviews = 20, last = 20)))
        val unsynced = verdict as DownloadVerdict.LocalUnsynced
        assertEquals("撤销让条数往回走，报负数没有意义", 0, unsynced.reviews)
    }

    @Test
    fun theSameCountsWithANewerRowIsStillAChange() {
        // 删一条又加一条，条数没变但自增 id 前进了：这也算动过，界面只会说"本机又动过"而不报数目。
        val verdict = SyncGuard.verdict(state(reviews = 20, last = 21), lineageAt(state(reviews = 20, last = 20)))
        assertEquals(DownloadVerdict.LocalUnsynced(words = 0, chapters = 0, reviews = 0), verdict)
    }

    @Test
    fun theSameCountsOnADifferentClockAreTheSameVerdict() {
        // 判定里没有任何时刻：两台机器的时钟本来就互不可信，服务器的 mtime 也不算数。
        val s = state()
        val early = lineageAt(s).copy(lastUploadSec = 1_700_000_000)
        val late = lineageAt(s).copy(lastUploadSec = 2_000_000_000, lastRestoreSec = 1)
        assertEquals(SyncGuard.verdict(s, early), SyncGuard.verdict(s, late))
        assertEquals(DownloadVerdict.InSync, SyncGuard.verdict(s, late))
    }

    @Test
    fun aDeviceThatOnlyImportedMaterialIsNotEmpty() {
        // 一个词都没学，但整包素材已经导进来了：那份文件里没有它，下载照样会把它盖掉。
        val withMaterial = LocalState(0, 0, 0, 0, extras = SyncFingerprint.render(mapOf("quiz_material" to 500)))
        assertFalse(withMaterial.isEmpty)
        assertEquals(
            DownloadVerdict.LocalUnsynced(words = 0, chapters = 0, reviews = 0),
            SyncGuard.verdict(withMaterial, Lineage.empty("pad")),
        )
    }

    @Test
    fun materialTrickyBibleAndRoommateChangesCountToo() {
        // 词/章/记录三个数一个没动，只改了素材或易错关系：那也是本机才有的东西。
        val synced = LocalState(10, 3, 20, 20, SyncFingerprint.render(mapOf("quiz_material" to 30)))
        val now = LocalState(10, 3, 20, 20, SyncFingerprint.render(mapOf("quiz_material" to 31)))
        assertEquals(
            DownloadVerdict.LocalUnsynced(words = 0, chapters = 0, reviews = 0),
            SyncGuard.verdict(now, lineageAt(synced)),
        )
        assertEquals(DownloadVerdict.InSync, SyncGuard.verdict(synced, lineageAt(synced)))
    }

    @Test
    fun aLineageWrittenBeforeTheFingerprintExistedAsksOnce() {
        // 老行这一列是空串，与任何真实指纹都不相等：判定不了就问一句，这比直接放行安全。
        val legacy = LocalState(10, 3, 20, 20, extras = "")
        assertEquals(
            DownloadVerdict.LocalUnsynced(words = 0, chapters = 0, reviews = 0),
            SyncGuard.verdict(state(), lineageAt(legacy)),
        )
    }

    @Test
    fun yourOwnLastUploadIsStillNotNewerThanWhatYouWroteAfterIt() {
        // 拉回来的哪怕是自己传的那一份，之后新写的记录照样会没——所以一样要问。
        val mine = lineageAt(state(reviews = 20, last = 20), name = "immerlex-2026-10-28T080503-phone-v14.db.gz")
        assertEquals(
            DownloadVerdict.LocalUnsynced(words = 0, chapters = 0, reviews = 7),
            SyncGuard.verdict(state(reviews = 27, last = 27), mine),
        )
    }
}
