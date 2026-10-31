package com.immerlex.core.roommate

import android.net.Uri
import com.immerlex.core.db.RM_CHANNEL_GIFT
import com.immerlex.core.db.RM_CHANNEL_RECIPE
import com.immerlex.core.db.RM_KIND_NOTE
import com.immerlex.core.db.RoommateEventEntity
import com.immerlex.core.db.RoommateOfferEntity
import com.immerlex.core.db.RoommateProfileEntity
import com.immerlex.core.db.RoommateWorkEntity
import java.io.File
import java.time.Instant
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
    private val personaWriter = RoommatePersona(repo, generator, zone)
    private val daily = RoommateDaily(repo, generator, gifts, zone)
    private val cursors = ConcurrentHashMap<String, AtomicInteger>()
    private var onStage: String? = null
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
        onStage = repo.episode(day)?.assetId
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

    /** 建档：名字、三样底色与设定一次定好，之后由 [updatePersona] 改。 */
    suspend fun createProfile(
        name: String,
        nature: String,
        pace: String,
        face: String,
        setting: String,
        now: Instant,
        onStage: (RoommateSetupStage) -> Unit = {},
    ): CreatedRoommate = personaWriter.create(name, nature, pace, face, setting, now, onStage)

    /** 改名片：只有第一句话和台词本跟着重发，其余一概不碰。 */
    suspend fun updatePersona(
        name: String,
        nature: String,
        pace: String,
        face: String,
        setting: String,
        now: Instant,
        onStage: (RoommateSetupStage) -> Unit = {},
    ): CreatedRoommate? = personaWriter.update(name, nature, pace, face, setting, now, onStage)

    /** 台词本只向模型要一次；建档时已经要过了，这里只服务没走过新流程的老室友。 */
    suspend fun upgradeLinebook(now: Instant = Instant.now()): Boolean {
        val profile = repo.profile() ?: return false
        val state = repo.state()
        if (state.linebookAiDone) return false
        repo.saveState(state.copy(linebookAiDone = true))
        val ai = generator.linebook(repo.episodeContext(profile, dayOf(now))) ?: return false
        repo.saveLinebook(ai)
        return true
    }

    /**
     * 今天的一包本地内容先落库，保证断网也有东西看。
     * 攒钱目标只保留一件：已有未买的目标就不再新建，价格也永不改。
     */
    suspend fun ensureDaily(now: Instant = Instant.now(), profile: RoommateProfileEntity? = null): Boolean =
        daily.ensure(now, dayOf(now), profile)

    /**
     * 一天最多一次 AI 改写；先落 lastAiDay 再发请求，失败也不反复烧调用。
     * 只替换还没买下的礼物与摊子；菜谱解锁后保持原样，攒钱目标只换内容不换价格。
     */
    suspend fun upgradeDaily(now: Instant = Instant.now()): RoomView? {
        val day = dayOf(now)
        if (!daily.upgrade(now, day)) return null
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
        val asset = nextAsset(action)
        if (action == RoomAction.OUTING) writeOutingNote(day, now)
        val line = if (action == RoomAction.OUTING) nextLine(action) else coquetry(day) ?: nextLine(action)
        return Interaction(asset, line)
    }

    /** 房间开着的时候自己换脸：不用点它，它也会动。 */
    suspend fun idleAsset(): String? = nextAsset(null)

    /** 一次互动一张新脸：正在放的这张跳过，除非池子里只有它。 */
    private suspend fun nextAsset(action: RoomAction?): String? {
        val assets = repo.assets()
        if (assets.isEmpty()) return null
        val key = "asset:" + (action?.key ?: "any")
        val index = cursors.computeIfAbsent(key) { AtomicInteger() }.getAndIncrement()
        val asset = RoommateAssets.next(assets, action, index, onStage) ?: return null
        onStage = asset.assetId
        return asset.assetId
    }

    suspend fun shop(now: Instant = Instant.now()): ShopView {
        val day = dayOf(now)
        val balance = repo.balance()
        return shopOf(repo.offersOfDay(day), repo.activeNear(), repo.works(), balance)
    }

    suspend fun buy(offerId: String, now: Instant = Instant.now()): Purchase {
        val day = dayOf(now)
        val outcome = repo.purchaseOffer(offerId, now.epochSecond, day)
        if (outcome !is PurchaseOutcome.Ok) return Purchase(outcome)
        val offer = repo.offer(offerId)
        val profile = repo.profile()
        if (offer?.channel == RM_CHANNEL_GIFT && profile != null) {
            gifts.response(profile, day, now)
        }
        return Purchase(outcome, offer?.let(::momentOf))
    }

    /** 买下的一件东西换来两句话：它当时的反应进对话框，之后发生的事算旁白。 */
    private fun momentOf(offer: RoommateOfferEntity): RoomMoment =
        // 菜谱的 reaction 是第一次免费那口时说的话，之后每份的反应存在 followUp 里。
        if (offer.channel == RM_CHANNEL_RECIPE) {
            RoomMoment(offer.followUp, "")
        } else {
            RoomMoment(offer.reaction, offer.followUp)
        }

    /** 一天只写一篇；已有正文才上架，不让用户付钱赌生成。 */
    suspend fun writeWork(now: Instant = Instant.now()): RoommateWorkEntity? {
        val day = dayOf(now)
        if (repo.workCountOfDay(day) > 0) return null
        val profile = repo.profile() ?: return null
        val generated = generator.work(repo.episodeContext(profile, day))
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
        val profile = repo.profile()
        return RoomView(
            profile = profile?.toPersona(),
            setting = profile?.setting.orEmpty(),
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
        val row = repo.daily(day) ?: return null
        if (row.coquetryUsed) return null
        val lines = row.coquetry.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
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

    private companion object {
        const val OUTING_NOTE_GAP_SECONDS = 3 * 60 * 60L
        const val NOTEBOOK_DAYS = 30L
        const val LINEBOOK_SIZE = 6
    }
}

private fun RoommateProfileEntity.toPersona(): Persona = Persona(name, nature, pace, face)
