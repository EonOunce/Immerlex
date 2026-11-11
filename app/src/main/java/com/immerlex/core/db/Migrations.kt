package com.immerlex.core.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: `review_logs` gains the pre-rating card snapshot that makes
 * "amend a rating" possible without inflating the review count
 * (docs/adr/0004-chapter-word-units.md). Rows that already exist get the
 * [PRE_DUE_LEGACY] sentinel because their pre-rating state is unrecoverable.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE review_logs ADD COLUMN preStability REAL")
        db.execSQL("ALTER TABLE review_logs ADD COLUMN preDifficulty REAL")
        db.execSQL("ALTER TABLE review_logs ADD COLUMN preState INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE review_logs ADD COLUMN preDueEpochSec INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE review_logs ADD COLUMN preLastReviewEpochSec INTEGER")
    }
}

/**
 * v2 -> v3: the story bible stops being a single overwritten `synopsis` string.
 * `bible_entries` holds the setting lines the user can edit, `character_states` holds one row per
 * person with their latest known situation, and `story_bibles.seedText` keeps the original
 * one-paragraph idea so the expansion can be re-rolled (docs/adr/0005-story-bible-from-one-prompt.md).
 * The old `charactersJson` / `synopsis` columns stay — dropping a column is not reliable on
 * minSdk 26 — and [com.immerlex.core.bible.StoryBibleRepository] empties them once they are adopted.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bible_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                "NOT NULL, `genre` TEXT NOT NULL, `section` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                "`ordinal` INTEGER NOT NULL, `authoredByUser` INTEGER NOT NULL, `updatedAtSec` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `character_states` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                "NOT NULL, `genre` TEXT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, `firstChapterNumber` INTEGER NOT NULL, `lastChapterNumber` INTEGER NOT NULL, " +
                "`authoredByUser` INTEGER NOT NULL, `conflictChapterNumber` INTEGER NOT NULL, " +
                "`conflictText` TEXT NOT NULL, `updatedAtSec` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_character_states_genre_name` " +
                "ON `character_states` (`genre`, `name`)",
        )
        db.execSQL("ALTER TABLE `story_bibles` ADD COLUMN `seedText` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v3 -> v4: the daily loop needs two facts that cannot be re-derived later — whether a given day's
 * due queue ended up empty, and whether that day spent the week's single makeup credit.
 * "Did the user check in that day" is deliberately not stored; it already lives in `review_logs`.
 * `backup_runs` records each upload attempt so the settings page can show a truthful last result.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_loop` (`epochDay` INTEGER NOT NULL PRIMARY KEY, " +
                "`dueCleared` INTEGER NOT NULL, `makeupConsumed` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `backup_runs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                "NOT NULL, `atSec` INTEGER NOT NULL, `ok` INTEGER NOT NULL, `remoteName` TEXT NOT NULL, " +
                "`bytes` INTEGER NOT NULL, `detail` TEXT NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_runs_atSec` ON `backup_runs` (`atSec`)")
    }
}

/**
 * v4 -> v5: v1.5 的书架与短篇。`chapters.kind` 区分连载章节（老行全是它）与单篇短篇；
 * `reading_positions` 每个书架条目一行，记住读到哪章，"上次在看的东西"取其中最新的一行。
 * 老章节靠列默认值直接归为 serial，不重建表、不动任何正文。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `chapters` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'serial'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reading_positions` (`key` TEXT NOT NULL, " +
                "`chapterId` INTEGER NOT NULL, `updatedAtSec` INTEGER NOT NULL, PRIMARY KEY(`key`))",
        )
    }
}

/**
 * v5 -> v6: 阅读页的易错词。`tricky_words` 不挂词库外键（没入库的词也能标），
 * `tricky_links` 只存有序对，双向关系靠查询时两边都看，天然不会出现单向行。
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tricky_words` (`word` TEXT NOT NULL, " +
                "`gloss` TEXT NOT NULL, `ipa` TEXT NOT NULL, `createdAtSec` INTEGER NOT NULL, " +
                "PRIMARY KEY(`word`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tricky_links` (`wordA` TEXT NOT NULL, " +
                "`wordB` TEXT NOT NULL, `createdAtSec` INTEGER NOT NULL, " +
                "PRIMARY KEY(`wordA`, `wordB`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tricky_links_wordB` ON `tricky_links` (`wordB`)")
    }
}

/**
 * v6 -> v7: 每行复习记录区分"学"与"回忆"，并记住测验题型（docs/adr/0012）。
 * 老行全部是"先回忆再反馈"的复习，回填 recall；题型当时没记，保持 NULL。
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'recall'")
        db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `questionType` TEXT")
    }
}

/**
 * v7 -> v8: 短篇不再只有三个预设腔调。`toneText` 存用户自己写的腔调（空 = 用 `genre`
 * 里那个预设名），`idea` 存"这篇想写什么"。两句都跟着这一篇落库，重写时原样喂回模型。
 * 连载章节用不到，保持空串。
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `chapters` ADD COLUMN `toneText` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `chapters` ADD COLUMN `idea` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v8 -> v9: 室友。全部是新表，不动任何老列——小说、复习、词库原样保留。
 * `rm_ledger.dedupeKey` 唯一索引就是"同一个词同一天只发一次"的硬约束；
 * `rm_evidence` 的复合主键保证一个习惯一天最多一条证据。
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rm_profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`nature` TEXT NOT NULL, `pace` TEXT NOT NULL, `face` TEXT NOT NULL, " +
                "`persona` TEXT NOT NULL, `createdAtSec` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rm_assets` (`assetId` TEXT NOT NULL, `fileName` TEXT NOT NULL, " +
                "`tag` TEXT NOT NULL, `bytes` INTEGER NOT NULL, `importedAtSec` INTEGER NOT NULL, " +
                "PRIMARY KEY(`assetId`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rm_assets_tag` ON `rm_assets` (`tag`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rm_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`dayKey` INTEGER NOT NULL, `atSec` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, `assetId` TEXT, `choiceA` TEXT NOT NULL, `choiceB` TEXT NOT NULL, " +
                "`chosen` INTEGER NOT NULL, `openLoop` INTEGER NOT NULL, `sourceId` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rm_events_dayKey` ON `rm_events` (`dayKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rm_events_atSec` ON `rm_events` (`atSec`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rm_ledger` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`atSec` INTEGER NOT NULL, `dayKey` INTEGER NOT NULL, `amount` INTEGER NOT NULL, " +
                "`reason` TEXT NOT NULL, `dedupeKey` TEXT NOT NULL, `note` TEXT NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rm_ledger_dedupeKey` ON `rm_ledger` (`dedupeKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rm_ledger_dayKey` ON `rm_ledger` (`dayKey`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rm_habits` (`habitId` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                "`learnedDay` INTEGER NOT NULL, `learnedAtSec` INTEGER NOT NULL, PRIMARY KEY(`habitId`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rm_evidence` (`habitId` TEXT NOT NULL, `dayKey` INTEGER NOT NULL, " +
                "`eventId` INTEGER NOT NULL, `atSec` INTEGER NOT NULL, PRIMARY KEY(`habitId`, `dayKey`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rm_inventory` (`itemId` TEXT NOT NULL, " +
                "`acquiredAtSec` INTEGER NOT NULL, `used` INTEGER NOT NULL, PRIMARY KEY(`itemId`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rm_works` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `body` TEXT NOT NULL, `price` INTEGER NOT NULL, " +
                "`createdAtSec` INTEGER NOT NULL, `purchased` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rm_state` (`id` INTEGER NOT NULL, `lastSettledDay` INTEGER NOT NULL, " +
                "`lastEpisodeDay` INTEGER NOT NULL, `lastAiDay` INTEGER NOT NULL, " +
                "`lastVisitSec` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

/**
 * v9 -> v10: 室友开始过日子。台词本、每日内容包、铺子里的商品、它送的礼物各占一张新表；
 * `rm_state` 只多一个"台词本已经问过模型"的标记。老账本、老事件、老素材原样保留。
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rm_lines` (`action` TEXT NOT NULL, `ordinal` INTEGER NOT NULL, " +
                "`line` TEXT NOT NULL, PRIMARY KEY(`action`, `ordinal`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rm_daily` (`dayKey` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
                "`coquetry` TEXT NOT NULL, `petGiftName` TEXT NOT NULL, `petGiftStory` TEXT NOT NULL, " +
                "`petGiftLine` TEXT NOT NULL, `coquetryUsed` INTEGER NOT NULL DEFAULT 0, " +
                "`createdAtSec` INTEGER NOT NULL, PRIMARY KEY(`dayKey`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rm_offers` (`offerId` TEXT NOT NULL, `dayKey` INTEGER NOT NULL, " +
                "`channel` TEXT NOT NULL, `name` TEXT NOT NULL, `story` TEXT NOT NULL, " +
                "`reaction` TEXT NOT NULL, `followUp` TEXT NOT NULL, `price` INTEGER NOT NULL, " +
                "`purchased` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, `requires` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, `createdAtSec` INTEGER NOT NULL, PRIMARY KEY(`offerId`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rm_offers_dayKey` ON `rm_offers` (`dayKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rm_offers_channel` ON `rm_offers` (`channel`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `rm_pet_gifts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`dayKey` INTEGER NOT NULL, `atSec` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`story` TEXT NOT NULL, `line` TEXT NOT NULL, `cause` TEXT NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rm_pet_gifts_dayKey` ON `rm_pet_gifts` (`dayKey`)")
        db.execSQL("ALTER TABLE `rm_state` ADD COLUMN `linebookAiDone` INTEGER NOT NULL DEFAULT 0")
        // 固定商品目录被每日小铺取代；旧购买记录仍在 rm_events 里，库存表不再有含义。
        db.execSQL("DROP TABLE IF EXISTS `rm_inventory`")
    }
}

/**
 * v10 -> v11: 室友建档从"先建再偷偷补"变成"一次定好"。
 * `setting` 存用户多留的一句话，`introLine` 存它当时说的第一句；老室友两列都是空串，
 * 不补演、不覆盖已经长出来的性格。
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `rm_profile` ADD COLUMN `setting` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `rm_profile` ADD COLUMN `introLine` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v11 -> v12: 测验素材落库。一张新表，别的都不动——一个词的英文释义、例句和易混候选池
 * 只在第一次考它时向模型要一回，之后出题读这一行。
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quiz_material` (`wordId` INTEGER NOT NULL, " +
                "`definition` TEXT NOT NULL DEFAULT '', `example` TEXT NOT NULL DEFAULT '', " +
                "`distractors` TEXT NOT NULL DEFAULT '[]', `createdAtSec` INTEGER NOT NULL, " +
                "PRIMARY KEY(`wordId`))",
        )
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quiz_session` (`mode` TEXT NOT NULL, " +
                "`payload` TEXT NOT NULL, PRIMARY KEY(`mode`))",
        )
    }
}

/**
 * v13 -> v14: 多设备手动同步要判断"把云端那份拉下来会不会丢掉本机刚写的记录"，
 * 这条判断的依据必须跟着库走，所以新增一行 `sync_lineage`。老用户那行是空的，
 * 第一次下载前会因此问一句——判定不了的时候只能问人，不能猜。
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_lineage` (`id` INTEGER NOT NULL DEFAULT 1, " +
                "`device` TEXT NOT NULL DEFAULT '', `syncedName` TEXT NOT NULL DEFAULT '', " +
                "`syncedWords` INTEGER NOT NULL DEFAULT 0, `syncedChapters` INTEGER NOT NULL DEFAULT 0, " +
                "`syncedReviews` INTEGER NOT NULL DEFAULT 0, `syncedLastReviewId` INTEGER NOT NULL DEFAULT 0, " +
                "`lastUploadName` TEXT NOT NULL DEFAULT '', `lastUploadSec` INTEGER NOT NULL DEFAULT 0, " +
                "`lastRestoreName` TEXT NOT NULL DEFAULT '', `lastRestoreSec` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`id`))",
        )
    }
}

/**
 * v14 -> v15: 出题素材改按词面存。主键从词库 id 换成小写单词，素材就跟着"这个词"走，
 * 不再跟着"这条词库记录"走——删了再加回来的词还认得自己的素材，素材包里那些根本没进
 * 词库的考研词也才有地方放。顺手加一列 `exampleZh`（例句的中文对照）。
 *
 * 老行靠 `words` 反查词面，所以**删过词留下的孤儿行会在这里丢掉**：id 已经没了，
 * 谁也想不出那个单词长什么样。
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quiz_material_new` (`word` TEXT NOT NULL, " +
                "`definition` TEXT NOT NULL DEFAULT '', `example` TEXT NOT NULL DEFAULT '', " +
                "`exampleZh` TEXT NOT NULL DEFAULT '', `distractors` TEXT NOT NULL DEFAULT '[]', " +
                "`updatedAtSec` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`word`))",
        )
        // 词库里同一个词大小写写法不同的两条会撞成一行：留 id 更小的那条，另一条的素材丢掉。
        db.execSQL(
            "INSERT OR IGNORE INTO `quiz_material_new` (`word`, `definition`, `example`, `exampleZh`, " +
                "`distractors`, `updatedAtSec`) " +
                "SELECT lower(trim(w.`word`)), m.`definition`, m.`example`, '', m.`distractors`, " +
                "m.`createdAtSec` FROM `quiz_material` m JOIN `words` w ON w.`id` = m.`wordId` " +
                "ORDER BY m.`wordId`",
        )
        db.execSQL("DROP TABLE `quiz_material`")
        db.execSQL("ALTER TABLE `quiz_material_new` RENAME TO `quiz_material`")
    }
}

/** v15 -> v16: 素材多一列搭配短语。老行留空串，搭配题对它们不可用——不编，也不拿别的词的搭配充数。 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `quiz_material` ADD COLUMN `collocations` TEXT NOT NULL DEFAULT '[]'")
    }
}

/**
 * v16 -> v17: 同步指纹从"词 / 章 / 复习记录"三个数扩到所有会丢的东西（素材、易错、故事档案、室友）。
 * 老行这一列留空串，与任何真实指纹都不相等——升级后第一次下载会多问一句，宁可问，不能猜。
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sync_lineage` ADD COLUMN `syncedExtras` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v17 -> v18：两张只装"过后算不回来"的东西。
 *
 * `quiz_runs` 留做完的每一组题的成绩——会话表在结束时整行删掉，之前"最近测了什么、
 * 各组对了几个"没有任何地方能查，破纪录也就无从比。
 * `recall_snapshots` 每天留一个"此刻大概能想起几个词"的影子：这个数算的是卡片当下的
 * 回忆把握，明天再也回推不出今天的样子。
 *
 * 巅峰分、段位、宿敌都不建表——它们全部能从 `review_logs` 现推，写进库里就会有两套说法。
 * 老库这两张表是空的，战绩页第一次打开只会显示"还没有记录"，不编一个数凑数。
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quiz_runs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`mode` TEXT NOT NULL, `finishedAtSec` INTEGER NOT NULL, `asked` INTEGER NOT NULL, " +
                "`correct` INTEGER NOT NULL, `wrong` INTEGER NOT NULL, `bestStreak` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_runs_finishedAtSec` ON `quiz_runs` (`finishedAtSec`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recall_snapshots` (`epochDay` INTEGER NOT NULL, " +
                "`canRecall` INTEGER NOT NULL, `mastered` INTEGER NOT NULL, `atSec` INTEGER NOT NULL, " +
                "PRIMARY KEY(`epochDay`))",
        )
    }
}

/**
 * v18 -> v19：给做完的每一组补一列"这一组让巅峰分动了多少"。
 *
 * 巅峰分本身不用留：拿 `review_logs` 重放一遍就回来了。但**一组自己的功过**回不来——
 * 它要的是"这一组写下的那几笔记在前面、其余照旧"两次相减，而同一天里再去阅读页记几笔，
 * 事后重放就分不清哪一笔属于哪一组了。所以当时算出来就落在这一行上。
 *
 * 可空：老行没算过，界面那行干脆不说涨跌，不拿 0 冒充"这一组不痛不痒"。
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `quiz_runs` ADD COLUMN `peakChange` INTEGER")
    }
}

val MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
    MIGRATION_16_17,
    MIGRATION_17_18,
    MIGRATION_18_19,
)
