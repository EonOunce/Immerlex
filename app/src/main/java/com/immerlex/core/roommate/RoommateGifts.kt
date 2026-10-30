package com.immerlex.core.roommate

import com.immerlex.core.db.RoommateDailyEntity
import com.immerlex.core.db.RoommateProfileEntity
import java.time.Instant
import java.time.ZoneId

/**
 * 它主动送东西的编排。触发理由全部来自 [PetGiftPolicy]，内容来自当天的包；
 * 这里只负责查事实、落库和把结果给界面，不做任何概率抽取。
 */
class RoommateGiftService(
    private val repo: RoommateRepository,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    suspend fun all(): List<PetGiftView> = repo.allPetGifts().map {
        PetGiftView(it.id, it.dayKey, it.name, it.story, it.line, it.cause)
    }

    suspend fun today(dayKey: Long): PetGiftView? = repo.petGiftsOfDay(dayKey).firstOrNull()?.let {
        PetGiftView(it.id, it.dayKey, it.name, it.story, it.line, it.cause)
    }

    /** 进房间时：久别、连续 7/30 天、或按天固定的"就是想送"。 */
    suspend fun proactive(profile: RoommateProfileEntity, day: Long, streak: Int, now: Instant): PetGiftView? {
        val daily = repo.daily(day) ?: return null
        val lastVisitDay = repo.state().lastVisitSec.takeIf { it > 0 }?.let { dayOf(it) }
        val lastGiftDay = repo.petGiftLastDay()
        val cause = PetGiftPolicy.proactiveCause(
            today = day,
            creationDay = creationDay(profile),
            lastVisitDay = lastVisitDay,
            streak = streak,
            lastGiftDay = lastGiftDay,
            giftsLast7Days = repo.petGiftsBetween(day - 6, day),
            whimRoll = PetGiftPolicy.whimRoll(day),
        ) ?: return null
        return grant(daily, day, now, cause)
    }

    /** 收到用户礼物后的回礼。只有"送给它"的礼物会走到这里，摊子和菜谱不算。 */
    suspend fun response(profile: RoommateProfileEntity, day: Long, now: Instant): PetGiftView? {
        val daily = repo.daily(day) ?: return null
        val lastGiftDay = repo.petGiftLastDay()
        val cause = PetGiftPolicy.responseCause(
            today = day,
            creationDay = creationDay(profile),
            hasEverGifted = lastGiftDay != null,
            lastGiftDay = lastGiftDay,
            giftsLast7Days = repo.petGiftsBetween(day - 6, day),
        ) ?: return null
        return grant(daily, day, now, cause)
    }

    private suspend fun grant(
        daily: RoommateDailyEntity,
        day: Long,
        now: Instant,
        cause: String,
    ): PetGiftView {
        val fallback = RoommateText.petGift(day)
        val name = daily.petGiftName.ifBlank { fallback.name }
        val story = daily.petGiftStory.ifBlank { fallback.story }
        val line = daily.petGiftLine.ifBlank { fallback.line }
        val id = repo.grantPetGift(day, now.epochSecond, name, story, line, cause)
        return PetGiftView(id, day, name, story, line, cause)
    }

    private fun dayOf(epochSec: Long): Long =
        Instant.ofEpochSecond(epochSec).atZone(zone()).toLocalDate().toEpochDay()

    private fun creationDay(profile: RoommateProfileEntity): Long =
        Instant.ofEpochSecond(profile.createdAtSec).atZone(zone()).toLocalDate().toEpochDay()
}
