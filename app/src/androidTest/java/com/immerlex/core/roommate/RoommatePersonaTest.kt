package com.immerlex.core.roommate

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.immerlex.core.db.ImmerlexDatabase
import com.immerlex.core.db.RM_CAUSE_WHIM
import com.immerlex.core.db.RM_PACK_LOCAL
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 名片的边界：名字、三项底色和多留的一句设定只决定它**怎么说话**，
 * 所以改名片只重发第一句话和整本台词本；住进来的日子、小鱼干、小本子、
 * 它送的东西和已经买下的货都不该跟着动。这里用没有 AI 的生成器，
 * 走的就是本地兜底句子——没配密钥也照样能改名片。
 */
@RunWith(AndroidJUnit4::class)
class RoommatePersonaTest {

    private lateinit var db: ImmerlexDatabase
    private lateinit var repo: RoommateRepository
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-29T12:00:00Z")
    private val today = now.atZone(zone).toLocalDate().toEpochDay()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ImmerlexDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = RoommateRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun creatingWithNoAiStillWritesADayStampAndAFullLinebook() = runBlocking {
        val created = writer().create("小鱼", PersonaCards.INDIRECT, PersonaCards.FAST, PersonaCards.PERFORMS, "它怕打雷", now)

        val profile = repo.profile()!!
        assertEquals(created.persona.name, profile.name)
        assertEquals("它怕打雷", profile.setting)
        assertEquals(RoommateText.introLine(today), profile.introLine)
        assertEquals(now.epochSecond, profile.createdAtSec)
        assertEquals(today - 1, repo.state().lastSettledDay)
        assertEquals(RoommateText.linebook(), repo.lines())
        assertTrue(repo.eventsOfDay(today).any { it.body.contains("今天你们认识了") })
    }

    @Test
    fun editingTheCardRespeaksThePersonaAndLeavesEverythingElseAlone() = runBlocking {
        val writer = writer()
        writer.create("小鱼", PersonaCards.DIRECT, PersonaCards.SLOW, PersonaCards.OPEN, "它怕打雷", now)

        val pack = RoommateText.dailyPack(today, null, 8)
        repo.saveOffers(packOffers(pack, today, 8, now.epochSecond, RM_PACK_LOCAL))
        repo.creditWord(today, "seed", now.epochSecond, amount = 100)
        val gift = "d$today:g0"
        repo.purchaseOffer(gift, now.epochSecond, today)
        val petGift = repo.grantPetGift(
            dayKey = today,
            atSec = now.epochSecond,
            name = "一颗圆石子",
            story = "它在河边挑了最久的一颗。",
            line = "这个给你。",
            cause = RM_CAUSE_WHIM,
        )
        val balanceBefore = repo.balance()
        val eventsBefore = repo.recentEvents(200)
        val createdAtBefore = repo.profile()!!.createdAtSec
        val settledDayBefore = repo.state().lastSettledDay

        val laterDay = today + 3
        val later = now.plusSeconds(3 * 24 * 3600)
        writer.update("阿澄", PersonaCards.QUIET, PersonaCards.MOOD, PersonaCards.PLAIN, "它喜欢靠窗坐着", later)

        val profile = repo.profile()!!
        assertEquals("阿澄", profile.name)
        assertEquals(PersonaCards.QUIET, profile.nature)
        assertEquals("它喜欢靠窗坐着", profile.setting)
        assertEquals(RoommateText.introLine(laterDay), profile.introLine)
        assertEquals(RoommateText.linebook(), repo.lines())

        assertEquals(createdAtBefore, profile.createdAtSec)
        assertEquals(settledDayBefore, repo.state().lastSettledDay)
        assertEquals(balanceBefore, repo.balance())
        assertEquals(eventsBefore, repo.recentEvents(200))
        assertTrue(repo.offer(gift)!!.purchased)
        assertEquals(listOf(petGift), repo.allPetGifts().map { it.id })
        assertEquals(today, repo.petGiftLastDay())
    }

    @Test
    fun editingWithoutARoommateDoesNothing() = runBlocking {
        assertNull(writer().update("阿澄", PersonaCards.QUIET, PersonaCards.SLOW, PersonaCards.OPEN, "", now))
        assertNull(repo.profile())
    }

    private fun writer() =
        RoommatePersona(repo, RoommateGenerator({ error("没有配 key，不该走到模型") }, { false }), { zone })
}
