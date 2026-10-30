package com.immerlex.core.roommate

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.immerlex.core.db.ImmerlexDatabase
import com.immerlex.core.db.RM_CAUSE_WHIM
import com.immerlex.core.db.RM_KIND_GIFT
import com.immerlex.core.db.RM_KIND_NOTE
import com.immerlex.core.db.RM_KIND_PET_GIFT
import com.immerlex.core.db.RM_KIND_RECIPE
import com.immerlex.core.db.RM_PACK_LOCAL
import com.immerlex.core.db.ReviewLogEntity
import com.immerlex.core.db.WordEntity
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 小鱼干的硬约束：余额只能由账本决定、收益递减但不封顶、购买不会扣成负数也不会扣两次、
 * 学习记录要过了 8 秒撤销窗口才变成钱、同一条记录永远只发一次、礼物买齐才解锁菜谱。
 * 这些规则跨了仓库和结算两层，纯 JVM 单测碰不到 Room 的唯一索引与事务，所以放仪器测试。
 */
@RunWith(AndroidJUnit4::class)
class RoommateLedgerTest {

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
    fun settlementPaysDiminishingWordsWithoutACapAndOnlyOnce() = runBlocking {
        val settlement = RoommateSettlement(repo)
        val old = now.epochSecond - 10
        repeat(5) { seedTouch("early$it", old) }
        assertEquals(15, settlement.settle(now, zone).balance)

        repeat(27) { seedTouch("late$it", old) }
        val after = settlement.settle(now, zone)
        assertEquals(41, after.balance)
        assertEquals(RewardPolicy.rewardFor(32), after.balance)

        val again = settlement.settle(now, zone)
        assertEquals(41, again.balance)
        assertEquals(41, again.earnedToday)
    }

    @Test
    fun settlementOnlyPaysRecordsPastTheUndoGrace() = runBlocking {
        val settlement = RoommateSettlement(repo)
        val logId = seedTouch("cup", now.epochSecond - 2)

        assertEquals(0, settlement.settle(now, zone).balance)
        assertEquals(0, settlement.settle(now.plusSeconds(5), zone).balance)
        assertEquals(2, settlement.settle(now.plusSeconds(8), zone).balance)

        // 已发的钱不会因为随后删掉记录而收回；撤销窗口存在的意义就是让删除只发生在发钱之前。
        db.reviewLogDao().deleteById(logId)
        assertEquals(2, settlement.settle(now.plusSeconds(30), zone).balance)
    }

    @Test
    fun settlementPaysEachRecordOnceEvenWhenTheWordCameUpTwice() = runBlocking {
        val settlement = RoommateSettlement(repo)
        val old = now.epochSecond - 10
        listOf("lamp", "thread", "window", "letter", "spoon").forEach {
            seedTouch(it, old)
            seedTouch(it, old + 1)
        }

        val first = settlement.settle(now, zone)
        assertEquals(15, first.balance)

        val again = settlement.settle(now, zone)
        assertEquals(15, again.balance)
        assertEquals(15, again.earnedToday)
    }

    @Test
    fun firstRunDoesNotBackfillOldDays() = runBlocking {
        val settlement = RoommateSettlement(repo)
        val threeDaysAgo = now.epochSecond - 3 * 24 * 3600
        listOf("lamp", "thread", "window", "letter", "spoon").forEach { seedTouch(it, threeDaysAgo) }

        assertEquals(0, settlement.settle(now, zone).balance)
        assertEquals(today - 1, repo.state().lastSettledDay)
    }

    @Test
    fun shopSellsGiftsByTierAndOnlyUnlocksTheRecipeAfterAllThree() = runBlocking {
        val pack = RoommateText.dailyPack(today, null, 8)
        repo.saveOffers(packOffers(pack, today, 8, now.epochSecond, RM_PACK_LOCAL))

        val gift0 = "d$today:g0"
        val gift1 = "d$today:g1"
        val gift2 = "d$today:g2"
        val recipeId = "d$today:r"
        val nearId = "near:$today"

        assertEquals(PurchaseOutcome.TooPoor, repo.purchaseOffer(gift0, now.epochSecond, today))
        assertEquals(PurchaseOutcome.Locked, repo.purchaseOffer(recipeId, now.epochSecond, today))
        assertEquals(0, repo.balance())

        repo.creditWord(today, "seed", now.epochSecond, amount = 100)

        assertEquals(PurchaseOutcome.Ok(95), repo.purchaseOffer(gift0, now.epochSecond, today))
        assertEquals(PurchaseOutcome.Ok(83), repo.purchaseOffer(gift1, now.epochSecond, today))
        assertTrue(repo.offer(recipeId)!!.hidden)

        val last = repo.purchaseOffer(gift2, now.epochSecond, today)
        assertEquals(PurchaseOutcome.Ok(58, pack.recipe!!.name), last)
        assertFalse(repo.offer(recipeId)!!.hidden)
        assertEquals(58, repo.balance())
        assertTrue(
            repo.eventsOfDay(today).any {
                it.kind == RM_KIND_RECIPE && it.body == pack.recipe.freeLine
            },
        )

        assertEquals(PurchaseOutcome.Ok(46), repo.purchaseOffer(recipeId, now.epochSecond, today))
        // 菜谱可以再做一份，每次单独扣钱、单独留一条。
        assertEquals(PurchaseOutcome.Ok(34), repo.purchaseOffer(recipeId, now.epochSecond, today))
        assertEquals(PurchaseOutcome.Ok(26), repo.purchaseOffer(nearId, now.epochSecond, today))
        assertNull(repo.activeNear())
        assertEquals(26, repo.balance())
        assertEquals(
            2,
            repo.eventsOfDay(today).count { it.kind == RM_KIND_RECIPE && it.body == pack.recipe.restLine },
        )

        // 三件当天的礼物，加上攒着买的那件，都会在时间线上留一条。
        val gifts = repo.eventsOfDay(today).filter { it.kind == RM_KIND_GIFT }
        assertEquals(4, gifts.size)
        assertTrue(repo.eventsOfDay(today).count { it.kind == RM_KIND_NOTE && it.openLoop } >= 1)
    }

    @Test
    fun workIsBoughtOnceAndOnlyWithEnoughFish() = runBlocking {
        val workId = repo.addWork("数到第七样", "正文", now.epochSecond, today)
        assertEquals(PurchaseOutcome.Missing, repo.purchaseWork(999L, now.epochSecond, today))
        assertEquals(PurchaseOutcome.TooPoor, repo.purchaseWork(workId, now.epochSecond, today))

        repo.creditWord(today, "seed", now.epochSecond, amount = OfferPolicy.WORK_PRICE)
        assertEquals(PurchaseOutcome.Ok(0), repo.purchaseWork(workId, now.epochSecond, today))
        assertTrue(repo.work(workId)!!.purchased)
        assertEquals(PurchaseOutcome.AlreadyOwned, repo.purchaseWork(workId, now.epochSecond, today))
        assertEquals(0, repo.balance())
    }

    @Test
    fun petGiftsAreKeepsakesThatNeverTouchTheLedger() = runBlocking {
        val id = repo.grantPetGift(
            dayKey = today,
            atSec = now.epochSecond,
            name = "一颗圆石子",
            story = "它在河边挑了最久的一颗。",
            line = "这个给你。",
            cause = RM_CAUSE_WHIM,
        )

        assertEquals(0, repo.balance())
        assertEquals(id, repo.petGiftsOfDay(today).single().id)
        assertEquals(1, repo.allPetGifts().size)
        assertEquals(today, repo.petGiftLastDay())

        val event = repo.eventsOfDay(today).single { it.kind == RM_KIND_PET_GIFT }
        assertTrue(event.body.contains("一颗圆石子"))
        assertFalse(event.openLoop)
        assertTrue(repo.offersOfDay(today).isEmpty())
    }

    private suspend fun seedTouch(word: String, atSec: Long): Long {
        val wordId = db.wordDao().insert(
            WordEntity(word = word, source = "test", createdAtSec = atSec),
        )
        return db.reviewLogDao().insert(
            ReviewLogEntity(
                wordId = wordId,
                rating = 3,
                source = "reader",
                reviewEpochSec = atSec,
                elapsedDays = 0,
                scheduledDays = 1,
            ),
        )
    }
}
