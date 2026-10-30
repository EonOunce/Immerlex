package com.immerlex.core.roommate

import android.net.Uri
import com.immerlex.core.db.RM_CHANNEL_GIFT
import com.immerlex.core.db.RM_CHANNEL_NEAR
import com.immerlex.core.db.RM_CHANNEL_RECIPE
import com.immerlex.core.db.RM_KIND_EPISODE
import com.immerlex.core.db.RM_KIND_NOTE
import com.immerlex.core.db.RM_PACK_AI
import com.immerlex.core.db.RM_PACK_LOCAL
import com.immerlex.core.db.RoommateDailyEntity
import com.immerlex.core.db.RoommateEventEntity
import com.immerlex.core.db.RoommateProfileEntity
import com.immerlex.core.db.RoommateWorkEntity
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class Interaction(val assetId: String?, val line: String)

data class RoomHook(
    val met: Boolean,
    val name: String,
    val line: String,
    val balance: Int,
    val earnedToday: Int,
)

/**
 * 室友的日常编排：台词本、每天的一包内容、一次选择、三个免费动作、小铺和账本。
 * 内容可以来自模型，规则永远来自这里——价格、菜谱解锁、礼物触发都走确定性代码。
 *
 * 进房间只准备本地内容并立刻返回；AI 升级由 [upgradeDaily] 在后台做一次，
 * 失败就继续用本地内容，不重试、不阻塞界面。
 */
class RoommateService(
    private val repo: RoommateRepository,
    private val settlement: RoommateSettlement,
    private val generator: RoommateGenerator,
    private val store: RoommateAssetStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    /** 今日连续天数由日程层提供；无库的单测传默认 0。 */
    private val streakProvider: suspend (Instant) -> Int = { 0 },
) {
    private val gifts = RoommateGiftService(repo, zone)
    private val cursors = ConcurrentHashMap<String, AtomicInteger>()
    private val prep = Mutex()

    fun dayOf(now: Instant = Instant.now()): Long =
        now.atZone(zone()).toLocalDate().toEpochDay()

    suspend fun settle(now: Instant = Instant.now()): Settlement = settlement.settle(now, zone())

    /** 进房间：结算、备好台词与今天的内容、看它是否要送东西、记下这次到访。 */
    suspend fun enter(now: Instant = Instant.now()): RoomView = prep.withLock {
        settlement.settle(now, zone())
        val day = dayOf(now)
        val profile = repo.profile() ?: return@withLock buildView(now, day)
        ensureLinebook()
        ensureDaily(now, profile)
        gifts.proactive(profile, day, streakProvider(now), now)
        repo.saveState(repo.state().copy(lastVisitSec = now.epochSecond))
        buildView(now, day)
    }

    suspend fun view(now: Instant = Instant.now()): RoomView {
        settlement.settle(now, zone())
        return buildView(now, dayOf(now))
    }

    /** 今日页只读：不准备内容、不触发礼物、不更新到访时间。 */
    suspend fun hook(now: Instant = Instant.now()): RoomHook {
        val settlement = settlement.settle(now, zone())
        val profile = repo.profile()
            ?: return RoomHook(false, "", "", settlement.balance, settlement.earnedToday)
        val episode = repo.episode(dayOf(now))
        return RoomHook(
            met = true,
            name = profile.name,
            line = episode?.body.orEmpty(),
            balance = settlement.balance,
            earnedToday = settlement.earnedToday,
        )
    }

    /** 小本子：最近 30 天，新的日子在前；同一天里按发生顺序。 */
    suspend fun notebook(now: Instant = Instant.now()): List<RoommateEventEntity> {
        val day = dayOf(now)
        return repo.notebook(day - NOTEBOOK_DAYS, day)
    }

    suspend fun petGifts(): List<PetGiftView> = gifts.all()

    fun assetFile(assetId: String?): File? =
        assetId?.let { store.fileOf(it) }?.takeIf { it.exists() }

    suspend fun createProfile(name: String, nature: String, pace: String, face: String, now: Instant) {
        val clean = name.trim().take(8).ifBlank { "小室友" }
        val profile = Persona(clean, nature, pace, face)
        repo.runTransaction {
            repo.saveProfile(
                RoommateProfileEntity(
                    name = clean,
                    nature = nature,
                    pace = pace,
                    face = face,
                    persona = profile.card,
                    createdAtSec = now.epochSecond,
                ),
            )
            repo.saveState(repo.state().copy(lastSettledDay = dayOf(now) - 1))
            repo.insertEvent(
                RoommateEventEntity(
                    dayKey = dayOf(now),
                    atSec = now.epochSecond,
                    kind = RM_KIND_NOTE,
                    body = "今天你们认识了。它记住了你叫它「$clean」。",
                ),
            )
        }
        repo.saveLinebook(RoommateText.linebook())
    }

    /** 台词本只向模型要一次；失败就用本地六句，之后不再重试。 */
    suspend fun upgradeLinebook(now: Instant = Instant.now()): Boolean {
        val profile = repo.profile() ?: return false
        val state = repo.state()
        if (state.linebookAiDone) return false
        repo.saveState(state.copy(linebookAiDone = true))
        val ai = generator.linebook(context(profile, dayOf(now))) ?: return false
        repo.saveLinebook(ai)
        return true
    }

    /**
     * 今天的一包本地内容先落库，保证断网也有东西看。
     * 攒钱目标只保留一件：已有未买的目标就不再新建，价格也永不改。
     */
    suspend fun ensureDaily(now: Instant = Instant.now(), profile: RoommateProfileEntity? = null): Boolean {
        if (profile == null && repo.profile() == null) return false
        val day = dayOf(now)
        repo.daily(day)?.let { return false }
        // 升级上来的老用户今天可能已经有见闻（v2.0），不能再往时间线上叠一条。
        val episode = repo.episode(day)
        val loop = repo.openLoops(1).firstOrNull()
        val activeNear = repo.activeNear()
        val nearPrice = activeNear?.price ?: OfferPolicy.nearPrice(repo.balance())
        val pack = RoommateText.dailyPack(day, loop?.body, nearPrice)
        val asset = RoommateAssets.pick(repo.assets(), RoomAction.TOUCH, day)
        val offers = packOffers(pack, day, nearPrice, now.epochSecond, RM_PACK_LOCAL)
            .filterNot { it.channel == RM_CHANNEL_NEAR && activeNear != null }
        repo.runTransaction {
            repo.saveDaily(dailyOf(pack, day, now.epochSecond, RM_PACK_LOCAL))
            val eventId = episode?.id ?: repo.insertEvent(
                RoommateEventEntity(
                    dayKey = day,
                    atSec = now.epochSecond,
                    kind = RM_KIND_EPISODE,
                    body = pack.episode.line,
                    choiceA = pack.episode.choiceA,
                    choiceB = pack.episode.choiceB,
                    assetId = asset?.assetId,
                ),
            )
            repo.saveOffers(offers)
            if (episode == null) {
                repo.saveState(repo.state().copy(lastEpisodeDay = day))
                loop?.let { repo.closeLoop(it.id) }
                recordHabit(pack.episode.habit, day, eventId, now)
            }
        }
        return true
    }

    /**
     * 一天最多一次 AI 改写；先落 lastAiDay 再发请求，失败也不反复烧调用。
     * 只替换还没买下的礼物与摊子；菜谱解锁后保持原样，攒钱目标只换内容不换价格。
     */
    suspend fun upgradeDaily(now: Instant = Instant.now()): RoomView? {
        val profile = repo.profile() ?: return null
        val day = dayOf(now)
        val state = repo.state()
        if (state.lastAiDay == day) return null
        repo.saveState(state.copy(lastAiDay = day))
        val daily = repo.daily(day) ?: return null
        val activeNear = repo.activeNear()
        val nearPrice = activeNear?.price ?: OfferPolicy.nearPrice(repo.balance())
        val fallback = RoommateText.dailyPack(day, repo.openLoops(1).firstOrNull()?.body, nearPrice)
        val context = dailyContext(profile, day, now, nearPrice)
        val pack = generator.daily(context, fallback) ?: return null
        val episode = repo.episode(day) ?: return null
        val assetId = pack.episode.assetTag?.let { tag ->
            repo.assets().firstOrNull { it.tag == tag }?.assetId
        }
        repo.runTransaction {
            repo.updateEvent(
                episode.copy(
                    body = pack.episode.line,
                    choiceA = pack.episode.choiceA,
                    choiceB = pack.episode.choiceB,
                    assetId = assetId ?: episode.assetId,
                ),
            )
            repo.saveDaily(
                daily.copy(
                    source = RM_PACK_AI,
                    coquetry = pack.coquetry.joinToString("\n"),
                    petGiftName = pack.petGift?.name.orEmpty(),
                    petGiftStory = pack.petGift?.story.orEmpty(),
                    petGiftLine = pack.petGift?.line.orEmpty(),
                ),
            )
            val old = repo.offersOfDay(day).associateBy { it.offerId }
            val fresh = packOffers(pack, day, nearPrice, now.epochSecond, RM_PACK_AI)
                .mapNotNull { row ->
                    val existing = old[row.offerId]
                    when {
                        existing?.purchased == true -> null
                        row.channel == RM_CHANNEL_NEAR -> null
                        row.channel == RM_CHANNEL_RECIPE && existing?.hidden == false -> null
                        existing == null -> row
                        else -> row.copy(hidden = existing.hidden)
                    }
                }
            repo.saveOffers(fresh)
            if (activeNear != null && activeNear.source == RM_PACK_LOCAL && pack.near != null) {
                repo.saveOffers(
                    listOf(
                        activeNear.copy(
                            name = pack.near.name,
                            story = pack.near.story,
                            reaction = pack.near.reaction,
                            followUp = pack.near.followUp,
                            source = RM_PACK_AI,
                        ),
                    ),
                )
            }
            recordHabit(pack.episode.habit, day, episode.id, now)
        }
        repo.revealRecipeIfReady(day, now.epochSecond)
        return buildView(now, day)
    }

    suspend fun choose(eventId: Long, chosen: Int, now: Instant = Instant.now()): String? {
        val event = repo.event(eventId) ?: return null
        if (event.chosen != 0) return null
        val day = dayOf(now)
        val reply = RoommateText.choiceReply(day, chosen)
        repo.choose(eventId, chosen, reply, now.epochSecond, day)
        return reply
    }

    suspend fun interact(action: RoomAction, now: Instant = Instant.now()): Interaction {
        val day = dayOf(now)
        val asset = RoommateAssets.pick(repo.assets(), action, day * 24 + now.epochSecond / 3600)
        if (action == RoomAction.OUTING) writeOutingNote(day, now)
        val line = if (action == RoomAction.OUTING) nextLine(action) else coquetry(day) ?: nextLine(action)
        return Interaction(asset?.assetId, line)
    }

    suspend fun shop(now: Instant = Instant.now()): ShopView {
        val day = dayOf(now)
        val balance = repo.balance()
        return shopOf(repo.offersOfDay(day), repo.activeNear(), repo.works(), balance)
    }

    suspend fun buyOffer(offerId: String, now: Instant = Instant.now()): PurchaseOutcome {
        val day = dayOf(now)
        val outcome = repo.purchaseOffer(offerId, now.epochSecond, day)
        if (outcome is PurchaseOutcome.Ok) {
            val offer = repo.offer(offerId)
            val profile = repo.profile()
            if (offer?.channel == RM_CHANNEL_GIFT && profile != null) {
                gifts.response(profile, day, now)
            }
        }
        return outcome
    }

    /** 一天只写一篇；已有正文才上架，不让用户付钱赌生成。 */
    suspend fun writeWork(now: Instant = Instant.now()): RoommateWorkEntity? {
        val day = dayOf(now)
        if (repo.workCountOfDay(day) > 0) return null
        val profile = repo.profile() ?: return null
        val generated = generator.work(context(profile, day))
        val (title, body) = generated?.let { it.title to it.body } ?: RoommateText.work(day)
        val id = repo.addWork(title, body, now.epochSecond, day)
        return repo.work(id)
    }

    suspend fun buyWork(id: Long, now: Instant = Instant.now()): PurchaseOutcome =
        repo.purchaseWork(id, now.epochSecond, dayOf(now))

    suspend fun importAssets(uri: Uri, now: Instant = Instant.now()): ImportSummary {
        val summary = store.import(uri, now.epochSecond)
        if (summary.added > 0) attachAssetIfMissing(now)
        return summary
    }

    private suspend fun buildView(now: Instant, day: Long): RoomView {
        val assets = repo.assets()
        val balance = repo.balance()
        return RoomView(
            profile = repo.profile()?.toPersona(),
            episode = repo.episodeView(day),
            balance = balance,
            earnedToday = repo.earnedOn(day),
            habits = repo.habits().map { it.label },
            notebookCount = repo.notebook(day - NOTEBOOK_DAYS, day).size,
            missingAssets = store.missing(assets).size,
            assetsImported = assets.size,
            petGiftCount = gifts.all().size,
            petGiftToday = gifts.today(day),
            near = repo.activeNear()?.toCard(balance),
        )
    }

    private suspend fun ensureLinebook() {
        val book = repo.lines()
        val complete = RoomAction.entries.all { (book[it]?.size ?: 0) >= LINEBOOK_SIZE }
        if (!complete) repo.saveLinebook(RoommateText.linebook())
    }

    private suspend fun nextLine(action: RoomAction): String {
        val pool = repo.lines()[action].orEmpty()
            .ifEmpty { RoommateText.linebook().getValue(action) }
        val index = cursors.computeIfAbsent(action.key) { AtomicInteger() }.getAndIncrement()
        return pool[Math.floorMod(index, pool.size)]
    }

    /** 一天最多一句撒娇；出门不算。 */
    private suspend fun coquetry(day: Long): String? {
        val daily = repo.daily(day) ?: return null
        if (daily.coquetryUsed) return null
        val lines = daily.coquetry.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        repo.markCoquetryUsed(day)
        return lines.first()
    }

    /** 今天那段生活是在没有素材时写的：导入后补一张，用户不用等明天。 */
    private suspend fun attachAssetIfMissing(now: Instant) {
        val day = dayOf(now)
        val episode = repo.episode(day) ?: return
        if (episode.assetId != null) return
        val asset = RoommateAssets.pick(repo.assets(), RoomAction.TOUCH, day) ?: return
        repo.updateEvent(episode.copy(assetId = asset.assetId))
    }

    private suspend fun recordHabit(
        habit: String?,
        day: Long,
        eventId: Long,
        now: Instant,
    ) {
        val label = HabitPolicy.normalize(habit.orEmpty()) ?: return
        val habitId = label.lowercase()
        val learned = repo.habit(habitId) != null
        if (!learned) repo.addEvidence(habitId, day, eventId, now.epochSecond)
        if (HabitPolicy.shouldLearn(repo.evidenceDays(habitId), day, learned)) {
            repo.learnHabit(habitId, label, day, now.epochSecond)
        }
    }

    private suspend fun context(profile: RoommateProfileEntity, day: Long): EpisodeContext {
        val recent = repo.recentEvents(8)
            .filterNot { it.dayKey == day && it.kind == RM_KIND_EPISODE }
            .map { it.body }
            .filter { it.isNotBlank() }
        return EpisodeContext(
            name = profile.name,
            persona = profile.persona,
            recent = recent,
            habits = repo.habits().map { it.label },
            openLoop = repo.openLoops(1).firstOrNull()?.body,
            assetTags = repo.assets().map { it.tag }.distinct(),
        )
    }

    private suspend fun dailyContext(
        profile: RoommateProfileEntity,
        day: Long,
        now: Instant,
        nearPrice: Int,
    ): DailyContext {
        val start = LocalDate.ofEpochDay(day).atStartOfDay(zone()).toEpochSecond()
        val touched = repo.touches(start, now.epochSecond + 1)
            .map { it.wordKey }
            .distinct()
            .takeLast(MAX_TOUCHED_WORDS)
        return DailyContext(
            episode = context(profile, day),
            touchedWords = touched,
            balance = repo.balance(),
            nearPrice = nearPrice,
            recentGifts = gifts.all().take(3).map { it.name },
        )
    }

    private suspend fun writeOutingNote(day: Long, now: Instant) {
        val last = repo.eventsOfDay(day).lastOrNull { it.kind == RM_KIND_NOTE }
        if (last != null && now.epochSecond - last.atSec < OUTING_NOTE_GAP_SECONDS) return
        repo.insertEvent(
            RoommateEventEntity(
                dayKey = day,
                atSec = now.epochSecond,
                kind = RM_KIND_NOTE,
                body = "它出门转了一圈，回来时没说什么，但心情看起来不错。",
            ),
        )
    }

    private fun dailyOf(
        pack: DailyPack,
        day: Long,
        atSec: Long,
        source: String,
    ): RoommateDailyEntity = RoommateDailyEntity(
        dayKey = day,
        source = source,
        coquetry = pack.coquetry.joinToString("\n"),
        petGiftName = pack.petGift?.name.orEmpty(),
        petGiftStory = pack.petGift?.story.orEmpty(),
        petGiftLine = pack.petGift?.line.orEmpty(),
        createdAtSec = atSec,
    )

    private companion object {
        const val OUTING_NOTE_GAP_SECONDS = 3 * 60 * 60L
        const val NOTEBOOK_DAYS = 30L
        const val LINEBOOK_SIZE = 6
        const val MAX_TOUCHED_WORDS = 8
    }
}

private fun RoommateProfileEntity.toPersona(): Persona = Persona(name, nature, pace, face)
