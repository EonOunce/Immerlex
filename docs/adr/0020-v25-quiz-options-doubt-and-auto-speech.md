# 0020 v2.5：测验的另一面与「不确定」、分享卡词墙、自动发音

日期：2026-10-31。状态：已实现，模拟器验收（待真机）。前作见 [ADR 0019](0019-daily-share-card.md)。

## 背景

用户真机第二轮用完后提了五条小改（原话摘要）：

1. 分享卡上已经有大号的「连续 N 天」，上面那行「连续打卡」是重复的，删掉；卡片再加「今天学过的词」「今天测验过的词」；
2. 测验加一个「不确定」按钮；
3. 选错时（比如英→中）把所有中文对应的英文都显示出来，并问一句要不要把这个词加入易错词；
4. 句子挖空的选项本来就已经是英文，答完不要再把那个单词重复写一遍，也不用那个「—」；
5. 易混词加搜索；
6. 英→中 / 中→英 与阅读区点开单词时，可以选择是否自动发音。

平板适配（v3.0）单独立项，这一刀不碰。

## 决定

1. **选项自己带另一面，答完就地揭开**。`QuizOption.gloss` 的含义从「答完后展示的释义」改成
   `[text]` 之外的另一面：英文选项带中文释义，中文选项带英文单词（`QuestionBuilders.correctChoice` /
   `choice` 两处一起改）。于是「列出所有选项的释义」那段独立文案和「正确答案是 X（…）」那一行都删了——
   正确那项已经用边框与「正确」标出来，再把同一个词写一遍是重复。挖空题的选项本来就是英文，
   揭开的是释义，正好是用户要的「不再把单词写上去」。
2. **「不确定」不是第五种评分，是一条答错**。`QuizViewModel.doubt()` 走与 `answer()` 完全相同的记账
   路径（`quiz.record(question, correct = false)` → `recall(AGAIN, source = quiz)`），差别只在
   `chosen = null`：没有「你选的这一项」可标红，只把答案翻开，照样排进本轮末尾换题型重练。
   FSRS 那边一个参数都没动，所以撤销窗口、小鱼干、热力图口径全部照旧。
   判定那一行也不写「答错了」——点「不确定」的人没撒谎，写的是「算没记住」：后果四个字说完，不再解释为什么。
   `QuizState.answered = chosen != null || doubt`，续做时 `doubt` 跟着 `quiz_session` 存回来。
3. **答错或不确定时问一句「标为易错」**。新的 `TrickyRepository.mark(word)` 只打标记、绝不挂易混关系
   （沿用「系统只推荐、由用户点选」这条）；一组的 `trickyIds` 在建组时从 `tricky_words` 现查一次并随会话
   存进 `quiz_session`，所以已经标过的词不会再问，中途退出也不会忘。
4. **朗读从阅读页搬到 `app/ui/WordSpeech.kt`**。原先 `WordSpeaker` 住在 `feature/reader`，测验要用就得
   跨 feature 依赖；`app/ui` 是既定的跨 feature 呈现层，`ReaderSheets` 与 `QuizScreen` 现在共用同一个
   `rememberWordSpeaker()`。
5. **自动发音只读不会剧透的那一面**：`speaksNow(type, answered)`——英→中出题即读（题干就是那个词），
   中→英要等翻开答案之后才读（先读等于把答案念出来），句子挖空与释义选词不自动读。开关是
   `Appearance.autoSpeak`，默认关（在公共场合连着出声比听不到更糟），设置页改了立即生效；
   没有语音引擎或没有英文语音包时读不出来：测验里直接把按钮藏掉，阅读词卡说明缺哪一样。
6. **分享卡的两行词墙只列一小截**。`DailyLoopService.todayTally()` 的返回从两个 `Int` 换成
   `learned / recalled / quizzed` 三份词面（按字母序），卡片上的数字就数这一份，两边不会各说各的；
   一天二十几个词全写会挤掉热力图，所以只列两行——折行由 `wallLines` 按字符数自己算（交给文本换行会把
   分隔点甩到行首），剩下的写一句「还有 N 个」；卡片上两张墙都填满时页脚仍不被挤掉。
   「今天测验的词」按 `source = quiz` 再筛一次，与「在读里回忆过」分开——两张墙回答的是两种动作。
7. **`LOG_SOURCE_QUIZ` 落到 `core/db/Entities.kt`**。`core.schedule` 不能反过来依赖 `core.quiz`，
   而分享卡需要知道测验那批记录的 source 标记，所以常量与 `LOG_SOURCE_READER` 做邻居。
8. **`quiz_session` 加两个可选键，不升 Room 版本**。`doubt` / `trickyIds` 是 JSON 里的字段，
   kotlinx-serialization 用默认值读旧存档（`doubt = false`、`trickyIds = 空`），表结构一个字没改，
   所以这一刀没有 v14 迁移。
9. **阅读页点词的命中区把词尾也算上**。`TextLayoutResult.getOffsetForPosition` 返回最近的字符**边界**，
   点在最后一个字母的右半边会解析到 `annotation.end`，三四个字母的短词右半片一整块点不动。
   `ReaderText` 里新增 `wordRanges(annotated)` 一次把标注折成区间，`wordAt(ranges, position)` 除
   `start until end` 之外接受 `position == end`。
10. **易混词搜索留在纯函数里**。`TrickyGroups.matching(groups, query)` 命中组内任一成员的词面或释义就
    保留**整组**——拆开了就看不出谁和谁混；搜索框只做本地过滤，不改 `groups()` 的取数。

## 后果

- 选错一个中文选项能直接看见它对应的英文，这是原来「列出所有选项的释义」想给的信息，现在换了位置、
  少了两行字；`QuestionBuildersTest` 锁住「中文选项的另一面是英文单词」。
- 「不确定」和答错在库里一模一样，用户看不出区别（除了没有标红的错误项）；这是有意的——想不起来就是
  没记住，不该有一个既不扣分也不推近间隔的第三种答案。
- 自动发音默认关，所以这一版对没主动开声音的用户行为完全不变；`reader_speak` 那个手动按钮一直都有。
- 词墙把卡片顶部的空间吃掉了，`kicker`、段落间距、大号数字与热力格一起收紧过；版面是 1080×1440 的
  固定像素，实际留白要靠导出 PNG 目检，不能只看代码。
- `TodayTally` 从计数变成列表，`HomeViewModel` 的 `learnedToday / recalledToday` 也跟着换成 `List<String>`；
  词已从词库删掉的不会出现在墙上，也不会被计入数字（两边同源，宁可少算一个也不让数字与列表矛盾）。
