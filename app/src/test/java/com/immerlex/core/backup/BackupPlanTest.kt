package com.immerlex.core.backup

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 云端"版本历史"其实就是文件名列表，所以命名与保留这两件事必须算得准：
 * 算错的代价是删掉用户唯一一份旧备份。
 */
class BackupPlanTest {

    private fun at(y: Int, mo: Int, d: Int, h: Int = 8, mi: Int = 5, s: Int = 3) =
        LocalDateTime.of(y, mo, d, h, mi, s)

    private fun name(at: LocalDateTime, device: String = "phone", version: Int = 14) =
        BackupNaming.remoteName(at, device, version)

    private fun legacy(day: LocalDate) = "immerlex-$day.db.gz"

    private fun dayOf(raw: String) = BackupNaming.keyOf(raw)?.day

    @Test
    fun everyUploadKeepsItsOwnFile() {
        assertEquals("immerlex-2026-10-28T080503-phone-v14.db.gz", name(at(2026, 10, 28)))
        assertEquals(name(at(2026, 10, 28)), name(at(2026, 10, 28)))
        // 同一天再传一次不再顶掉上一份：云端没有历史，被顶掉的那份就再也找不回来。
        assertNotEquals(name(at(2026, 10, 28, 20, 30, 0)), name(at(2026, 10, 28, 8, 5, 3)))
        // 换个设备也是另一份文件。
        assertNotEquals(name(at(2026, 10, 28), device = "pad"), name(at(2026, 10, 28), device = "phone"))
    }

    @Test
    fun deviceNamesAreWashedIntoFileNameSafeOnes() {
        assertEquals("phone", BackupNaming.deviceTag("  Phone  "))
        assertEquals("23116pn5bc", BackupNaming.deviceTag("23116PN5BC"))
        assertEquals("xiaomi-14-pro", BackupNaming.deviceTag("Xiaomi 14 Pro"))
        assertEquals("huawei-matepad-pro-11", BackupNaming.deviceTag("HUAWEI MatePad Pro 11"))
        // 长到不像话的机型名截断，免得文件名没法读。
        assertEquals("a".repeat(24), BackupNaming.deviceTag("a".repeat(40)))
        // 一个能进文件名的字符都没有（纯中文机型名），也不能生成一个传不上去的空名字。
        assertEquals("device", BackupNaming.deviceTag("平板"))
        assertEquals("device", BackupNaming.deviceTag(""))
        assertEquals("x20pro-max", BackupNaming.deviceTag("---x20pro-max---"))
    }

    @Test
    fun oldDayOnlyNamesStillReadAndSortFirst() {
        val old = BackupNaming.keyOf(legacy(LocalDate.of(2026, 10, 28)))!!
        assertEquals(LocalDate.of(2026, 10, 28), old.day)
        assertEquals(legacy(LocalDate.of(2026, 10, 28)), old.name)
        assertTrue("只有日期的老命名读成那天最早的一份", old.isLegacy)
        assertEquals("", old.device)
        assertEquals(0, old.dbVersion)

        val new = BackupNaming.keyOf(name(at(2026, 10, 28)))!!
        assertFalse(new.isLegacy)
        assertEquals("phone", new.device)
        assertEquals(14, new.dbVersion)
        // 同一天：老命名一定排在带时刻的那份之前。
        assertTrue(NEWEST.compare(new, old) < 0)
        assertEquals("08:05", new.hourMinute)
        assertEquals("", old.hourMinute)
        assertEquals(old.rank, LocalDate.of(2026, 10, 28).toEpochDay() * SnapshotKey.SECONDS_PER_DAY)
    }

    @Test
    fun onlyOurOwnFileNameIsRecognised() {
        val good = "/dav/Immerlex/immerlex-2026-10-28.db.gz"
        assertEquals(LocalDate.of(2026, 10, 28), dayOf(good))
        assertEquals(LocalDate.of(2026, 10, 28), dayOf(name(at(2026, 10, 28))))
        assertNull(dayOf("notes.txt"))
        assertNull(dayOf("immerlex-2026-10-28.db"))
        assertNull(dayOf("immerlex-20261028.db.gz"))
        assertNull(dayOf("immerlex-2026-10-28T080503-phone.db.gz"))
        assertNull(dayOf("immerlex-2026-10-28T080503-手机-v14.db.gz"))
        assertNull(dayOf("immerlex-2026-10-28T250503-phone-v14.db.gz"))
        // 形状对但日子不存在，也不能当历史算。
        assertNull(dayOf("immerlex-2026-02-30.db.gz"))
        assertNull(dayOf(""))
    }

    @Test
    fun nothingGoesAwayUntilThereAreMoreThanTheCap() {
        val days = (0 until Retention.KEEP_RECENT).map { LocalDate.of(2026, 10, 1).plusDays(it.toLong()) }
        assertTrue(Retention.toDelete(days.map(::legacy)).isEmpty())
    }

    @Test
    fun overflowKeepsTheNewestSnapshotsPlusOnePerOlderMonth() {
        // 6-01..8-29 共 90 份：最新的 60 份是 7-01..8-29，剩下的尾巴只有 6 月，留 6-30 那一份。
        val days = (0L until 90L).map { LocalDate.of(2026, 6, 1).plusDays(it) }
        val doomed = Retention.toDelete(days.map(::legacy))

        assertEquals(29, doomed.size)
        assertFalse(doomed.contains(legacy(LocalDate.of(2026, 6, 30))))
        assertFalse(doomed.contains(legacy(LocalDate.of(2026, 7, 1))))
        assertFalse(doomed.contains(legacy(LocalDate.of(2026, 8, 29))))
        assertTrue(doomed.contains(legacy(LocalDate.of(2026, 6, 1))))
        assertTrue(doomed.contains(legacy(LocalDate.of(2026, 6, 29))))
    }

    @Test
    fun theCapCountsSnapshotsNotDays() {
        // 7 月每天两份共 62 份：留最新 60 份，剩下的 7-01 两份里只留更晚的那一份。
        val names = (1..31).flatMap { day ->
            listOf(at(2026, 7, day, 8, 0, 0), at(2026, 7, day, 20, 0, 0)).map(::name)
        }
        val doomed = Retention.toDelete(names)

        assertEquals(1, doomed.size)
        assertFalse(doomed.contains(name(at(2026, 7, 1, 20, 0, 0))))
        assertFalse(doomed.contains(name(at(2026, 7, 31, 20, 0, 0))))
        assertTrue(doomed.contains(name(at(2026, 7, 1, 8, 0, 0))))
    }

    @Test
    fun foreignNamesSurviveEvenAmplentyOfOurs() {
        // 9-01..10-10 共 40 份，一份都没到上限，谁都不该被删。
        val days = (0L until 40L).map { LocalDate.of(2026, 9, 1).plusDays(it) }
        val doomed = Retention.toDelete(days.map(::legacy) + listOf("backup.db.gz", "词库.txt", "Immerlex/"))
        assertTrue(doomed.isEmpty())

        // 再塞满：超上限以后外来名字依然不参与删除。
        val more = (0L until 90L).map { LocalDate.of(2026, 6, 1).plusDays(it) }
        val overflow = Retention.toDelete(more.map(::legacy) + listOf("backup.db.gz", "词库.txt", "Immerlex/"))
        assertTrue(overflow.none { it in listOf("backup.db.gz", "词库.txt", "Immerlex/") })
        assertEquals(29, overflow.size)
    }

    @Test
    fun theCloudListReadsNewestFirst() {
        val files = listOf(
            RemoteFile(legacy(LocalDate.of(2026, 10, 28)), 1L),
            RemoteFile(name(at(2026, 10, 28, 20, 30, 0)), 1L),
            RemoteFile(name(at(2026, 10, 29, 7, 0, 0)), 1L),
            RemoteFile("notes.txt", 1L),
        )
        assertEquals(
            listOf(
                name(at(2026, 10, 29, 7, 0, 0)),
                name(at(2026, 10, 28, 20, 30, 0)),
                legacy(LocalDate.of(2026, 10, 28)),
            ),
            // 读不出来历的行排在最后，界面照样看得见它，只是不会被当成历史。
            BackupNaming.sorted(files).map { it.name }.take(3),
        )
        assertEquals("notes.txt", BackupNaming.sorted(files).last().name)
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

    private companion object {
        val NEWEST = SnapshotKey.NEWEST_FIRST
    }
}
