package com.immerlex.core.roommate

/**
 * 小鱼干只由真实学习记录产生，不设每日封顶，但收益递减：
 * 前 5 个词每个 2 条，第 6–30 个每个 1 条，第 31 个起每 2 个词 1 条。
 * 满 5 个另补 5 条。第 [index] 个词值多少由 [rewardAt] 单独给出，
 * 结算按当天已经记过的词数往后排，所以中间补结算也不会打乱档位。
 */
object RewardPolicy {
    const val EARLY_LIMIT = 5
    const val FULL_LIMIT = 30
    const val MILESTONE_AT = 5
    const val MILESTONE_BONUS = 5

    /** 与 ScheduleService.UNDO_WINDOW_SECONDS 对齐：过了这段时间的记录才不可撤销。 */
    const val UNDO_GRACE_SECONDS = 8L

    /** 第 [index] 个词（从 1 开始）值多少条小鱼干。 */
    fun rewardAt(index: Int): Int = when {
        index <= 0 -> 0
        index <= EARLY_LIMIT -> 2
        index <= FULL_LIMIT -> 1
        index % 2 == 0 -> 1
        else -> 0
    }

    fun rewardFor(count: Int): Int {
        if (count <= 0) return 0
        val words = (1..count).sumOf { rewardAt(it) }
        return words + if (count >= MILESTONE_AT) MILESTONE_BONUS else 0
    }

    fun wordKey(day: Long, wordKey: String): String = "w:$day:$wordKey"

    fun milestoneKey(day: Long): String = "m:$day:$MILESTONE_AT"

    fun offerKey(offerId: String): String = "buy:$offerId"

    fun workKey(workId: Long): String = "work:$workId"
}

/** 铺子里的价格是程序定的，模型只给档位；攒钱目标的价位来自固定阶梯。 */
object OfferPolicy {
    const val WORK_PRICE = 12
    const val RECIPE_PRICE = 12
    val NEAR_LADDER = listOf(3, 5, 8, 12, 18, 25, 35, 50, 70, 100, 140, 200)
    val STALL_PRICES = listOf(3, 8)

    fun giftPrice(tier: String): Int = when (tier) {
        "small" -> 5
        "medium" -> 12
        "large" -> 25
        else -> 12
    }

    /** 比当前余额高一级的目标；已经攒过最高一档就停在最高档。 */
    fun nearPrice(balance: Int): Int =
        NEAR_LADDER.firstOrNull { it > balance } ?: NEAR_LADDER.last()

    fun nearGap(price: Int, balance: Int): Int = (price - balance).coerceAtLeast(0)
}

/**
 * 它主动送东西的闸门。触发理由只由确定性事实决定：用户送过礼、连续天数、
 * 离开多久、以及按天固定的"就是想说"；每天最多一件、七天最多两件、两件至少隔三天。
 */
object PetGiftPolicy {
    const val MIN_GAP_DAYS = 3L
    const val MAX_PER_WEEK = 2

    fun blocked(today: Long, creationDay: Long, lastGiftDay: Long?, giftsLast7Days: Int): Boolean =
        today == creationDay ||
            giftsLast7Days >= MAX_PER_WEEK ||
            (lastGiftDay != null && today - lastGiftDay < MIN_GAP_DAYS)

    /** 进房间时它可能主动送：久别 > 连续 7/30 天 > 偶尔想送。 */
    fun proactiveCause(
        today: Long,
        creationDay: Long,
        lastVisitDay: Long?,
        streak: Int,
        lastGiftDay: Long?,
        giftsLast7Days: Int,
        whimRoll: Int,
    ): String? {
        if (blocked(today, creationDay, lastGiftDay, giftsLast7Days)) return null
        if (lastVisitDay != null && today - lastVisitDay >= MIN_GAP_DAYS) return "absence"
        if (streak == 7 || streak == 30) return "streak"
        if (whimRoll == 0) return "whim"
        return null
    }

    /** 收到用户礼物后的回礼：第一次必回，之后隔了至少三天才再回。 */
    fun responseCause(
        today: Long,
        creationDay: Long,
        hasEverGifted: Boolean,
        lastGiftDay: Long?,
        giftsLast7Days: Int,
    ): String? {
        if (blocked(today, creationDay, lastGiftDay, giftsLast7Days)) return null
        if (!hasEverGifted) return "first"
        if (lastGiftDay != null && today - lastGiftDay >= MIN_GAP_DAYS) return "return"
        return null
    }

    /** 约四分之一的日子会"就是想送"；同一天重进房间结果不变。 */
    fun whimRoll(day: Long): Int = Math.floorMod(day * 31 + 17, 4L).toInt()
}

/**
 * 习惯用确定性规则，不用概率抽取：14 天里有 3 个不同日子做过同一件事，就学会。
 * 这样用户看到的行为可预期，测试也能一眼断言；"只出现一次就变专家"依然不会发生。
 */
object HabitPolicy {
    const val WINDOW_DAYS = 14L
    const val MIN_EVIDENCE_DAYS = 3

    fun shouldLearn(evidenceDays: List<Long>, today: Long, alreadyLearned: Boolean): Boolean {
        if (alreadyLearned) return false
        val from = today - (WINDOW_DAYS - 1)
        return evidenceDays.count { it in from..today } >= MIN_EVIDENCE_DAYS
    }

    /** 模型回的习惯名先收紧：空、过长、带标点的一律不认，免得一天冒出三个新习惯。 */
    fun normalize(label: String): String? {
        val clean = label.trim().replace(Regex("\\s+"), "")
        if (clean.length !in 1..8) return null
        if (clean.any { it in "，。！？,.!?、；;：:（）()「」\"'" }) return null
        return clean
    }
}
