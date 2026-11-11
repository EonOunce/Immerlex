# 0028 四选一只算"认出来"：连对三次记一次 GOOD（v3.7）

## 背景

用户 2026-11-09 真机用完报告：FSRS 给的间隔普遍太长，到期时其实已经想不起来了。
根因不在算法参数，在**记账口径**——测验是四选一，"认出来"比"凭空想起来"容易得多，
可 v1.3 起一次答对就写一次 `recall GOOD`，等于把"认出来"的成绩记成了"想起来"的成绩，
稳定度（stability）被一口一口喂大。用户自己的观察也对上了：

> 「我发现还是可以连续3次才算good，但是从每天的测验里点进去还是只考两次……
> 有没有办法能在不增加测验负担的前提下，还能每天仅靠非阅读的方式完成当日练习呢？」
> ——「今晚就改，我信你」

要动的东西很敏感：所有排期都从 `review_logs` 与 FSRS 卡片长出来，改错一处就是把用户的
复习日历打乱。所以定了三条硬约束——**不新增表、不加 Room 迁移、不改 FSRS 一个参数**。

## 决定

1. **一种新的记账事件：`kind = 'attempt'`（认出来，还没攒成一次回忆）**
   （`core/db/Entities.kt` 的 `LOG_KIND_ATTEMPT`，`core/schedule/ScheduleService.kt` 的 `recordAttempt`）。
   `review_logs.kind` 本来就是一列 TEXT，`attempt` 只是它多出来的一个取值，所以不用迁移。
   这条日志：
   - **不碰卡片**：不改 stability / difficulty / due / reps，也不进 `lapses`；
   - **不可撤销**：写的时候 `preDueEpochSec = PRE_DUE_LEGACY`，`hasPreRatingSnapshot` 为假，
     `undo()` 会跳过它（撤销只撤真正改写卡片的那一条）；
   - **自动被所有 `kind = 'recall'` 的查询看不见**：题型门禁（`QuizPolicy` 读历史成功率）、
     阅读页三色、分享卡的「今日回忆」、素材账都不用改一行——它们本来就只认 `recall`；
   - **照样点亮热力图与打卡**：那两处走 `reviewsSince`，不按 kind 过滤，"今天碰过这个词"是真的。
2. **闸门只加在"认出来"这一条路上**：`ScheduleService.recognize(word, correct, …)`（只有测验调它）
   - 答对：往后数这一串连续的 `attempt`，`已有 + 1 >= GOOD_STREAK(3)` 才写一次 `recall GOOD`，
     否则只落一条 `attempt`；写了 GOOD 之后尾巴上不再留 `attempt`，计数自然从头起算；
   - 答错：**立刻**一次 `recall AGAIN` 并清零——坏消息不推迟，推迟才是危险的；
   - 阅读页不进这个闸门（`ReaderGrading` 调的是 `schedule.recall`）：判断发生在点开卡片之前，
     本来就是一次真回忆，「想起来了」一次算一次。
   - 跨入口累计：`attempt` 是按词往后数的，不分组、不分日，所以"测验里对两次 + 提前复习里对一次"
     也攒成一次 GOOD（`ScheduleServiceTest.theStreakAccumulatesAcrossDifferentEntries`）。
   - `MIN_RECALL_GAP_SECONDS` 那条防连点的闸门原样保留，位置在 `recognize` 开头。
3. **易错词的安全项两条**
   - **干扰项取整个连通分量**（`QuizService.confusablesFor`）：以前只拿与这个词**直接挂线**的词，
     于是 a–b、b–c 的组里，出 a 的题只会拿 b 当干扰项。现在按广度优先把整组捞出来，
     **直接相连的排前面**，整组封顶 `MAX_CONFUSABLES = 4` 个。管理页说的"组就是连通分量"，
     出题这边终于按同一件事理解。
   - **连对 6 次问一句要不要移出易错**（纯函数 `core/tricky/TrickyRetirement.kt`，
     `STREAK = 6`；`QuizService.retirementCandidate`）：只数**标记之后**的记录，碰到一次 `AGAIN`
     就从头算；`attempt` 与 `recall` 都算一次答对。测验答对后由 `QuizViewModel` 查一次，
     有结果就在这一题下面挂一个「移出易错」按钮，与答错时那个「标为易错」对称。
     点了走 `TrickyRepository.deleteWord()`——只清易错两张表，**复习记录一条不动**；不点就一切照旧。

4. **第三条测验入口：`QuizMode.TRICKY`（易错练习）**
   用户那句话的后半段——"每天仅靠非阅读的方式完成当日练习"——到期词测完就没有别的口子了。
   于是加第三种入口：候选 = **标过易错且真的在词库里的词**，按 `lapses` 多到少、其次按到期日排，
   今天已经 `recall` 过的不再重复考，一组仍封顶 `SESSION_LIMIT`（`QuizService.trickyCandidates` /
   `planTricky`）。词面两边都过 `core.word.wordSurface` 才对得上：易错表存小写词面，词库里的词可能大写开头。
   今日页不另开一张卡——它挂在「今日测验」那张白卡下面一行小字「易错练习 N 词」，N 为 0 时整行不出现。
   `quiz_session` 的主键本来就是 `mode` 这一列 TEXT，所以第三份存档与 DUE / EARLY 各自独立，
   **没有 Room 迁移**，中途退出照样原位续做。

## 后果

- **间隔会明显变短**，尤其那些靠四选一刷上去的词：一张卡要三次认出来才推进一次 GOOD。
  这是要的效果——宁可前期多考几遍，也别在到期时给假信心。
- **测验的题量一点没加**：一次作答仍然只答一道题，只是其中两道改成"不算数"的账。
  用户要的"不增加测验负担"守住了。
- **分享卡的「今日回忆」数字会变小**：一次测验组做完，只有攒满三次的那些词计进回忆。
  词墙（「今天测验的词」）同理只认 `recall`，纯 `attempt` 的词这一轮不进墙。报告里如实说明。
- 老库里已有的 `recall GOOD` 不回滚、不重算：闸门只管从今天往后的作答。
- 易错词"自动移出"没做：仍然要用户点一下，跟"系统只推荐、绝不自动关联"是同一条规矩。
- 真机未验证（用户睡觉时改的）：闸门、移出提示、整组干扰项、易错练习都在 JVM 单测里锁住
  （`ScheduleServiceTest` 7 条、`QuizServiceTest` 6 条、`TrickyRetirementTest` 4 条）。
  2026-11-11 在模拟器上直接查库对过闸门的两个分支：`whisper` 答对只多一条
  `kind='attempt'` + `rating=3` + `preDueEpochSec=-1`，那张卡的 stability / due / reps 一个字节没动；
  `brittle` 答错立即写一条 `recall` + `AGAIN`，卡片真的被推进（reps 3→4、lapses 1→2）。
  易错练习入口实走：今日页出现「易错练习 3 词」→ 进去是 3 题、标题挂着「易错练习」的牌，
  中途退出后 `quiz_session` 里 `DUE` 与 `TRICKY` 两行并存，再点原样回到同一组同一题。
