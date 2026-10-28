package com.immerlex.app

import android.app.Application
import android.content.Context
import com.immerlex.core.ai.AiConfig
import com.immerlex.core.ai.ApiKeyStore
import com.immerlex.core.ai.BibleExpander
import com.immerlex.core.ai.ChapterGenerator
import com.immerlex.core.ai.OpenAiCompatClient
import com.immerlex.core.bible.StoryBibleRepository
import com.immerlex.core.db.ImmerlexDatabase
import com.immerlex.core.dict.EcdictDictionary
import com.immerlex.core.fsrs.Scheduler
import com.immerlex.core.importer.ImportRepository
import com.immerlex.core.ocr.MlKitOcr
import com.immerlex.core.ocr.OcrEngine
import com.immerlex.core.schedule.ScheduleService
import com.immerlex.core.settings.AppPreferences

class ImmerlexApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val db: ImmerlexDatabase by lazy { ImmerlexDatabase.create(context) }
    val dictionary: EcdictDictionary by lazy { EcdictDictionary.openFromAssets(context) }
    val prefs: AppPreferences by lazy { AppPreferences(context) }
    val apiKeyStore: ApiKeyStore by lazy { ApiKeyStore(context) }
    val ocr: OcrEngine by lazy { MlKitOcr() }

    // App-wide FSRS config: no learning steps (words graduate per review),
    // fuzzing off for deterministic scheduling, 10-year cap.
    val scheduler: Scheduler by lazy {
        Scheduler(
            learningSteps = emptyList(),
            relearningSteps = emptyList(),
            maximumInterval = 3650,
            enableFuzzing = false,
        )
    }
    val importRepository: ImportRepository by lazy { ImportRepository(db.wordDao(), dictionary) }
    val scheduleService: ScheduleService by lazy {
        ScheduleService(db.wordDao(), db.reviewLogDao(), scheduler)
    }
    val storyBible: StoryBibleRepository by lazy {
        StoryBibleRepository(db.storyBibleDao(), db.bibleEntryDao(), db.characterStateDao(), db.chapterDao())
    }
    val chapterGenerator: ChapterGenerator by lazy {
        ChapterGenerator(
            queue = scheduleService,
            chapterDao = db.chapterDao(),
            story = storyBible,
            wordDao = db.wordDao(),
            clientFor = { OpenAiCompatClient(aiConfig()) },
        )
    }
    val bibleExpander: BibleExpander by lazy { BibleExpander(clientFor = { OpenAiCompatClient(aiConfig()) }) }

    fun aiConfig(): AiConfig = AiConfig(
        baseUrl = prefs.baseUrl,
        apiKey = apiKeyStore.key(),
        model = prefs.model,
    )
}
