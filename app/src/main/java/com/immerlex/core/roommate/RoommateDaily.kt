package com.immerlex.core.roommate

import com.immerlex.core.db.RM_CHANNEL_NEAR
import com.immerlex.core.db.RM_CHANNEL_RECIPE
import com.immerlex.core.db.RM_KIND_EPISODE
import com.immerlex.core.db.RM_PACK_AI
import com.immerlex.core.db.RM_PACK_LOCAL
import com.immerlex.core.db.RoommateDailyEntity
import com.immerlex.core.db.RoommateEventEntity
import com.immerlex.core.db.RoommateProfileEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class GeneratedEpisode(
    val line: String,
    val choiceA: String,
    val choiceB: String,
    val habit: String?,
    val assetTag: String?,
)

data class GeneratedWork(val title: String, val body: String)

/**
 * 一天一包内容。生成只负责写这些文字；价格、能不能买、菜谱何时现身
 * 都由 [OfferPolicy] 与 [RoommateService] 按事实决定。
 */
data class GiftPlan(
    val name: String,
    val story: String,
    val reaction: String,
    val followUp: String,
    val tier: String,
)

data class StallPlan(
    val name: String,
    val story: String,
    val reaction: String,
)

data class RecipePlan(
    /** 需要先买齐的礼物下标（0..2），同一天有效。 */
    val giftIndices: List<Int>,
    val name: String,
    val story: String,
    /** 买齐后第一口免费时它说的话。 */
    val freeLine: String,
    /** 之后想再吃一份时买下的反应。 */
    val restLine: String,
)

data class PetGiftPlan(
    val name: String,
    val story: String,
    val line: String,
)

data class NearPlan(
    val name: String,
    val story: String,
    val reaction: String,
    val followUp: String,
)

data class DailyPack(
    val episode: GeneratedEpisode,
    val gifts: List<GiftPlan>,
    val stall: List<StallPlan>,
    val recipe: RecipePlan?,
    val petGift: PetGiftPlan?,
    val coquetry: List<String>,
    val near: NearPlan?,
)

/** 它送过的东西，按时间倒序展示在「它送的」里。 */
data class PetGiftView(
    val id: Long,
    val dayKey: Long,
    val name: String,
    val story: String,
    val line: String,
    val cause: String,
)

/** 生成侧真正需要的上下文：底色 + 设定 + 最近的事 + 没做完的事 + 可用素材标签。 */
internal suspend fun RoommateRepository.episodeContext(
    profile: RoommateProfileEntity,
    day: Long,
): EpisodeContext {
    val recent = recentEvents(8)
        .filterNot { it.dayKey == day && it.kind == RM_KIND_EPISODE }
        .map { it.body }
        .filter { it.isNotBlank() }
    return EpisodeContext(
        name = profile.name,
        persona = profile.persona,
        setting = profile.setting,
        recent = recent,
        habits = habits().map { it.label },
        openLoop = openLoops(1).firstOrNull()?.body,
        assetTags = assets().map { it.tag }.distinct(),
    )
}

/**
 * 今天的一包内容：本地先落库保证断网也有东西看，AI 只负责在同一批行上改写。
 * 价格、解锁、习惯这些规则始终由确定性代码管——模型只交文本，越界的字段退回本地。
 */
internal class RoommateDaily(
    private val repo: RoommateRepository,
    private val generator: RoommateGenerator,
    private val gifts: RoommateGiftService,
    private val zone: () -> ZoneId,
) {

    /** 返回 false 表示今天已经备好（或还没有室友），调用方不该再往时间线上叠一条。 */
    suspend fun ensure(now: Instant, day: Long, profile: RoommateProfileEntity? = null): Boolean {
        if (profile == null && repo.profile() == null) return false
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
    suspend fun upgrade(now: Instant, day: Long): Boolean {
        val profile = repo.profile() ?: return false
        val state = repo.state()
        if (state.lastAiDay == day) return false
        repo.saveState(state.copy(lastAiDay = day))
        val daily = repo.daily(day) ?: return false
        val activeNear = repo.activeNear()
        val nearPrice = activeNear?.price ?: OfferPolicy.nearPrice(repo.balance())
        val fallback = RoommateText.dailyPack(day, repo.openLoops(1).firstOrNull()?.body, nearPrice)
        val context = DailyContext(
            episode = repo.episodeContext(profile, day),
            touchedWords = touchedWords(day, now),
            balance = repo.balance(),
            nearPrice = nearPrice,
            recentGifts = gifts.all().take(3).map { it.name },
        )
        val pack = generator.daily(context, fallback) ?: return false
        val episode = repo.episode(day) ?: return false
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
        return true
    }

    private suspend fun touchedWords(day: Long, now: Instant): List<String> {
        val start = LocalDate.ofEpochDay(day).atStartOfDay(zone()).toEpochSecond()
        return repo.touches(start, now.epochSecond + 1)
            .map { it.wordKey }
            .distinct()
            .takeLast(MAX_TOUCHED_WORDS)
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
        const val MAX_TOUCHED_WORDS = 8
    }
}

