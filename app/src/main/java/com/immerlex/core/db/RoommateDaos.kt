package com.immerlex.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/** 一个词在某天最后一次被碰到的时刻；结算按它的时间决定有没有过撤销窗口。 */
data class WordTouch(val wordKey: String, val lastSec: Long)

/** 某个习惯最近的支持日期，给"14 天里 3 天"的确定性规则用。 */
data class EvidenceDay(val dayKey: Long, val atSec: Long)

@Dao
interface RoommateProfileDao {
    @Query("SELECT * FROM rm_profile WHERE id = :id")
    suspend fun byId(id: Int = RM_PROFILE_ID): RoommateProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoommateProfileEntity)
}

@Dao
interface RoommateAssetDao {
    @Query("SELECT * FROM rm_assets ORDER BY tag ASC, assetId ASC")
    suspend fun all(): List<RoommateAssetEntity>

    @Query("SELECT * FROM rm_assets WHERE assetId = :assetId")
    suspend fun byId(assetId: String): RoommateAssetEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: RoommateAssetEntity): Long
}

@Dao
interface RoommateEventDao {
    @Insert
    suspend fun insert(entity: RoommateEventEntity): Long

    @Update
    suspend fun update(entity: RoommateEventEntity)

    @Query("SELECT * FROM rm_events WHERE id = :id")
    suspend fun byId(id: Long): RoommateEventEntity?

    @Query("SELECT * FROM rm_events WHERE dayKey = :dayKey ORDER BY atSec ASC, id ASC")
    suspend fun forDay(dayKey: Long): List<RoommateEventEntity>

    @Query("SELECT * FROM rm_events ORDER BY atSec DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<RoommateEventEntity>

    @Query(
        "SELECT * FROM rm_events WHERE dayKey >= :fromDay AND dayKey <= :toDay " +
            "ORDER BY dayKey DESC, atSec ASC, id ASC",
    )
    suspend fun between(fromDay: Long, toDay: Long): List<RoommateEventEntity>

    @Query("SELECT * FROM rm_events WHERE kind = :kind AND dayKey = :dayKey LIMIT 1")
    suspend fun firstOfDay(kind: String, dayKey: Long): RoommateEventEntity?

    @Query("SELECT COUNT(*) FROM rm_events WHERE dayKey = :dayKey AND kind = :kind")
    suspend fun countOfDay(kind: String, dayKey: Long): Int

    @Query("SELECT * FROM rm_events WHERE openLoop = 1 ORDER BY atSec DESC LIMIT :limit")
    suspend fun openLoops(limit: Int): List<RoommateEventEntity>

    @Query("UPDATE rm_events SET openLoop = 0 WHERE id = :id")
    suspend fun closeLoop(id: Long)

    @Query("UPDATE rm_events SET chosen = :chosen WHERE id = :id")
    suspend fun setChosen(id: Long, chosen: Int)
}

@Dao
interface RoommateLedgerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: RoommateLedgerEntity): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM rm_ledger")
    suspend fun balance(): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM rm_ledger WHERE dayKey = :dayKey AND reason = :reason")
    suspend fun sumForDay(dayKey: Long, reason: String): Int

    @Query("SELECT COUNT(*) FROM rm_ledger WHERE dayKey = :dayKey AND reason = 'word'")
    suspend fun wordCredits(dayKey: Long): Int

    @Query("SELECT COUNT(*) FROM rm_ledger WHERE reason = :reason AND dedupeKey LIKE :prefix || '%'")
    suspend fun countByKeyPrefix(reason: String, prefix: String): Int
}

@Dao
interface RoommateHabitDao {
    @Query("SELECT * FROM rm_habits ORDER BY learnedDay DESC, habitId ASC")
    suspend fun all(): List<RoommateHabitEntity>

    @Query("SELECT * FROM rm_habits WHERE habitId = :habitId")
    suspend fun byId(habitId: String): RoommateHabitEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: RoommateHabitEntity): Long
}

@Dao
interface RoommateEvidenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: RoommateEvidenceEntity): Long

    @Query("SELECT dayKey, atSec FROM rm_evidence WHERE habitId = :habitId ORDER BY dayKey DESC")
    suspend fun daysFor(habitId: String): List<EvidenceDay>
}

@Dao
interface RoommateWorkDao {
    @Insert
    suspend fun insert(entity: RoommateWorkEntity): Long

    @Query("SELECT * FROM rm_works ORDER BY createdAtSec DESC, id DESC")
    suspend fun all(): List<RoommateWorkEntity>

    @Query("SELECT * FROM rm_works WHERE id = :id")
    suspend fun byId(id: Long): RoommateWorkEntity?

    @Query("UPDATE rm_works SET purchased = 1 WHERE id = :id")
    suspend fun markPurchased(id: Long)
}

@Dao
interface RoommateStateDao {
    @Query("SELECT * FROM rm_state WHERE id = :id")
    suspend fun byId(id: Int = RM_STATE_ID): RoommateStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoommateStateEntity)
}

@Dao
interface RoommateLineDao {
    @Query("SELECT * FROM rm_lines ORDER BY action ASC, ordinal ASC")
    suspend fun all(): List<RoommateLineEntity>

    @Query("SELECT * FROM rm_lines WHERE action = :action ORDER BY ordinal ASC")
    suspend fun forAction(action: String): List<RoommateLineEntity>

    @Query("SELECT COUNT(*) FROM rm_lines")
    suspend fun count(): Int

    @Query("DELETE FROM rm_lines")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RoommateLineEntity>)
}

@Dao
interface RoommateDailyDao {
    @Query("SELECT * FROM rm_daily WHERE dayKey = :dayKey")
    suspend fun byDay(dayKey: Long): RoommateDailyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoommateDailyEntity)

    @Query("UPDATE rm_daily SET coquetryUsed = 1 WHERE dayKey = :dayKey")
    suspend fun markCoquetryUsed(dayKey: Long)
}

@Dao
interface RoommateOfferDao {
    @Query("SELECT * FROM rm_offers WHERE offerId = :offerId")
    suspend fun byId(offerId: String): RoommateOfferEntity?

    @Query("SELECT * FROM rm_offers WHERE dayKey = :dayKey ORDER BY channel ASC, offerId ASC")
    suspend fun forDay(dayKey: Long): List<RoommateOfferEntity>

    @Query("SELECT * FROM rm_offers WHERE channel = 'near' AND purchased = 0 ORDER BY createdAtSec DESC LIMIT 1")
    suspend fun activeNear(): RoommateOfferEntity?

    @Query("SELECT COUNT(*) FROM rm_offers WHERE channel = :channel AND purchased = 1 AND dayKey = :dayKey")
    suspend fun purchasedCount(channel: String, dayKey: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoommateOfferEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RoommateOfferEntity>)

    @Query("UPDATE rm_offers SET purchased = 1 WHERE offerId = :offerId")
    suspend fun markPurchased(offerId: String)

    @Query("UPDATE rm_offers SET hidden = 0 WHERE offerId = :offerId")
    suspend fun reveal(offerId: String)

    @Query("DELETE FROM rm_offers WHERE dayKey = :dayKey AND channel = :channel AND purchased = 0 AND source = 'local'")
    suspend fun deleteLocalUnpurchased(dayKey: Long, channel: String)
}

@Dao
interface RoommatePetGiftDao {
    @Insert
    suspend fun insert(entity: RoommatePetGiftEntity): Long

    @Query("SELECT * FROM rm_pet_gifts ORDER BY atSec DESC, id DESC")
    suspend fun all(): List<RoommatePetGiftEntity>

    @Query("SELECT * FROM rm_pet_gifts WHERE dayKey = :dayKey ORDER BY atSec DESC, id DESC")
    suspend fun forDay(dayKey: Long): List<RoommatePetGiftEntity>

    @Query("SELECT MAX(dayKey) FROM rm_pet_gifts")
    suspend fun lastDay(): Long?

    @Query("SELECT COUNT(*) FROM rm_pet_gifts WHERE dayKey >= :fromDay AND dayKey <= :toDay")
    suspend fun countBetween(fromDay: Long, toDay: Long): Int
}

/** 学习记录里"当天碰过哪些词、各自最后一次是什么时候"；结算只认过了撤销窗口的那些。 */
@Dao
interface RoommateSettlementDao {
    @Query(
        """
        SELECT lower(trim(w.word)) AS wordKey, MAX(l.reviewEpochSec) AS lastSec
        FROM review_logs l JOIN words w ON w.id = l.wordId
        WHERE l.reviewEpochSec >= :fromSec AND l.reviewEpochSec < :toSec
        GROUP BY lower(trim(w.word))
        ORDER BY lastSec ASC
        """,
    )
    suspend fun touchesBetween(fromSec: Long, toSec: Long): List<WordTouch>
}
