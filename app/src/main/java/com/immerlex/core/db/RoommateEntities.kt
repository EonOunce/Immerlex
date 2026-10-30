package com.immerlex.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 室友（v2.0）。所有表都以 `rm_` 开头，和学习、章节、故事圣经完全分开：
 * 改小说档案不会碰到室友，室友也不会反过来改复习排期。
 *
 * 一天的归属日 [dayKey] 是事件发生时的本地日历日（`LocalDate.toEpochDay()`），
 * 事后不再按当前时区重算——换时区不重写旧账。
 */
const val RM_PROFILE_ID = 1
const val RM_STATE_ID = 1

/** 用户选定并生成过的那一个人。只有一行。 */
@Entity(tableName = "rm_profile")
data class RoommateProfileEntity(
    @PrimaryKey val id: Int = RM_PROFILE_ID,
    val name: String,
    /** 三项底色维度：说话方式 / 做事节奏 / 表情。 */
    val nature: String,
    val pace: String,
    val face: String,
    /** 由三项维度落成的性格卡正文。 */
    val persona: String,
    val createdAtSec: Long,
)

/**
 * 一份导入的素材文件。主键是文件内容的 sha-256 前 32 位：
 * 同一条 GIF 从别的文件夹、别的手机再导一次，仍然是同一行，不会重复占空间。
 */
@Entity(tableName = "rm_assets", indices = [Index("tag")])
data class RoommateAssetEntity(
    @PrimaryKey val assetId: String,
    val fileName: String,
    /** 从文件名里读出的动作名（"吹鼻子" / "摇铃" / "通知"），用于挑合适的那一张。 */
    val tag: String,
    val bytes: Long,
    val importedAtSec: Long,
)

/** 小本子里的一行。episode = 今天的故事，choice = 用户选的分支，gift / work / note = 其它。 */
@Entity(tableName = "rm_events", indices = [Index("dayKey"), Index("atSec")])
data class RoommateEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: Long,
    val atSec: Long,
    val kind: String,
    val body: String,
    val assetId: String? = null,
    val choiceA: String = "",
    val choiceB: String = "",
    /** 0 = 还没选，1 = 选了 A，2 = 选了 B。 */
    val chosen: Int = 0,
    /** 还没结束的小事；下一次生成会带上它。 */
    val openLoop: Boolean = false,
    /** 礼物或作品的行号，用来把"买了什么"和"后来怎样"接上。 */
    val sourceId: Long = 0,
)

const val RM_KIND_EPISODE = "episode"
const val RM_KIND_CHOICE = "choice"
const val RM_KIND_GIFT = "gift"
const val RM_KIND_WORK = "work"
const val RM_KIND_NOTE = "note"
const val RM_KIND_RECIPE = "recipe"
const val RM_KIND_PET_GIFT = "pet_gift"

/**
 * 小鱼干的唯一账本。余额 = SUM(amount)，不另存一个"当前余额"列——
 * 那样一旦中途失败就会出现两个说法。
 * [dedupeKey] 唯一：同一个词同一天只能领一次，里程碑也只能领一次。
 */
@Entity(tableName = "rm_ledger", indices = [Index(value = ["dedupeKey"], unique = true), Index("dayKey")])
data class RoommateLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atSec: Long,
    val dayKey: Long,
    val amount: Int,
    val reason: String,
    val dedupeKey: String,
    val note: String = "",
)

const val RM_REASON_WORD = "word"
const val RM_REASON_MILESTONE = "milestone"
const val RM_REASON_SPEND = "spend"

/** 已形成的习惯。是否形成由确定性规则决定，模型只提供"今天做了什么"。 */
@Entity(tableName = "rm_habits")
data class RoommateHabitEntity(
    @PrimaryKey val habitId: String,
    val label: String,
    val learnedDay: Long,
    val learnedAtSec: Long,
)

/** 一个习惯一天最多一条证据；同一天再提一次不会让门槛提前。 */
@Entity(tableName = "rm_evidence", primaryKeys = ["habitId", "dayKey"])
data class RoommateEvidenceEntity(
    val habitId: String,
    val dayKey: Long,
    val eventId: Long,
    val atSec: Long,
)

/** 它自己写出来、摆在摊子上的东西。正文生成了才能上架，买了才能读。 */
@Entity(tableName = "rm_works")
data class RoommateWorkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val price: Int,
    val createdAtSec: Long,
    val purchased: Boolean = false,
)

/** 只有一行：结算水位线，外加"今天是否已经准备过一段生活"。 */
@Entity(tableName = "rm_state")
data class RoommateStateEntity(
    @PrimaryKey val id: Int = RM_STATE_ID,
    /** 已经结算完的最后一个**整天**；0 表示还没开始记账。 */
    val lastSettledDay: Long = 0,
    val lastEpisodeDay: Long = 0,
    /** 这天已经试过 AI 写见闻；失败过也不再反复烧调用，本地内容继续用。 */
    val lastAiDay: Long = 0,
    val lastVisitSec: Long = 0,
    /** 台词本只向模型要一次，失败就用本地六句，不再重试。 */
    @ColumnInfo(defaultValue = "0") val linebookAiDone: Boolean = false,
)

/** 台词本：每个动作六句，互动时按顺序轮换，不做随机抽取。 */
@Entity(tableName = "rm_lines", primaryKeys = ["action", "ordinal"])
data class RoommateLineEntity(
    val action: String,
    val ordinal: Int,
    val line: String,
)

const val RM_PACK_LOCAL = "local"
const val RM_PACK_AI = "ai"

/** 每天一包内容，只有本地/AI 两个来源；购买与规则不写在这里。 */
@Entity(tableName = "rm_daily")
data class RoommateDailyEntity(
    @PrimaryKey val dayKey: Long,
    val source: String,
    /** 撒娇句，换行分隔；一天最多用掉一句。 */
    val coquetry: String,
    val petGiftName: String,
    val petGiftStory: String,
    val petGiftLine: String,
    @ColumnInfo(defaultValue = "0") val coquetryUsed: Boolean = false,
    val createdAtSec: Long,
)

const val RM_CHANNEL_GIFT = "gift"
const val RM_CHANNEL_STALL = "stall"
const val RM_CHANNEL_RECIPE = "recipe"
const val RM_CHANNEL_NEAR = "near"

/**
 * 今天摆在铺子里的东西。价格是程序按渠道与档位定的，模型只写名字和故事。
 * [requires] 只给隐藏菜谱用：逗号分隔的礼物 offerId，买齐才现身。
 * [source] 让 AI 改写只覆盖今天本地生成、还没买下的内容。
 */
@Entity(tableName = "rm_offers", indices = [Index("dayKey"), Index("channel")])
data class RoommateOfferEntity(
    @PrimaryKey val offerId: String,
    val dayKey: Long,
    val channel: String,
    val name: String,
    val story: String,
    val reaction: String,
    val followUp: String,
    val price: Int,
    val purchased: Boolean = false,
    val hidden: Boolean = false,
    val requires: String = "",
    val source: String = RM_PACK_LOCAL,
    val createdAtSec: Long,
)

const val RM_CAUSE_FIRST = "first"
const val RM_CAUSE_RETURN = "return"
const val RM_CAUSE_STREAK = "streak"
const val RM_CAUSE_ABSENCE = "absence"
const val RM_CAUSE_WHIM = "whim"

/** 它主动送的东西：永久收藏，不值小鱼干、不能转卖，也不影响排期。 */
@Entity(tableName = "rm_pet_gifts", indices = [Index("dayKey")])
data class RoommatePetGiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: Long,
    val atSec: Long,
    val name: String,
    val story: String,
    val line: String,
    val cause: String,
)
