# 0038 干扰项真轮换、选项释义不带词性标记

- 日期：2026-11-20
- 状态：已实现，待真机验收（v4.5 候选；基于 v4.4.0 之后的未提交改动）
- 触发：用户真机用完 v4.4 后提的两条——「有时候我发现连续好几个题的选项除了正确答案的在变，其他三个选项都是一样的」「自己写的易错词在选项中，是没有 v.n. 这样的标记的，那就会一眼看出哪个是易错词从而排除错误答案呀」

## 背景

**第一条（干扰项雷同）**：`QuestionBuilders.rotate(items, attempt, size)` 原本拿 `attempt`（这个词被考过几次）当偏移，`start = attempt * size mod n`。可是同一组卷子里连续出现的是**不同的词**，它们各自 `attempt = 0`，而候选池 `wordDao.randomWords(POOL_SIZE) + words` 在一组里是固定的——于是每个新词的选项都落在同一格起手，连续几题看到同样三个词库干扰项。只有'这个词以前被考过'才会错开，而一组里的新词大多没被考过。

此外 `attempt` 只数 `kind='recall'` 的记录：认出闸门挡下的 `attempt` 不动它，同一个词当天第二遍、第三遍被考时窗口纹丝不动。

**第二条（词性标记暴露易错词）**：选项的中文面分两种来源——词库词取 `translation`（ECDICT 的精简释义，开头带 `adj.` / `vt.` / `[医]` 这类标记），手写的易错词取 `tricky_words.gloss`（用户自己填或从 ECDICT 抄，通常不带标记）。两种并排时，一眼就能看出哪一个是自己标的易错词，直接排除——选择题的干扰项自己出卖了自己。

## 决定

### 1. 偏移带盐：按「词面 + 题型」而不是只按次数

`rotate(items, attempt, size, salt)`，`start = floorMod(attempt * size + salt, n)`；`salt = saltOf(key, type, seed) = stableHash("$key|${type.key}", seed)`。

- **每个词的起手格由词面与题型共同决定**：同一组里不同的词各有各的盐，连续几题不再撞同一个窗口；同一个词隔天再考（`attempt` +1）窗口顺移一整段。
- **`attempt` 改数 `kind='recall'` 与 `kind='attempt'` 两种记录**（`QuizService.build()` 里 `count { it.kind == LOG_KIND_RECALL || it.kind == LOG_KIND_ATTEMPT }`）：认出闸门挡下的作答也算考过一遍，免得同一天三遍复习都看到同一批干扰项。
- **同词搭配题的盐挂在中心词上**（`saltOf(head, type, seed + 47)`），不挂在短语上——短语自己会随 `attempt` 轮换，如果盐也跟着变，两次抽到的窗口可能重合（单测踩过：`theNextAttemptAsksAnotherOfTheWordsOwnPhrasesAndAnotherDistractorSet` 第一次实测抽回同一个池子）。
- **易混词保留位不动**：`KEEP_CONFUSABLES = 2` 的直连易混词仍然固定在最前（它们答错一次就该看见），只有剩下的填充位参与轮换。`salt` 只作用于 `fill`，`lead`（AI 干扰项）照旧按 `attempt` 轮换。

### 2. 选项释义剥掉词性与域标记

纯函数 `glossFace(raw)`：按 `\n` / `；` / `;` 拆段 → 每段用 `GLOSS_PREFIX` 剥掉开头的 `adj.` / `vt.` / `[医]` / `&` / `/` 这类标记 → 空段丢弃 → 逐字去重 → 用 `；` 接回。

- **所有选项面都过这一道**：`Face.WORD`（中→英题干里的中文提示、答完后英文选项下面露出的中文）与 `Face.GLOSS`（英→中对四个中文选项的展示，以及挖空 / 释义 / 搭配题的中文面）——`choice(en, zh, face)` 统一构造。
- **中→英的题干也过**：`ZhToEnBuilder` 的 `prompt = glossFace(word.translation)`，否则题目问的是一句带 `adj.` 的话，选项却是剥干净的中文。
- **正确答案那一面同样剥**：`correctChoice(word, face)` 里也走 `glossFace`，否则正确选项自带标记、干扰项不带，反向暴露。
- **`glossFor` 改成词库优先**：`QuizService.glossFor()` 先查 `tricky_words` 再查词库，现在反过来——同一个词在两处都有释义时，词库那一份覆盖手写的。否则一个词当正确答案时显示手写版（不带标记）、当干扰项时显示词库版（带标记），一道题里出现两种拼法。

## 后果

- **没有 Room 迁移、不动任何数据**：改动全在出题构造与释义面拼装里，库里一个字没写。
- **同一组卷子里的干扰项集合现在会随题变化**；同一个词当天第二遍被考时窗口也顺移。用户说的"连续好几题一样"不复现。
- **词性标记对所有人都一视同仁**：手写易错词与 ECDICT 词库词在选项里长得一模一样，没法凭标记挑出"哪个是我标的"。代价是中文释义读起来没了词性提示——但题目本来就是认意思，不是认词性。
- **手写释义被词库覆盖**：用户给易错词手写的 gloss 如果与词库释义不同，选项里显示词库那一份。这是为了"同一个词一道题里只有一个面"，记在这是有意的取舍。
- **已知未覆盖**：`glossFace` 只剥**一个词条开头**的标记，中途出现的 `；vt. …` 这类段落标记也剥（拆段后每段都过一遍），但像 `a. 与 b.` 这种正文里嵌着字母点号的自然不会误伤（正则要求 1–6 个字母紧跟点号且位于段首）。

## 验证

- `QuestionBuildersTest` 新增 3 条：`consecutiveQuestionsDoNotRepeatTheSameDistractors`（10 个词连续出题，任意相邻三题的干扰项三元组不同、至少 9 种组合）、`optionFacesDropPartOfSpeechMarkersSoTrickyWordsDoNotStandOut`（词库 `adj. 无处不在的` 与手写 `不情愿的` 并排，选项里没有一个匹配 `^[a-zA-Z]{1,6}\.`，中→英题干同样剥净）、`glossFaceStripsTheMarkersEcdictPutsInFrontOfEachSense`（`adj.` / `vt.&vi.` / `n.` + `[医]` / 无标记四种输入）。
- 全量门：**477 项 JVM 单测 0 失败**（app 476 + FSRS 参考向量 1），`:app:assembleDebug` / `:app:lintDebug` 通过，lint 仍是那两条既有 `VectorPath` 图标长路径告警、0 error。
- `emulator-5554` 实走（`scratch/v44/`）：一组 4 题今日测验的干扰项分别是 {低语, 转瞬即逝的, 不该出现的长词}、{脆弱的, 转瞬即逝的, 低语}、{港…, 雾, 脆弱的}——不再雷同；同一个 `lantern` 在「今日复习」里第二次被考，选项换成 {灯笼, 港口, 需要…, 不该出现的长词}（第一次是 {低语, 灯笼, 转瞬即逝的, 不该出现的长词}）。易错练习里 `reluctant` 的四个选项是 `港, 避难所；庇护, 藏匿, (使)入港停泊` / `居民` / `相关的` / `不情愿的`——词库释义剥掉 `n.` / `vt.`，手写的 `不情愿的` 与它们没有任何可辨差异，答完露出的 `ubiquitous / 无处不在的` 同样干净。