package com.immerlex.app

import android.app.Application
import android.content.Context
import com.immerlex.core.ai.AiConfig
import com.immerlex.core.ai.ApiKeyStore
import com.immerlex.core.ai.BibleExpander
import com.immerlex.core.ai.ChapterGenerator
import com.immerlex.core.ai.OpenAiCompatClient
import com.immerlex.core.backup.BackupOutcome
import com.immerlex.core.backup.BackupHost
import com.immerlex.core.backup.BackupScheduler
import com.immerlex.core.backup.BackupService
import com.immerlex.core.bible.StoryBibleRepository
import com.immerlex.core.db.ImmerlexDatabase
import com.immerlex.core.dict.EcdictDictionary
import com.immerlex.core.fsrs.Scheduler
import com.immerlex.core.importer.ImportRepository
import com.immerlex.core.notify.GenerationNotifier
import com.immerlex.core.notify.Notifier
import com.immerlex.core.notify.ReminderControl
import com.immerlex.core.notify.ReminderHost
import com.immerlex.core.notify.ReminderScheduler
import com.immerlex.core.ocr.MlKitOcr
import com.immerlex.core.ocr.OcrEngine
import com.immerlex.core.secret.SecretStore
import com.immerlex.core.schedule.DailyLoopService
import com.immerlex.core.schedule.ScheduleService
import com.immerlex.core.settings.AppPreferences
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ImmerlexApplication : Application(), ReminderHost, BackupHost {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.ensureChannels(this)
        // 每次打开应用都重新排到今天（或明天）的设定时刻：任务被系统清掉过也能自己补回来。
        if (container.prefs.reminderEnabled) {
            ReminderScheduler.scheduleNext(this, container.prefs.reminderMinuteOfDay)
        }
        BackupScheduler.sync(this, container.prefs.backupEnabled)
    }

    override val reminderEnabled: Boolean get() = container.prefs.reminderEnabled
    override val reminderMinuteOfDay: Int get() = container.prefs.reminderMinuteOfDay
    override var lastRemindedEpochDay: Long
        get() = container.prefs.lastRemindedEpochDay
        set(value) {
            container.prefs.lastRemindedEpochDay = value
        }

    override suspend fun todayDueCount(now: Instant): Int =
        container.scheduleService.todayDueCount(now, container.prefs.dailyNewLimit)

    override val backupEnabled: Boolean get() = container.prefs.backupEnabled

    override suspend fun runBackup(now: Instant): BackupOutcome = container.backupService.run(now)
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
    val dailyLoopService: DailyLoopService by lazy {
        DailyLoopService(db.dailyLoopDao(), db.reviewLogDao())
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

    // 写作通知要活过页面切换：ViewModel 清掉时它还得把"进行中"那条改写成结果，所以挂在容器自己的 scope 上。
    private val notifyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val generationNotifier: GenerationNotifier by lazy { GenerationNotifier(context, notifyScope) }
    val reminder: ReminderControl by lazy { ReminderControl(context, prefs) }

    val webDavPassword: SecretStore by lazy { SecretStore(context, "webdav_password") }
    val backupService: BackupService by lazy {
        BackupService(context, db, prefs, webDavPassword, db.backupRunDao())
    }

    fun aiConfig(): AiConfig = AiConfig(
        baseUrl = prefs.baseUrl,
        apiKey = apiKeyStore.key(),
        model = prefs.model,
    )
}
