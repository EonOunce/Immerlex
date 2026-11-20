# 0037 按今天的词数分段、认出按题型加权、生词先照面、连连看

日期：2026-11-20
状态：已实现，待真机验收（versionName 4.4.0 / versionCode 31，尚未构建发布）

## 背景

用户真机用了一阵后一次提了八条，讨论后批准其中五条这一刀做完（3 查词语境 / 5 快速模式 / 7 干扰项轮换 / 8 易错词词性标记明确留到以后）：

1. **一组多少题不写死**（原话："我希望测验、复习的时候不要是固定的 40 词或者自己规定的 xx 词，而是根据今天的总词数 / 希望的进行轮数得到一个每轮多少词"）。讨论后用户在"收法一（每次固定比例）"与"收法二（每天先选分几段，段长 = 今天词数 ÷ 段数）"之间选了**收法二**，并澄清「段」不是「轮」——今天 120 词分 3 段，每段 40，不是说每个词要考 3 遍（考几遍由认出闸门管）。
2. **认出闸门按题型加权**：用户定"句子挖空选词选对了，算作 2 次；挖空和看英文释义选单词都记成 2 分"。
3. **英文释义选词的题干里，不同义项要回车隔开**。
4. **生词也能进今日测验**（原话："现在没有阅读过的单词是没办法进入测验复习吗"），第一次不管对错都只算"打过照面"。
5. **测验里点题目那个词，一键查词典**。
6. **测验时底栏会把人带走**，干脆藏起来。
7. **连连看题型**。

## 决定

### 1. 分段（`core.quiz.QuizRound`，取代 `EarlyReview`）

- 段数 2 / 3 / 4 三档，默认 3，存 `AppPreferences.quizSegments`（SharedPreferences 键 `quiz_segments`，坏值退回 3）。设置页「测验 → 每天分几段」三选一、改完立即生效。
- 段长 = `perSegment(total, segments)` = 今天词数 ÷ 段数，**向上取整**；词数为 0 时是 0。
- 被除数是 `QuizService.todayTotal()` = **原始**到期队列的 `reviewWords + newWords`（按 id 去重，数词不数题）。用原始队列而不是"今天还剩多少"：答完一轮后今天的词不再压人，若拿剩余数当被除数，段长会越除越小。
- 所有入口同一把尺：`planDue / planReview / planEarly / planTricky / planNemesis / planMatch` 都按它切一段；今日页入口上的数字用同一除法（`HomeViewModel.quizCount`），显示的就是这一组能测多少。
- 休息日（今天一个到期词都没有）除不出段长时退回入口自己的候选池（`perSegmentOr(total, poolSize, segments)`），否则提前复习、易错练习会永远显示 0 个而进不去。
- 旧的「提前复习一组 5 / 10 / 20」连同 `EarlyReview.kt`、`earlyGroupSize`、`early_group_size` 键一并删除，旧值不迁移——它管的只是"一组几个"，这一刀之后所有入口都由段长决定。`quiz_session.mode` 是 TEXT，**没有 Room 迁移**。

### 2. 认出闸门加权（`core.schedule.RecognitionGate`）

- `pointsFor(questionType)`：`"CLOZE"`、`"DEFINITION"` 一次 **2 分**，其余（含 `"MATCH"`、null、认不出的题型）**1 分**。
- `GOOD_POINTS = 3`：`ScheduleService.recognize()` 从记录里数**末尾连续**的 attempt 分数和，攒满 3 折算成一次 `recall` GOOD；答错当场 `recall` AGAIN 并清零，行为与 v3.7 一致，只是"次数"变成"分数"。
- 分从 `review_logs.questionType` 已有的字符串算，**升级前的 attempt 自动按新权重重算**，不改表、不回填。
- `TrickyRetirement` 沿用同一把尺：移出易错的闸门从"连着认出 6 次"变成"攒够 `RETIRE_POINTS = 6` 分"，重题型更快够格。

### 3. 生词先照面（功能 1）

- `QuizService.dueCandidateIds` 加入 `queue.newWords`——没读过课文的人也能开测。
- `QuizService.record()` 先试 `schedule.learn(rating = 答对 ? GOOD : AGAIN, source = quiz)`；**返回非空就到此为止**：只写一条 learn，不算回忆、不往闸门里攒分。返回 null（这个词记过账了）才走 `recognize()`。
- 后果：生词第一次作答不管对错都只开始排期；它今天算"碰过"（learn 不进 `handledWordIds`，但后续测验候选的口径用 `learnedWordIdsSince` 排掉了它，且它进今日复习候选）。从第二次作答起才进认出闸门。
- 连看也走同一条：`recordMatch` 对生词先 learn，老词才 `recognize(MATCH)`。

### 4. 测验里点词查词典（功能 2）

- 只对 **英→中** 且**已作答**的题目开放：`QuizQuestionBody` 的题干词可点，`QuizViewModel.lookupWord()` 查 `DictionarySource`，弹 `app/ui/DictionaryCard.kt` 的 `DictionaryCardDialog`（与词库页同一张卡，`onAdd = null` 时不摆「加入词库」——测验里只查，不动词库），素材随卡 `material.peek()` 现读。
- 先答再查：四选一没答就查等于把答案递到眼前。

### 5. 测验时藏底栏（功能 4）

- `ImmerlexRoot`：`showBottomBar = currentRoute != ROUTE_QUIZ && currentRoute != ROUTE_MATCH`。测验与连连看都是一段要连着做完的事，离开走各自的返回键。

### 6. 连连看（功能 6，`QuizMode.MATCH`）

- **是一个新的 `QuizMode`，不是新的 `QuizType`**：不建排期、不进题型门禁、不进 `QuizPolicy` 统计；记账时 `questionType = "MATCH"`（1 分）。
- 候选与「今日测验」同一批（`dueCandidateIds`，今天还没碰过的）；`MatchBoards` 每屏最多 `BOARD_SIZE = 5` 对、最少 `MIN_PAIRS = 2` 对才开局，偶数均分屏，左右两列各自用 `stableHash` 独立排序，同一个词不会两列都出现同一张牌。
- **一屏连完就翻下一屏**（`boardCleared(board, matched)`：这一屏自己的每一对都连上；不是整组连完才翻），翻之前留 `BOARD_PAUSE_MS = 650ms` 让最后一对露一下。第一版用 `matched.size >= totalPairs`（整组总数）判翻屏，导致第一屏永远不翻——这是验收时抓到的真 bug，纯函数单测锁住。
- 连错的两张牌红 `WRONG_FLASH_MS = 420ms`，**配错本身不写任何记录**（错的是那一次配对，不是这两个词里任何一个）；配错过的词这一组之后再配也不计分。
- 只有**第一下就连对**才写一笔（`recordMatch(firstTry = true)`）；最后一对不用认也连得上，那种"对上了"说明不了记得。记账走 learn（生词）/ recognize（老词，1 分）。
- 结果页：主色大卡「一次就连对 N / M 对」+ 巅峰涨跌/破纪录小字 + 没一下连对的词墙 +「再连一组」「完成」；`quiz_runs` 留一行 `MATCH asked=对数 correct=第一下就对 wrong=其余 bestStreak=0`。
- **不存续做**：退出这一组就散了，下次重新摆牌（它本来就是一气呵成玩完的东西，`quiz_session` 不为它留行）。
- 今日页入口在「今日测验 / 今日复习」两扇门下面：「连连看 · N 对词」，凑不满 2 对整格不出现。

### 7. 英文释义换行（`definitionLines`）

`DefinitionBuilder` 的题干按 `\n`、`；`、`;` 拆开、逐条 trim、去掉空条、用换行重新拼——ECDICT 的释义是一长串用 `；` 连起来的义项，整段塞进题干会挤成一堆。

## 后果

- **`SESSION_LIMIT = 40` 与 20 题上限彻底不存在了**（`QuizService` 里 grep 不到），所有入口的组大小都由段长决定；`QuizServiceTest` 相应改写，`EarlyReview.kt` / `EarlyReviewTest.kt` 删除，`QuizRoundTest` / `RecognitionGateTest` 新增。
- 曾经在设置里选过 10 / 20 的机器，升级后回到默认 3 段——那个设置本来只描述"提前复习一组几个"，现在这件事由段数统一管。
- 闸门加权之后，句子挖空与释义选词两道题就能毕业一个词（2 + 1 或 2 + 2），英→中要三次；这是用户要的"难题型更值钱"。
- 生词的第一条记录是 learn（rating 按当次对错），所以"第一次答错"不会记成一次失败回忆、也不清零任何东西；从第二次起才按闸门算。今日页入口数字与今日复习随之变化，两扇门的定义不变。
- **没有 Room 迁移**（`questionType`、`quiz_runs.mode` 都是既有 TEXT 列，段数在 SharedPreferences），也没有为新设置进 `SyncFingerprint`。
- 用户明确**不在这一刀**的：3 语境查词（`D:\Installation\App\词拾` 那套）、5 快速模式、7 干扰项轮换、8 易错词词性标记。

## 验证

- **474 项 JVM 单测 0 失败**（app 473 + FSRS 参考向量 1），`:app:lintDebug` 通过，只剩那两条既有图标长路径告警、0 error。
- `emulator-5554` 实走（`scratch/v44/`）：
  - 设置页「测验」三档 2 / 3 / 4 段渲染正确；切 2 段 `quiz_segments=2` 落盘，今日页连连看从 4 对当场变 6 对；切回 3 段落盘。
  - 加权闸门：五个词（static / brittle / corridor / meticulous / mist）先各攒 `attempt MATCH`(1) + `attempt EN_ZH`(1)，第二遍「今日复习」各答对一道 1 分题后，库里各出现一条 `recall rating=3`，**今日复习从 6 词落到 1 词**（只剩 harbour，它在此之前答错过一次、还差一分）。
  - 连连看：翻屏 bug 修后第 2/2 屏正常摆出；配错四张牌一起红一下、进度不动；结果页「一次就连对 4 / 6 对」、词墙 `ephemeral lantern`；`quiz_runs` 落 `MATCH|6|4|2|0|0|…`；配错过的词组内不再计分、库里**一条记录都没有**，第二次进连连看时它们仍在候选里。
  - 关于页「关于测验与计分」渲染段数、3 分制、连连看三段新文案。
