package com.immerlex.core.roommate

/** 室友的四份 prompt。只谈"写什么"，数值、价格、冷却一律由确定性程序管。 */
internal object RoommatePrompts {
    const val SYSTEM = "你在写一个同住小室友的日常片段。只写内容，不决定任何数值、价格或规则。"

    /** 建档时用户愿意等，但也不能无限等；到点落本地句子继续。 */
    const val SETUP_TIMEOUT_MS = 25_000L

    fun daily(context: DailyContext): String = buildString {
        val episode = context.episode
        appendActor(episode)
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

    fun linebook(context: EpisodeContext): String = buildString {
        appendActor(context)
        append(
            """
            为它写一本台词本：被摸、听用户说话、出门回来三种场合各六句。
            每句 10-35 字，有具体动作或小反应，不说教、不提学习/积分/钱，不用感叹号堆情绪。
            只输出 JSON：
            {"touch":["","","","","",""],"talk":["","","","","",""],"outing":["","","","","",""]}
            """.trimIndent(),
        )
    }

    fun intro(context: EpisodeContext): String = buildString {
        appendActor(context)
        append(
            """
            这是它刚搬进来、第一次见到用户的一刻。写它开口说的第一句话：
            不超过 40 字，带一个具体动作，能看出它刚安顿下来；不提学习、积分、钱，不说教。
            只输出 JSON：{"line":""}
            """.trimIndent(),
        )
    }

    fun work(context: EpisodeContext): String = buildString {
        appendActor(context)
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

    fun repair(task: String, previous: String): String = buildString {
        append(task).append("\n\n上一次的输出不合格：\n").append(previous.take(400))
        append("\n请只输出符合字段要求的 JSON，不要解释。")
    }

    private fun StringBuilder.appendActor(context: EpisodeContext): StringBuilder {
        append("角色：").append(context.name).append('。').append(context.persona)
        if (context.setting.isNotBlank()) {
            append("\n用户给的设定（务必遵守）：").append(context.setting)
        }
        return append('\n')
    }
}
