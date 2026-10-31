package com.immerlex.core.roommate

import com.immerlex.core.db.RM_KIND_NOTE
import com.immerlex.core.db.RoommateEventEntity
import com.immerlex.core.db.RoommateProfileEntity
import java.time.Instant
import java.time.ZoneId

/**
 * 人设只决定它怎么说话。建档定下名字、三样底色和多留的一句设定；改名片也只重发
 * 第一句话和整本台词——钱、账本、小本子、已经买下的东西都不在这个类手里。
 */
class RoommatePersona(
    private val repo: RoommateRepository,
    private val generator: RoommateGenerator,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    /**
     * 一次定好：先要它的第一句话，再要整本台词，最后才落库。
     * 放到最后写是因为建档中途退出程序时，宁可"还没认识"，也不要一个没有台词本的房间。
     * 模型慢、没配 key、返回不合格都由本地句子顶上——建档一定会完成，只是等不等得到 AI 的区别。
     */
    suspend fun create(
        name: String,
        nature: String,
        pace: String,
        face: String,
        setting: String,
        now: Instant,
        onStage: (RoommateSetupStage) -> Unit = {},
    ): CreatedRoommate {
        val clean = nameOf(name)
        val persona = Persona(clean, nature, pace, face)
        val card = settingOf(setting)
        val (intro, book) = ask(persona, card, now, onStage)
        val day = dayOf(now)
        repo.runTransaction {
            repo.saveProfile(
                RoommateProfileEntity(
                    name = clean,
                    nature = nature,
                    pace = pace,
                    face = face,
                    persona = persona.card,
                    setting = card,
                    introLine = intro,
                    createdAtSec = now.epochSecond,
                ),
            )
            repo.saveState(repo.state().copy(lastSettledDay = day - 1, linebookAiDone = true))
            repo.insertEvent(
                RoommateEventEntity(
                    dayKey = day,
                    atSec = now.epochSecond,
                    kind = RM_KIND_NOTE,
                    body = "今天你们认识了。它记住了你叫它「$clean」。$intro",
                ),
            )
        }
        repo.saveLinebook(book)
        return CreatedRoommate(persona, intro)
    }

    /**
     * 改名片：连住进来的日子都不动，所以它送东西的节奏不会因为改名而重来一遍。
     * 旧的小本子保持原样——那是已经发生过的事，不跟人设一起重写。
     */
    suspend fun update(
        name: String,
        nature: String,
        pace: String,
        face: String,
        setting: String,
        now: Instant,
        onStage: (RoommateSetupStage) -> Unit = {},
    ): CreatedRoommate? {
        val existing = repo.profile() ?: return null
        val clean = nameOf(name)
        val persona = Persona(clean, nature, pace, face)
        val card = settingOf(setting)
        val (intro, book) = ask(persona, card, now, onStage)
        repo.runTransaction {
            repo.saveProfile(
                existing.copy(
                    name = clean,
                    nature = nature,
                    pace = pace,
                    face = face,
                    persona = persona.card,
                    setting = card,
                    introLine = intro,
                ),
            )
            repo.saveState(repo.state().copy(linebookAiDone = true))
        }
        repo.saveLinebook(book)
        return CreatedRoommate(persona, intro)
    }

    /** 两样东西按顺序向模型要，阶段先报给界面：等待要看得见，才知道它没卡住。 */
    private suspend fun ask(
        persona: Persona,
        setting: String,
        now: Instant,
        onStage: (RoommateSetupStage) -> Unit,
    ): Pair<String, Map<RoomAction, List<String>>> {
        val context = EpisodeContext(
            name = persona.name,
            persona = persona.card,
            setting = setting,
            recent = emptyList(),
            habits = emptyList(),
            openLoop = null,
            assetTags = repo.assets().map { it.tag }.distinct(),
        )
        onStage(RoommateSetupStage.FIRST_LINE)
        val intro = generator.intro(context) ?: RoommateText.introLine(dayOf(now))
        onStage(RoommateSetupStage.LINEBOOK)
        return intro to (generator.linebook(context) ?: RoommateText.linebook())
    }

    private fun nameOf(name: String): String = name.trim().take(MAX_NAME_CHARS).ifBlank { "小室友" }

    private fun settingOf(setting: String): String = setting.trim().take(MAX_SETTING_CHARS)

    private fun dayOf(now: Instant): Long =
        now.atZone(zone()).toLocalDate().toEpochDay()

    private companion object {
        const val MAX_NAME_CHARS = 8
        const val MAX_SETTING_CHARS = 80
    }
}
