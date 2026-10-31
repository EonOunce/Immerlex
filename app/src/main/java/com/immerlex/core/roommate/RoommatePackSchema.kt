package com.immerlex.core.roommate

import com.immerlex.core.ai.ChapterParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val packJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * 模型输出的字段闸门：越界的那一段退回本地内容，其余段仍可采用。
 * 这里只判"这句话像不像话"，不判钱、价格、冷却——那些从来不由模型说了算。
 */
internal object RoommatePackSchema {

    fun daily(raw: String, context: DailyContext, fallback: DailyPack): DailyPack? {
        val dto = decode<DailyDto>(raw) ?: return null
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

    fun linebook(raw: String): Map<RoomAction, List<String>>? {
        val dto = decode<LinebookDto>(raw) ?: return null
        fun pool(lines: List<String>): List<String> = lines.map { clean(it) }.filter { it.length in 4..40 }
        val touch = pool(dto.touch)
        val talk = pool(dto.talk)
        val outing = pool(dto.outing)
        if (touch.size < 6 || talk.size < 6 || outing.size < 6) return null
        return mapOf(
            RoomAction.TOUCH to touch.take(6),
            RoomAction.TALK to talk.take(6),
            RoomAction.OUTING to outing.take(6),
        )
    }

    fun intro(raw: String): String? =
        decode<IntroDto>(raw)?.let { clean(it.line) }?.takeIf { it.length in 4..80 }

    fun work(raw: String): GeneratedWork? {
        val dto = decode<WorkDto>(raw) ?: return null
        val title = dto.title.trim()
        val body = dto.body.trim()
        if (title.length !in 2..14) return null
        if (body.length !in 120..1200) return null
        return GeneratedWork(title, body)
    }

    private inline fun <reified T : Any> decode(raw: String): T? {
        val body = ChapterParser.extractJson(raw) ?: return null
        return runCatching { packJson.decodeFromString<T>(body) }.getOrNull()
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

    private fun clean(value: String): String = value.trim().replace('\n', ' ')
}

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
private data class IntroDto(val line: String = "")

@Serializable
private data class WorkDto(val title: String = "", val body: String = "")
