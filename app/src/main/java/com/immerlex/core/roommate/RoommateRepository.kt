package com.immerlex.core.roommate

import androidx.room.withTransaction
import com.immerlex.core.db.ImmerlexDatabase
import com.immerlex.core.db.RM_CHANNEL_RECIPE
import com.immerlex.core.db.RM_KIND_CHOICE
import com.immerlex.core.db.RM_KIND_EPISODE
import com.immerlex.core.db.RM_KIND_GIFT
import com.immerlex.core.db.RM_KIND_NOTE
import com.immerlex.core.db.RM_KIND_PET_GIFT
import com.immerlex.core.db.RM_KIND_RECIPE
import com.immerlex.core.db.RM_KIND_WORK
import com.immerlex.core.db.RM_PROFILE_ID
import com.immerlex.core.db.RM_REASON_MILESTONE
import com.immerlex.core.db.RM_REASON_SPEND
import com.immerlex.core.db.RM_REASON_WORD
import com.immerlex.core.db.RM_STATE_ID
import com.immerlex.core.db.RoommateAssetEntity
import com.immerlex.core.db.RoommateDailyEntity
import com.immerlex.core.db.RoommateEventEntity
import com.immerlex.core.db.RoommateHabitEntity
import com.immerlex.core.db.RoommateLedgerEntity
import com.immerlex.core.db.RoommateLineEntity
import com.immerlex.core.db.RoommateOfferEntity
import com.immerlex.core.db.RoommatePetGiftEntity
import com.immerlex.core.db.RoommateProfileEntity
import com.immerlex.core.db.RoommateStateEntity
import com.immerlex.core.db.RoommateWorkEntity
import com.immerlex.core.db.WordTouch

sealed interface PurchaseOutcome {
    data class Ok(val balance: Int, val unlockedRecipe: String? = null) : PurchaseOutcome
    data object TooPoor : PurchaseOutcome
    data object AlreadyOwned : PurchaseOutcome
    data object Missing : PurchaseOutcome
    data object Locked : PurchaseOutcome
}

/**
 * 室友的唯一写入口。钱、库存、购买、每日内容、它送的礼物都在这一个类里落库，
 * 多步写入一律走 Room 事务——中途失败不会留下"扣了钱没东西"。
 */
class RoommateRepository(private val db: ImmerlexDatabase) {
    private val profiles = db.roommateProfileDao()
    private val assets = db.roommateAssetDao()
    private val events = db.roommateEventDao()
    private val ledger = db.roommateLedgerDao()
    private val habits = db.roommateHabitDao()
    private val evidence = db.roommateEvidenceDao()
    private val works = db.roommateWorkDao()
    private val state = db.roommateStateDao()
    private val settlement = db.roommateSettlementDao()
    private val lines = db.roommateLineDao()
    private val daily = db.roommateDailyDao()
    private val offers = db.roommateOfferDao()
    private val petGifts = db.roommatePetGiftDao()

    suspend fun profile(): RoommateProfileEntity? = profiles.byId(RM_PROFILE_ID)

    /** 多步写入（例如初识的三条）走同一个事务。 */
    suspend fun <T> runTransaction(block: suspend () -> T): T = db.withTransaction { block() }

    suspend fun saveProfile(profile: RoommateProfileEntity) = profiles.upsert(profile)

    suspend fun assets(): List<RoommateAssetEntity> = assets.all()

    suspend fun asset(assetId: String): RoommateAssetEntity? = assets.byId(assetId)

    suspend fun addAsset(asset: RoommateAssetEntity) {
        assets.insertIgnore(asset)
    }

    suspend fun state(): RoommateStateEntity = state.byId(RM_STATE_ID) ?: RoommateStateEntity()

    suspend fun saveState(value: RoommateStateEntity) = state.upsert(value)

    suspend fun episode(dayKey: Long): RoommateEventEntity? = events.firstOfDay(RM_KIND_EPISODE, dayKey)

    suspend fun episodeView(dayKey: Long): Episode? = episode(dayKey)?.let {
        Episode(it.id, it.body, it.choiceA, it.choiceB, it.chosen, it.assetId)
    }

    suspend fun recentEvents(limit: Int): List<RoommateEventEntity> = events.recent(limit)

    suspend fun notebook(fromDay: Long, toDay: Long): List<RoommateEventEntity> =
        events.between(fromDay, toDay)

    suspend fun eventsOfDay(dayKey: Long): List<RoommateEventEntity> = events.forDay(dayKey)

    suspend fun openLoops(limit: Int): List<RoommateEventEntity> = events.openLoops(limit)

    suspend fun insertEvent(event: RoommateEventEntity): Long = events.insert(event)

    suspend fun updateEvent(event: RoommateEventEntity) = events.update(event)

    suspend fun event(id: Long): RoommateEventEntity? = events.byId(id)

    suspend fun workCountOfDay(dayKey: Long): Int = events.countOfDay(RM_KIND_WORK, dayKey)

    suspend fun choose(eventId: Long, chosen: Int, reply: String, atSec: Long, dayKey: Long) {
        db.withTransaction {
            events.setChosen(eventId, chosen)
            events.insert(
                RoommateEventEntity(
                    dayKey = dayKey,
                    atSec = atSec,
                    kind = RM_KIND_CHOICE,
                    body = reply,
                    chosen = chosen,
                    sourceId = eventId,
                ),
            )
        }
    }

    suspend fun closeLoop(id: Long) = events.closeLoop(id)

    suspend fun balance(): Int = ledger.balance()

    suspend fun earnedOn(dayKey: Long): Int =
        ledger.sumForDay(dayKey, RM_REASON_WORD) + ledger.sumForDay(dayKey, RM_REASON_MILESTONE)

    suspend fun wordCredits(dayKey: Long): Int = ledger.wordCredits(dayKey)

    suspend fun touches(fromSec: Long, toSec: Long): List<WordTouch> =
        settlement.touchesBetween(fromSec, toSec)

    /** 唯一索引兜底：同一个词同一天第二次调用返回 false，不会多发。 */
    suspend fun creditWord(dayKey: Long, wordKey: String, atSec: Long, amount: Int = 1): Boolean =
        ledger.insertIgnore(
            RoommateLedgerEntity(
                atSec = atSec,
                dayKey = dayKey,
                amount = amount,
                reason = RM_REASON_WORD,
                dedupeKey = RewardPolicy.wordKey(dayKey, wordKey),
                note = wordKey,
            ),
        ) != -1L

    suspend fun creditMilestone(dayKey: Long, atSec: Long): Boolean =
        ledger.insertIgnore(
            RoommateLedgerEntity(
                atSec = atSec,
                dayKey = dayKey,
                amount = RewardPolicy.MILESTONE_BONUS,
                reason = RM_REASON_MILESTONE,
                dedupeKey = RewardPolicy.milestoneKey(dayKey),
            ),
        ) != -1L

    suspend fun habits(): List<RoommateHabitEntity> = habits.all()

    suspend fun habit(habitId: String): RoommateHabitEntity? = habits.byId(habitId)

    suspend fun addEvidence(habitId: String, dayKey: Long, eventId: Long, atSec: Long): Boolean =
        evidence.insertIgnore(
            com.immerlex.core.db.RoommateEvidenceEntity(habitId, dayKey, eventId, atSec),
        ) != -1L

    suspend fun evidenceDays(habitId: String): List<Long> = evidence.daysFor(habitId).map { it.dayKey }

    suspend fun learnHabit(habitId: String, label: String, dayKey: Long, atSec: Long): Boolean =
        habits.insertIgnore(RoommateHabitEntity(habitId, label, dayKey, atSec)) != -1L

    suspend fun lines(): Map<RoomAction, List<String>> =
        lines.all().groupBy { RoomAction.of(it.action) }
            .mapValues { (_, rows) -> rows.sortedBy { it.ordinal }.map { it.line } }

    suspend fun saveLinebook(book: Map<RoomAction, List<String>>) {
        db.withTransaction {
            lines.clear()
            lines.insertAll(
                book.flatMap { (action, pool) ->
                    pool.mapIndexed { index, line -> RoommateLineEntity(action.key, index, line) }
                },
            )
        }
    }

    suspend fun daily(dayKey: Long): RoommateDailyEntity? = daily.byDay(dayKey)

    suspend fun saveDaily(entity: RoommateDailyEntity) = daily.upsert(entity)

    suspend fun markCoquetryUsed(dayKey: Long) = daily.markCoquetryUsed(dayKey)

    suspend fun offersOfDay(dayKey: Long): List<RoommateOfferEntity> = offers.forDay(dayKey)

    suspend fun offer(offerId: String): RoommateOfferEntity? = offers.byId(offerId)

    suspend fun activeNear(): RoommateOfferEntity? = offers.activeNear()

    suspend fun saveOffers(entities: List<RoommateOfferEntity>) = offers.upsertAll(entities)

    suspend fun works(): List<RoommateWorkEntity> = works.all()

    suspend fun work(id: Long): RoommateWorkEntity? = works.byId(id)

    suspend fun addWork(title: String, body: String, atSec: Long, dayKey: Long): Long =
        db.withTransaction {
            val id = works.insert(
                RoommateWorkEntity(
                    title = title,
                    body = body,
                    price = OfferPolicy.WORK_PRICE,
                    createdAtSec = atSec,
                ),
            )
            events.insert(
                RoommateEventEntity(
                    dayKey = dayKey,
                    atSec = atSec,
                    kind = RM_KIND_WORK,
                    body = title,
                    sourceId = id,
                ),
            )
            id
        }

    /**
     * 买下铺子里的一件东西。隐藏菜谱买不到；礼物买齐后，同一天的菜谱会当场现身，
     * 并白送第一口——这些都是同一个事务里的事实，不会出现"扣了钱菜谱却没解锁"。
     * 菜谱本身可以反复买，每多做一份单独记一笔账。
     */
    suspend fun purchaseOffer(offerId: String, atSec: Long, dayKey: Long): PurchaseOutcome =
        db.withTransaction {
            val offer = offers.byId(offerId) ?: return@withTransaction PurchaseOutcome.Missing
            if (offer.hidden) return@withTransaction PurchaseOutcome.Locked
            val repeatable = offer.channel == RM_CHANNEL_RECIPE
            if (offer.purchased && !repeatable) return@withTransaction PurchaseOutcome.AlreadyOwned
            val balance = ledger.balance()
            if (balance < offer.price) return@withTransaction PurchaseOutcome.TooPoor
            val dedupeKey = if (offer.purchased) {
                "cook:${offer.offerId}:${ledger.countByKeyPrefix(RM_REASON_SPEND, "cook:${offer.offerId}:") + 1}"
            } else {
                RewardPolicy.offerKey(offerId)
            }
            val charged = ledger.insertIgnore(
                RoommateLedgerEntity(
                    atSec = atSec,
                    dayKey = dayKey,
                    amount = -offer.price,
                    reason = RM_REASON_SPEND,
                    dedupeKey = dedupeKey,
                    note = offer.name,
                ),
            ) != -1L
            if (!charged) {
                offers.markPurchased(offerId)
                return@withTransaction PurchaseOutcome.AlreadyOwned
            }
            offers.markPurchased(offerId)
            val body = if (offer.channel == RM_CHANNEL_RECIPE) offer.followUp else offer.reaction
            events.insert(
                RoommateEventEntity(
                    dayKey = dayKey,
                    atSec = atSec,
                    kind = if (offer.channel == RM_CHANNEL_RECIPE) RM_KIND_RECIPE else RM_KIND_GIFT,
                    body = body,
                ),
            )
            if (offer.channel != RM_CHANNEL_RECIPE) {
                events.insert(
                    RoommateEventEntity(
                        dayKey = dayKey,
                        atSec = atSec,
                        kind = RM_KIND_NOTE,
                        body = offer.followUp,
                        openLoop = true,
                    ),
                )
            }
            val unlocked = unlockRecipe(dayKey, atSec)
            PurchaseOutcome.Ok(balance - offer.price, unlocked)
        }

    /** AI 改写可能把菜谱需要的礼物换成已经买过的：补一次解锁检查，白送第一口。 */
    suspend fun revealRecipeIfReady(dayKey: Long, atSec: Long): String? =
        db.withTransaction { unlockRecipe(dayKey, atSec) }

    private suspend fun unlockRecipe(dayKey: Long, atSec: Long): String? {
        val recipe = offers.forDay(dayKey).firstOrNull {
            it.channel == RM_CHANNEL_RECIPE && it.hidden
        } ?: return null
        val required = recipe.requires.split(',').filter { it.isNotBlank() }
        if (required.isEmpty()) return null
        if (!required.all { offers.byId(it)?.purchased == true }) return null
        offers.reveal(recipe.offerId)
        events.insert(
            RoommateEventEntity(
                dayKey = dayKey,
                atSec = atSec,
                kind = RM_KIND_RECIPE,
                body = recipe.reaction,
            ),
        )
        return recipe.name
    }

    suspend fun purchaseWork(id: Long, atSec: Long, dayKey: Long): PurchaseOutcome =
        db.withTransaction {
            val work = works.byId(id) ?: return@withTransaction PurchaseOutcome.Missing
            if (work.purchased) return@withTransaction PurchaseOutcome.AlreadyOwned
            val balance = ledger.balance()
            if (balance < work.price) return@withTransaction PurchaseOutcome.TooPoor
            ledger.insertIgnore(
                RoommateLedgerEntity(
                    atSec = atSec,
                    dayKey = dayKey,
                    amount = -work.price,
                    reason = RM_REASON_SPEND,
                    dedupeKey = RewardPolicy.workKey(id),
                    note = work.title,
                ),
            )
            works.markPurchased(id)
            events.insert(
                RoommateEventEntity(
                    dayKey = dayKey,
                    atSec = atSec,
                    kind = RM_KIND_WORK,
                    body = work.title,
                    sourceId = id,
                ),
            )
            PurchaseOutcome.Ok(balance - work.price)
        }

    suspend fun petGiftLastDay(): Long? = petGifts.lastDay()

    suspend fun petGiftsBetween(fromDay: Long, toDay: Long): Int = petGifts.countBetween(fromDay, toDay)

    suspend fun petGiftsOfDay(dayKey: Long): List<RoommatePetGiftEntity> = petGifts.forDay(dayKey)

    suspend fun allPetGifts(): List<RoommatePetGiftEntity> = petGifts.all()

    suspend fun grantPetGift(
        dayKey: Long,
        atSec: Long,
        name: String,
        story: String,
        line: String,
        cause: String,
    ): Long = db.withTransaction {
        val id = petGifts.insert(
            RoommatePetGiftEntity(
                dayKey = dayKey,
                atSec = atSec,
                name = name,
                story = story,
                line = line,
                cause = cause,
            ),
        )
        events.insert(
            RoommateEventEntity(
                dayKey = dayKey,
                atSec = atSec,
                kind = RM_KIND_PET_GIFT,
                body = "它送你$name。$line",
                sourceId = id,
            ),
        )
        id
    }
}
