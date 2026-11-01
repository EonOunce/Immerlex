# 0022 出题素材挂在词面上，词典搜索与词库搜索并存（v3.1）

## 背景

用户要覆盖考研红宝书全量词，素材包由外部（另一个 AI）生成。这里有一个结构性问题：
`quiz_material` 原先按**词库那一行**（`wordId`）存素材，于是

- 删掉再加回来的词会重新向模型要一遍素材；
- 还没进词库的考研词根本挂不上素材；
- 用户在词库搜不到词时，词典里明明有，却看不到任何素材。

用户还明确要求：**搜索一个单词时词库与词典都要搜，两种结果的数量不同；点开单词就能看到
素材包里的信息**，词典词卡只读并提供「加入词库」；机器给的易混词（素材包里的 distractors）
**只在出题时参考**，用户自己的易混关系优先，且绝不把机器给的写进 `tricky_words`。

## 决定

1. **素材主键改为小写词面**（Room v15，`MIGRATION_14_15`）：`quiz_material(word, definition,
   example, exampleZh, distractors, updatedAtSec)`，`word` 为主键。词面归一化只有一处：
   `core.word.wordSurface(raw) = raw.trim().lowercase()`。迁移按 `lower(trim(words.word))` 折面，
   大小写撞车留 id 更小的那条，找不到词的素材行丢弃。读的时候按词面反查 id 发给出题层，
   `core.quiz` 一个字不改。
2. **素材包是纯 JSON 文本**（`core.material.MaterialPack`）：解析、校验、渲染都在 core，
   文件读写留在界面走 SAF。规矩是"单条不合格只丢那一条，整包照收"；读不懂的文件直说
   （`PackFailure.NOT_JSON / NOT_ARRAY / EMPTY`），不能当成空包；超长字段截断不报错。
   丢了多少条按 `DropReason` 计数。
3. **合并是 fill-only**（`MaterialMerge`）：包给了的字段盖掉旧的，包没说的字段留住原有的；
   例句与它的中文对照成对换；易混池整池换而不是两边掺。重导同一份包不产生任何写入。
   只写词面的易混候选由 ECDICT 补释义，补不上就丢掉这一条。
4. **导入是一个事务**（`MaterialRepository.import` 走 `withTransaction`），中途崩了就当没导过。
   导入/导出都是全量，不依赖当前搜索。
5. **词典检索**（`core.dict.DictionarySearch` + `EcdictDictionary.search`）：英文按前缀匹配
   （`dict.word` 小写且有索引），中文按包含匹配；`%` `_` 当字面量转义；至少两个字才查。
   词库页先列词库命中，再列"词典里还有 N 个"；两组各报各的数量。
6. **词典词卡只读**（ViewOnly）：显示素材包里的释义 / 例句 / 搭配 / 易混候选，动作只有
   「加入词库」。没进词库的词不排复习、没有正文颜色。
7. **机器给的易混词只在出题时读**：`QuizService` 把它们放进 `QuestionContext` 的选项来源，
   用户 `tricky_links` 的候选排在最前且固定保留；两者从不互相写入。
8. **不把素材包打进 APK**：包里是用户自己再生成的语料，进版本库既大又无法随红宝书更新。

## 后果

- 词条的身份从"词库行"变成"词面"，删词重加、词典直查、换机导入都认得同一份素材；
  同一词的大小写写法差异被吸收成一行。
- 素材包格式成为对外契约（`word / definition / example / exampleZh / distractors /
  collocations`），外部 AI 按这个形状生成即可；缺少字段仍能出题，只是题更少。
- 词典搜索与词库搜索的排序、数量互不影响，用户能清楚区分"我已经在学"和"词典里有"。
- 相关测试：`MaterialPackTest`、`MaterialMergeTest`、`MaterialImportTest`（设备）、
  `MaterialSurfaceMigrationTest`（v14→v15、v1→v15 全链）、`DictionarySearchTest`、
  `EcdictSearchTest`。

## 素材包的生成约定（2026-11-01 补）

素材包由外部的人或 AI 生成，所以规则要写成对方能照做的样子（100 词试包已验证过一遍）：

- 一份文件必须是一个**完整、合法**的 JSON 数组：不重复输出、不截断、不夹说明文字；
  **每 100–200 词拆一份**，坏一份只重导那一份。一份 100 词约 35KB，这已经是见过的
  最大可靠输出；第一份试包就是让它一次写太多——两段草稿拼进一个文件、断在句子中间，
  整份读不了。分批导入是安全的：后一份只覆盖它自己那几个词。
  词序随便（新词按 `ORDER BY RANDOM()` 抽），但**别随机挑词**，按你发过去的清单顺序走，
  这样双方都知道做到哪了。
- 每个词给 `word / definition / example / exampleZh / distractors / collocations`；
  `definition` 是英文释义，`exampleZh` 必须是**例句**的翻译而不是词义，
  `collocations` 的 `en` 必须真的含这个词、`zh` 必填。
- **干扰词必须是拼写相近但意思不同的词**（`statue / status / stake`），
  禁止近义词、同义词，也不能是能替换进例句仍然成立的词——否则一道题会出现两个正确答案。
  自检办法：把每个干扰词代进例句、再对着释义读一遍，仍然成立就换掉。
- 搭配优先给固定搭配与动宾/介宾（`at least`、`state law`、`parking space`），
  不要用"任何词都能拼"的自由组合（`especially important`）凑数。
- 词表底稿取 ECDICT 精简库里 `tag` 含 `ky` 的 4799 个词，素材包按这份清单**预先备好**；
  **词库仍是用户自己按天加的**（截图 / txt / PDF / 词典搜索里点「加入词库」），
  不把这份清单批量导进词库——用户 2026-11-01 明确否掉了批量导入。
  两者靠词面自动对上：某个词哪天被加成生词，它的素材早就在库里了。
  **红宝书原文与释义不复制**（版权）；中文词义统一取 ECDICT，个别多义由用户自己在词库里改。

