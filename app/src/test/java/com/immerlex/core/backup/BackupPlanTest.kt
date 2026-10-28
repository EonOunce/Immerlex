package com.immerlex.core.backup

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 云端"版本历史"其实就是文件名列表，所以命名与保留这两件事必须算得准：
 * 算错的代价是删掉用户唯一一份旧备份。
 */
class BackupPlanTest {

    private fun name(day: LocalDate) = BackupNaming.remoteName(day)

    @Test
    fun oneFilePerDayBecauseTheNameIsTheDay() {
        assertEquals("immerlex-2026-10-28.db.gz", name(LocalDate.of(2026, 10, 28)))
        assertEquals(name(LocalDate.of(2026, 10, 28)), name(LocalDate.of(2026, 10, 28)))
    }

    @Test
    fun onlyOurOwnFileNameHasADate() {
        val good = "/dav/Immerlex/immerlex-2026-10-28.db.gz"
        assertEquals(LocalDate.of(2026, 10, 28), BackupNaming.dateOf(good))
        assertNull(BackupNaming.dateOf("notes.txt"))
        assertNull(BackupNaming.dateOf("immerlex-2026-10-28.db"))
        assertNull(BackupNaming.dateOf("immerlex-20261028.db.gz"))
        // 形状对但日子不存在，也不能当历史算。
        assertNull(BackupNaming.dateOf("immerlex-2026-02-30.db.gz"))
        assertNull(BackupNaming.dateOf(""))
    }

    @Test
    fun nothingGoesAwayUntilThereAreMoreThanThirty() {
        val days = (0 until Retention.KEEP_RECENT).map { LocalDate.of(2026, 10, 1).plusDays(it.toLong()) }
        assertTrue(Retention.toDelete(days.map(::name)).isEmpty())
    }

    @Test
    fun overflowKeepsTheNewestThirtyPlusOnePerOlderMonth() {
        // 6-01..7-31 共 61 份：最新的 30 份是 7-02..7-31，剩下的尾巴里 7 月和 6 月各留最新一份。
        val days = (0L until 61L).map { LocalDate.of(2026, 6, 1).plusDays(it) }
        val doomed = Retention.toDelete(days.map(::name))

        assertEquals(29, doomed.size)
        assertFalse(doomed.contains(name(LocalDate.of(2026, 6, 30))))
        assertFalse(doomed.contains(name(LocalDate.of(2026, 7, 1))))
        assertFalse(doomed.contains(name(LocalDate.of(2026, 7, 31))))
        assertTrue(doomed.contains(name(LocalDate.of(2026, 6, 1))))
        assertTrue(doomed.contains(name(LocalDate.of(2026, 6, 29))))
    }

    @Test
    fun foreignNamesSurviveEvenAmplentyOfOurs() {
        // 9-01..10-10 共 40 份：留最新 30 份（10-10..9-11）+ 9 月尾巴里最新的 9-10，其余 9 份是 9-01..9-09。
        val days = (0L until 40L).map { LocalDate.of(2026, 9, 1).plusDays(it) }
        val doomed = Retention.toDelete(days.map(::name) + listOf("backup.db.gz", "词库.txt", "Immerlex/"))
        assertTrue(doomed.none { it in listOf("backup.db.gz", "词库.txt", "Immerlex/") })
        assertEquals(9, doomed.size)
        assertFalse(doomed.contains(name(LocalDate.of(2026, 9, 10))))
    }

    @Test
    fun readsAPropsResponseWithAnyNamespacePrefix() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/Immerlex/</d:href>
                <d:propstat><d:prop><d:getcontentlength/></d:prop></d:propstat>
              </d:response>
              <D:response>
                <D:href>/dav/Immerlex/immerlex-2026-10-28.db.gz</D:href>
                <D:propstat><D:prop><D:getcontentlength>1540</D:getcontentlength>
                  <D:displayname>immerlex-2026-10-28.db.gz</D:displayname></D:prop></D:propstat>
              </D:response>
              <response>
                <href>/dav/Immerlex/%E8%AF%8D%E5%BA%93.txt</href>
                <getcontentlength>7</getcontentlength>
              </response>
            </d:multistatus>
        """.trimIndent()

        val files = Propfind.parse(xml)

        // 目录那一行的 href 以 / 结尾，名字是空的，不该出现在列表里。
        assertEquals(2, files.size)
        assertEquals("immerlex-2026-10-28.db.gz", files[0].name)
        assertEquals(1540L, files[0].sizeBytes)
        assertEquals("词库.txt", files[1].name)
        assertEquals(7L, files[1].sizeBytes)
    }

    @Test
    fun aMissingContentLengthReadsAsZeroInsteadOfThrowing() {
        val xml = """
            <D:multistatus xmlns:D="DAV:"><D:response>
            <D:href>/dav/Immerlex/immerlex-2026-10-27.db.gz</D:href>
            <D:propstat><D:prop><D:creationdate>2026-10-27T01:02:03Z</D:creationdate></D:prop></D:propstat>
            </D:response></D:multistatus>
        """.trimIndent()
        assertEquals(listOf(RemoteFile("immerlex-2026-10-27.db.gz", 0L)), Propfind.parse(xml))
    }

    @Test
    fun emptyOrGarbageBodyYieldsNoFiles() {
        assertTrue(Propfind.parse("").isEmpty())
        assertTrue(Propfind.parse("<html><body>502 Bad Gateway</body></html>").isEmpty())
    }
}
