package com.immerlex.core.roommate

import com.immerlex.core.ai.LlmClient
import com.immerlex.core.ai.LlmException

/** 生成时需要的最小上下文：底色 + 用户设定 + 最近发生的事 + 还没结束的事 + 可用素材。 */
data class EpisodeContext(
    val name: String,
    val persona: String,
    val setting: String = "",
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
 * 不能改余额、价格、冷却、菜谱何时解锁或礼物何时触发。
 * prompt 在 [RoommatePrompts]，字段闸门在 [RoommatePackSchema]，这里只管调用与修复重试。
 */
class RoommateGenerator(
    private val clientFor: () -> LlmClient,
    private val aiUsable: () -> Boolean,
    private val maxRepairs: Int = 1,
) {
    suspend fun daily(context: DailyContext, fallback: DailyPack): DailyPack? {
        if (!aiUsable()) return null
        val task = RoommatePrompts.daily(context)
        var previous = ""
        repeat(maxRepairs + 1) { attempt ->
            val raw = call(task, attempt, previous) ?: return null
            previous = raw
            val pack = RoommatePackSchema.daily(raw, context, fallback)
            if (pack != null) return pack
        }
        return null
    }

    suspend fun linebook(context: EpisodeContext): Map<RoomAction, List<String>>? {
        if (!aiUsable()) return null
        val task = RoommatePrompts.linebook(context)
        var previous = ""
        repeat(maxRepairs + 1) { attempt ->
            val raw = call(task, attempt, previous, RoommatePrompts.SETUP_TIMEOUT_MS) ?: return null
            previous = raw
            val book = RoommatePackSchema.linebook(raw)
            if (book != null) return book
        }
        return null
    }

    /** 建档时它对用户说的第一句话；失败就由本地句子顶上，不让建档卡死。 */
    suspend fun intro(context: EpisodeContext): String? {
        if (!aiUsable()) return null
        val task = RoommatePrompts.intro(context)
        var previous = ""
        repeat(maxRepairs + 1) { attempt ->
            val raw = call(task, attempt, previous, RoommatePrompts.SETUP_TIMEOUT_MS) ?: return null
            previous = raw
            val line = RoommatePackSchema.intro(raw)
            if (line != null) return line
        }
        return null
    }

    suspend fun work(context: EpisodeContext): GeneratedWork? {
        if (!aiUsable()) return null
        val task = RoommatePrompts.work(context)
        var previous = ""
        repeat(maxRepairs + 1) { attempt ->
            val raw = call(task, attempt, previous) ?: return null
            previous = raw
            val written = RoommatePackSchema.work(raw)
            if (written != null) return written
        }
        return null
    }

    private suspend fun call(
        task: String,
        attempt: Int,
        previous: String,
        timeoutMillis: Long? = null,
    ): String? = try {
        val prompt = if (attempt == 0) task else RoommatePrompts.repair(task, previous)
        if (timeoutMillis == null) clientFor().complete(RoommatePrompts.SYSTEM, prompt)
        else clientFor().complete(RoommatePrompts.SYSTEM, prompt, timeoutMillis = timeoutMillis)
    } catch (e: LlmException) {
        null
    }
}
