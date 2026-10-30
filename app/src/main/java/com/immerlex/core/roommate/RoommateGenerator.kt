package com.immerlex.core.roommate

import com.immerlex.core.ai.ChapterParser
import com.immerlex.core.ai.LlmClient
import com.immerlex.core.ai.LlmException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 生成时需要的最小上下文：底色 + 最近发生的事 + 还没结束的事 + 可用素材。 */
data class EpisodeContext(
    val name: String,
    val persona: String,
    val recent: List<String>,
    val habits: List<String>,
    val openLoop: String?,
    val assetTags: List<String>,
)

/** 一天一包的上下文。词只借意象，prompt 里明确禁止提学习、复习和次数。 */
data class DailyContext(
    val episode: EpisodeContext,
    val touchedWords: List<String>,
    val balance: Int,
    val nearPrice: Int,
    val recentGifts: List<String>,
)

/**
 * 只写内容，不碰规则：模型返回的是"今天发生了什么、铺子里摆什么"，
 * 不能改余额、价格、冷却、菜谱何时解锁或礼物何时触发。字段越界一律判无效，
 * 无效的那一段退回本地内容，其余段仍可采用。
 */
class RoommateGenerator(
    private val clientFor: () -> LlmClient,
    private val aiUsable: () -> Boolean,
    private val maxRepairs: Int = 1,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun daily(context: DailyContext, fallback: DailyPack): DailyPack? {
        if (!aiUsable()) return null
        val task = dailyPrompt(context)
        var previous = ""
        repeat(maxRepairs + 1) { attempt ->
            val raw = try {
                clientFor().complete(SYSTEM, if (attempt == 0) task else repair(task, previous))
            } catch (e: LlmException) {
                return null
            }
            previous = raw
            val dto = parseDaily(raw) ?: return@repeat
            return mergeDaily(dto, context, fallback)
        }
        return null
    }

    suspend fun linebook(context: EpisodeContext): Map<RoomAction, List<String>>? {
        if (!aiUsable()) return null
        val task = linebookPrompt(context)
        var previous = ""
        repeat(maxRepairs + 1) { attempt ->
            val raw = try {
                clientFor().complete(SYSTEM, if (attempt == 0) task else repair(task, previous))
            } catch (e: LlmException) {
                return null
            }
            previous = raw
            val dto = parseLinebook(raw) ?: return@repeat
            return mapOf(
                RoomAction.TOUCH to dto.touch.take(6),
                RoomAction.TALK to dto.talk.take(6),
                RoomAction.OUTING to dto.outing.take(6),
            )
        }
        return null
    }

    suspend fun work(context: EpisodeContext): GeneratedWork? {
        if (!aiUsable()) return null
        val task = workPrompt(context)
        var previous = ""
        repeat(maxRepairs + 1) { attempt ->
            val raw = try {
                clientFor().complete(SYSTEM, if (attempt == 0) task else repair(task, previous))
            } catch (e: LlmException) {
                return null
            }
            previous = raw
            val parsed = parseWork(raw)
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseDaily(raw: String): DailyDto? {
        val body = ChapterParser.extractJson(raw) ?: return null
        return runCatching { json.decodeFromString<DailyDto>(body) }.getOrNull()
    }

    private fun mergeDaily(dto: DailyDto, context: DailyContext, fallback: DailyPack): DailyPack {
        val episode = parseEpisode(dto.episode, context.episode) ?: fallback.episode
        val gifts = dto.gifts.mapNotNull(::parseGift).takeIf { it.size == 3 } ?: fallback.gifts
        val stall = dto.stall.mapNotNull(::parseStall).takeIf { it.size == 2 } ?: fallback.stall
        val recipe = dto.recipe?.let(::parseRecipe) ?: fallback.recipe
        val petGift = dto.petGift?.let(::parsePetGift) ?: fallback.petGift
        val coquetry = dto.coquetry.map { clean(it) }
            .filter { it.length in 4..40 }
            .take(3)
            .takeIf { it.size >= 2 } ?: fallback.coquetry
        val near = dto.near?.let(::parseNear) ?: fallback.near
        return DailyPack(episode, gifts, stall, recipe, petGift, coquetry, near)
    }

    private fun parseEpisode(dto: EpisodeDto?, context: EpisodeContext): GeneratedEpisode? {
        dto ?: return null
        val line = clean(dto.line)
        val a = clean(dto.choiceA)
        val b = clean(dto.choiceB)
        if (line.length !in 8..120) return null
        if (a.length !in 2..12 || b.length !in 2..12) return null
        val habit = HabitPolicy.normalize(dto.habit.orEmpty())
        val asset = dto.asset?.trim()?.takeIf { it in context.assetTags }
        return GeneratedEpisode(line, a, b, habit, asset)
    }

    private fun parseGift(dto: GiftDto): GiftPlan? {
        val name = clean(dto.name)
        val story = clean(dto.story)
        val reaction = clean(dto.reaction)
        val followUp = clean(dto.followUp)
        val tier = dto.tier.trim().lowercase()
        if (name.length !in 2..10) return null
        if (story.length !in 8..80) return null
        if (reaction.length !in 6..60) return null
        if (followUp.length !in 6..80) return null
        if (tier !in setOf("small", "medium", "large")) return null
        return GiftPlan(name, story, reaction, followUp, tier)
    }

    private fun parseStall(dto: StallDto): StallPlan? {
        val name = clean(dto.name)
        val story = clean(dto.story)
        val reaction = clean(dto.reaction)
        if (name.length !in 2..10) return null
        if (story.length !in 8..80) return null
        if (reaction.length !in 6..60) return null
        return StallPlan(name, story, reaction)
    }

    private fun parseRecipe(dto: RecipeDto): RecipePlan? {
        val indices = dto.giftIndices.distinct()
        val name = clean(dto.name)
        val story = clean(dto.story)
        val free = clean(dto.freeLine)
        val rest = clean(dto.restLine)
        if (indices.size !in 2..3) return null
        if (indices.any { it !in 0..2 }) return null
        if (name.length !in 2..10) return null
        if (story.length !in 8..80) return null
        if (free.length !in 4..60 || rest.length !in 4..60) return null
        return RecipePlan(indices, name, story, free, rest)
    }

    private fun parsePetGift(dto: PetGiftDto): PetGiftPlan? {
        val name = clean(dto.name)
        val story = clean(dto.story)
        val line = clean(dto.line)
        if (name.length !in 2..12) return null
        if (story.length !in 8..80) return null
        if (line.length !in 4..60) return null
        return PetGiftPlan(name, story, line)
    }

    private fun parseNear(dto: NearDto): NearPlan? {
        val name = clean(dto.name)
        val story = clean(dto.story)
        val reaction = clean(dto.reaction)
        val followUp = clean(dto.followUp)
        if (name.length !in 2..12) return null
        if (story.length !in 8..80) return null
        if (reaction.length !in 4..60) return null
        if (followUp.length !in 6..80) return null
        return NearPlan(name, story, reaction, followUp)
    }

    private fun parseLinebook(raw: String): LinebookDto? {
        val body = ChapterParser.extractJson(raw) ?: return null
        val dto = runCatching { json.decodeFromString<LinebookDto>(body) }.getOrNull() ?: return null
        fun cleanPool(lines: List<String>): List<String> =
            lines.map { clean(it) }.filter { it.length in 4..40 }
        val touch = cleanPool(dto.touch)
        val talk = cleanPool(dto.talk)
        val outing = cleanPool(dto.outing)
        if (touch.size < 6 || talk.size < 6 || outing.size < 6) return null
        return dto.copy(touch = touch, talk = talk, outing = outing)
    }

    private fun parseWork(raw: String): GeneratedWork? {
        val body = ChapterParser.extractJson(raw) ?: return null
        val dto = runCatching { json.decodeFromString<WorkDto>(body) }.getOrNull() ?: return null
        val title = dto.title.trim()
        val text = dto.body.trim()
        if (title.length !in 2..14) return null
        if (text.length !in 120..1200) return null
        return GeneratedWork(title, text)
    }

    private fun dailyPrompt(context: DailyContext): String = buildString {
        val episode = context.episode
        append("角色：").append(episode.name).append('。').append(episode.persona).append('\n')
        append("它和用户同住，会自己出门、打工、交朋友；今天不必发生大事。\n")
        if (episode.recent.isNotEmpty()) {
            append("最近发生的事：\n")
            episode.recent.forEach { append("- ").append(it).append('\n') }
        }
        if (episode.habits.isNotEmpty()) {
            append("已经形成的习惯（可以自然带到，不要每句都提）：")
                .append(episode.habits.joinToString("、")).append('\n')
        }
        episode.openLoop?.let { append("还没结束的事：").append(it).append('\n') }
        if (context.recentGifts.isNotEmpty()) {
            append("最近收到的礼物：").append(context.recentGifts.joinToString("、")).append('\n')
        }
        if (context.touchedWords.isNotEmpty()) {
            append("今天书里出现过的英文词（只借它们的意象，绝对不要提学习、复习、背词、次数）：")
                .append(context.touchedWords.joinToString("、")).append('\n')
        }
        append("它的余额：").append(context.balance).append(" 条小鱼干。")
        append("它正在攒钱买一件约 ").append(context.nearPrice).append(" 条的东西。\n")
        append("可用动作素材：").append(episode.assetTags.joinToString("、").ifEmpty { "无" }).append('\n')
        append(
            """
            写今天的一包内容。全部用中文，具体、有动作、不说教、不提钱和积分。
            1. episode：2-3 句见闻；两个短选择各不超过 8 个字；habit 是延续的 2-6 字动作名或空；asset 从素材里原样挑或空。
            2. gifts：用户送它的三件小东西，tier 依次是 small、medium、large；每件给名字、来由、它收下时的反应、之后留下的后续。
            3. stall：它自己做、摆在摊子上卖给用户的两件小东西，给名字、来历、被买走时的反应。
            4. recipe：用其中 2-3 件礼物（giftIndices 是 0/1/2 的下标）能做的一道小东西；给名字、做法、第一口免费时它说的话、再要一份时它说的话。
            5. petGift：它可能主动送用户的一件小礼物，给名字、来历、递过来时说的话。
            6. coquetry：三句它偶尔撒娇会说的话，每句不超过 30 字。
            7. near：它攒钱想买的一件东西，价值和上面说的目标价位相称；给名字、为什么想要、买到时的反应、之后的后续。
            只输出 JSON：
            {"episode":{"line":"","choiceA":"","choiceB":"","habit":"","asset":""},
             "gifts":[{"name":"","story":"","reaction":"","followUp":"","tier":"small"}],
             "stall":[{"name":"","story":"","reaction":""}],
             "recipe":{"giftIndices":[0,1],"name":"","story":"","freeLine":"","restLine":""},
             "petGift":{"name":"","story":"","line":""},
             "coquetry":["","",""],
             "near":{"name":"","story":"","reaction":"","followUp":""}}
            """.trimIndent(),
        )
    }

    private fun linebookPrompt(context: EpisodeContext): String = buildString {
        append("角色：").append(context.name).append('。').append(context.persona).append('\n')
        append(
            """
            为它写一本台词本：被摸、听用户说话、出门回来三种场合各六句。
            每句 10-35 字，有具体动作或小反应，不说教、不提学习/积分/钱，不用感叹号堆情绪。
            只输出 JSON：
            {"touch":["","","","","",""],"talk":["","","","","",""],"outing":["","","","","",""]}
            """.trimIndent(),
        )
    }

    private fun workPrompt(context: EpisodeContext): String = buildString {
        append("角色：").append(context.name).append('。').append(context.persona).append('\n')
        if (context.recent.isNotEmpty()) {
            append("最近：\n")
            context.recent.take(5).forEach { append("- ").append(it).append('\n') }
        }
        append(
            """
            它写了一篇短小的观察手记，摆在摊子上卖。要有具体物件和一件小事，150-400 字，分成 2-3 段。
            不要提词库、学习、积分；不要写成功学结论。只输出 JSON：
            {"title":"不超过 10 个字","body":"正文"}
            """.trimIndent(),
        )
    }

    private fun repair(task: String, previous: String): String = buildString {
        append(task).append("\n\n上一次的输出不合格：\n").append(previous.take(400))
        append("\n请只输出符合字段要求的 JSON，不要解释。")
    }

    private fun clean(value: String): String = value.trim().replace('\n', ' ')

    @Serializable
    private data class EpisodeDto(
        val line: String = "",
        val choiceA: String = "",
        val choiceB: String = "",
        val habit: String? = null,
        val asset: String? = null,
    )

    @Serializable
    private data class GiftDto(
        val name: String = "",
        val story: String = "",
        val reaction: String = "",
        val followUp: String = "",
        val tier: String = "",
    )

    @Serializable
    private data class StallDto(
        val name: String = "",
        val story: String = "",
        val reaction: String = "",
    )

    @Serializable
    private data class RecipeDto(
        val giftIndices: List<Int> = emptyList(),
        val name: String = "",
        val story: String = "",
        val freeLine: String = "",
        val restLine: String = "",
    )

    @Serializable
    private data class PetGiftDto(
        val name: String = "",
        val story: String = "",
        val line: String = "",
    )

    @Serializable
    private data class NearDto(
        val name: String = "",
        val story: String = "",
        val reaction: String = "",
        val followUp: String = "",
    )

    @Serializable
    private data class DailyDto(
        val episode: EpisodeDto? = null,
        val gifts: List<GiftDto> = emptyList(),
        val stall: List<StallDto> = emptyList(),
        val recipe: RecipeDto? = null,
        val petGift: PetGiftDto? = null,
        val coquetry: List<String> = emptyList(),
        val near: NearDto? = null,
    )

    @Serializable
    private data class LinebookDto(
        val touch: List<String> = emptyList(),
        val talk: List<String> = emptyList(),
        val outing: List<String> = emptyList(),
    )

    @Serializable
    private data class WorkDto(val title: String = "", val body: String = "")

    private companion object {
        const val SYSTEM = "你在写一个同住小室友的日常片段。只写内容，不决定任何数值、价格或规则。"
    }
}
