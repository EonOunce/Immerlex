# Immerlex 工作指导手册

> **强制阅读**：任何 AI 会话在本仓库开始工作前，必须完整读取本文件。
> 若本文件与用户最新对话冲突，以用户最新决定为准，并在当次会话内把该决定回写本文件或 `docs/adr/`。

## 1. 产品是什么

Immerlex 是一个"把英文单词融入中文小说"的安卓应用：
词库（截图 OCR / txt / CSV 导入，ECDICT 补全释义）→ FSRS 间隔调度 → AI 按题材生成中文章节并按密度行内嵌入"新词 + 到期词"→ 双阅读模式（学习：释义可见 / 复习：释义隐藏、点击揭示或挖空）→ grade 回写 FSRS → 统计趋势。

- v1 范围：词库导入 + FSRS 调度 + AI 生成嵌词 + 双阅读模式 + 章节回看与重写 + 故事圣经 + 今日任务页与通知与坚果云备份（v1.0 收尾）。
- v1.2（已实现待真机验收）：统计趋势图、词库 CSV 导出、阅读页"只留英文"。
- v1.3（已实现待真机验收）：独立测验会话（四题型、答错换题型重练、提前复习与"再来一组"）+ 统一 learn/recall 记账（取消同日封顶与改评分，改为几秒内撤销）+ 复习模式里未到期的词只接受「没想起来」（「想起来了」不记账）。
- v1.5 剩余：导入嵌词（AI 建议插入点 → 用户确认）。

## 2. 已定决策（推翻需用户明确同意）

| 项 | 决策 |
|---|---|
| 技术栈 | Kotlin + Jetpack Compose + Room，离线优先；非密钥设置用 SharedPreferences（`core.settings.AppPreferences`），API key 用 EncryptedSharedPreferences（`core.ai.ApiKeyStore`）——因密钥本就必须走 EncryptedSharedPreferences，两套存储并存比引入 DataStore 更简单；字号/深色走 `AppPreferences.appearance` StateFlow（改存即生效，不等"保存"按钮），主题与阅读页从 `LocalAppearance` / `LocalMasteryTones` 读 |
| 调度算法 | FSRS-6（open-spaced-repetition 当前版本，21 参数），移植参照 py-fsrs scheduler.py；单测必须与参考测试向量一致（向量由 py-fsrs 生成：无学习/重学步、fuzz 关闭、上限 3650 天、DR=0.9）。用户 2026-10-27 确认升级 FSRS-6，见 `docs/adr/0001-fsrs-6-version.md` |
| 词典 | ECDICT（skywind3000/ECDICT）sqlite 精简版，asset 或按需下载 |
| 截图 OCR | ML Kit `text-recognition-chinese` bundled 模型（`core.ocr.MlKitOcr`），离线可用；arm64 release APK 实测 25.49MB，其中离线中文 OCR 模型占约 11.5MB。用户 2026-10-28 确认**保留 bundled**：主词源是微信/相册截图，必须在无 Google Play 服务的国产 ROM 上离线可用，包体积代价接受 |
| PDF 导入 | `core.ocr.PdfOcr`：`PdfRenderer` 逐页渲染成位图（目标宽 1500px、最多 20 页），复用截图导入的 ML Kit 通道，不另建识别链路。渲染位图**必须先铺白底**再画页：无底色 PDF 在透明位图上会让 ML Kit 把黑字当黑底，整页识别为空（`PdfOcrTest` 锁住）。`PdfRenderer` 不保证线程安全，整个渲染循环钉在单线程 dispatcher |
| 发布包 | 只交付 `app/build/outputs/apk/release/immerlex-arm64-v8a-release.apk`（用户 2026-10-27 指定）；ABI 切分为 arm64-v8a + x86_64（后者只服务模拟器），不出 universal APK；release 用 `keystore.properties` 指向的 `D:/dev-tools/immerlex-release.jks` 签名，该文件与 jks 均不进版本库；R8 保持关闭（未经设备验证不混淆） |
| AI 接入 | OpenAI 兼容协议；baseURL / key / model 由用户首启手动配置；代码与仓库中禁止硬编码 key |
| 语言对 | 仅中文小说嵌英文词 |
| 嵌词方式 | v1 生成嵌词；导入嵌词 v1.5 再做 |
| 密度 | 每千汉字三档：疏 10–15 / 中 20–30 / 密 35–50，**默认中**（用户 2026-10-28 改，原先默认密）；每日新词上限默认 25；目标篇幅 **千字左右 / 每章 20–30 词**（实测：密度调高到 35–50 时，千字里塞 35+ 词会让模型写成砸词的生硬文本，句子质量优先于密度）；嵌词槽位 = 新词（带上下文线索）+ 到期词复现（无需线索）混排；词库不足时自动降档，**降档静默即可**，不在 UI 里提示（用户 2026-10-28 确认） |
| 章 / 词的单位 | **章是阅读单位，词是调度单位**（用户 2026-10-28 定，见 `docs/adr/0004`）。同一个词在多章复现时，一次打分即清掉这次到期；删章不回滚复习进度；生成下一章不得让旧章无法继续阅读与打分，因此阅读页必须能在本章各章间切换（标题即入口），并提供"重写本章（同词重生成，编号不变）"与"删章" |
| 复习计分（v1.3 起，取代 ADR 0004 的同日封顶/改评分） | **一次独立回忆记一次，交给同一个 FSRS 连续更新**（`review_logs.kind`）。`learn` = 第一次接触、答案可见时写，只开始排期，不算回忆成功；`recall` = 先藏答案、先作答再反馈，阅读复习模式/测验/提前复习都记。看释义、查词、答错后的当堂重练不记。**没有每日次数上限，也不再改写旧评分**；阅读页评分后给几秒撤销窗口（删最后一条并恢复评分前快照），过后即真实记录。学习模式不提供评分（第一次 learn 除外），想计分请切复习模式或做测验。**复习模式里还没到期的词只听坏消息**（用户 2026-10-29 补）：判断发生在点开之前，所以「没想起来」算一次真实回忆（`AGAIN` + `allowEarly`，同样可几秒撤销，词会提前回来），「想起来了」什么都不记——提前记成功会把间隔推远。见 `docs/adr/0012` |
| 独立测验（v1.3） | 今日页入口，不是第五栏。只考今天到期或今天在阅读里学过的词；四种题型：英→中、中→英、句子挖空、英文释义选词。点选项立即判对错，并列出**所有选项的释义**。答错词在本轮末尾换一种题型再练一遍，**这次练习不写记录**。本地题库/章节句子优先；AI 只补缺失的英文释义、例句和干扰项，失败静默降级，没有 API key 也能完整使用。 |
| 题型策略（v1.3，扩展契约） | 一个词只有一张 FSRS 卡，题型只决定怎么考。门禁：新词只开英→中 + 挖空；有一次成功回忆后加中→英；中→英成功后加释义选词；易错词一开始四种都可考。最近一次 recall 答错时退回英→中/挖空。选择：在可用题型里挑历史成功率最低（平滑 `(成功+1)/(尝试+2)`）+ 最久没问的一种，刚答错的题型避开，同分用会话种子打散。新增题型只需登记 `QuizType.tier` + 一个 `QuestionBuilder` + UI 文案，门禁/换题型自动生效，不碰 FSRS。见 `docs/adr/0012` |
| 提前复习 / 再来一组（v1.3） | 取当天碰过、还没到期的 5 个最不稳定词，照常记 recall；每组做完可"再来一组"，**没有每日上限**。今日页另有"再练 5 个"入口。 |
| 备份 | 坚果云 WebDAV 自动备份（用户 2026-10-28 选定）：快照→gzip→上传，**禁止**把正在写入的 SQLite 文件直接交同步通道；WebDAV 无版本历史，故按日期命名并在云端保留最近 30 份 + 每月最后一份；备份**明文、不加密**；AI key 不进备份，恢复完需重填 baseURL/key/model。流量测算：重度使用一年约 1.5MB/份，每日一份约 45MB/月，远低于坚果云免费版 WebDAV 的 1GB/月上行 |
| 通知 | 每日到期提醒（默认 20:30，可改可关）+ 生成超过 10 秒挂进行中通知、完成转"第 N 章已写好"；文案只说真实状态（"有 N 个词到期"），在章节状态存在前不得写"该读第三章了" |
| 今日任务首页 | 底部第四栏 `feature/home`（原计划 v1.5，用户 2026-10-28 提前到 v1.0）：今日到期/已完成、连续天数、一个"开始今日阅读"入口。打卡门槛低——**当天学过或回忆过任意 1 个词即算**；漏一天不归零，次日清空当日到期词即自动补上，每周最多补 1 次；**不做积分/兑换**（外部奖励会给"制造复习次数"的动机，与我们刚定的防刷规则互相拆台） |
| 日常化落地（v1.0 已交付 2026-10-28） | 今日页的到期数**不另算**，取 `core.schedule.ScheduleService.todaysQueue()` 的 `reviewWords + newWords`，与生成下一章喂给模型的是同一份词表；连续天数与补卡是纯函数 `core.schedule.dailyInfoOf(DailyLoopInput)`，Room v4 新增 `daily_loop`（按 `epochDay` 一行）与 `backup_runs`。提醒用**自排程的一次性 unique work**（WorkManager 周期任务只有"每 24 小时"会漂到凌晨），`secondsUntilNext` 纯函数，worker 在 `finally` 里排明天、应用启动再重排，所以改时刻立即生效；当日 0 个到期就不弹，`last_reminded_day` 保证一天最多一条。备份固定为 `VACUUM INTO` 快照 → gzip → PUT，保留策略是纯函数 `core.backup.BackupPlan`；恢复先校验（gzip 头 / `user_version` / `integrity_check` / 必备表），不过就**逐项明说且不动本机库**，过了才换文件并强制"重开应用"。坚果云应用密码与 AI key 同存 EncryptedSharedPreferences，地址预填 `https://dav.jianguoyun.com/dav/`。详见 `docs/adr/0006` |
| 故事圣经 | 长篇的跨章连贯由三层承担（见 `docs/adr/0005`）：① 开局由用户口述一段想法，**一次调用**展开成结构化故事圣经（基调/主角与目标/结局倾向/禁止事项/起始人物），逐条可编辑可重掷；② 生成契约要求模型回报**本章变化**（谁的位置、关系、承诺或信息发生变化），系统合并进人物状态表；③ 本章全书逐章一行梗概全量入 prompt，超过约 60 章才折叠成分卷摘要。用户写过的条目优先级最高，冲突时不覆盖而标提示。**绝不**做聊天式多轮开局界面：入口只到"一段话 → 一份圣经"为止，否则等于为输入再造一个 app |
| 故事圣经落地（v0.3 已交付 2026-10-28） | 三层各一张表，Room v3 起 `bible_entries`（题材分区条目 + `authoredByUser`）与 `character_states`（题材内 `(genre,name)` 唯一）；合并在纯函数 `core.bible.CastMerger`（两条铁律：用户手写状态只标冲突不覆盖、旧章不能把新章之后的人物状态往回改）。展开调用是 `core.ai.BibleExpander`，schema 校验、≤2 次修复、超限即裁剪（条目 160 字 / 禁止事项 8 条 / 起始人物 12 条）。prompt 升到 **PROMPT_V3**：档案 → 人物当前状态 → 全书梗概（由旧到新）三层全量入 prompt，用户条目后挂「（读者设定，务必遵守）」，冲突写「注意：第 N 章把它写成了…」。入口在设置页「查看并编辑故事档案」，**没配 API key 也能逐条手填/删除**——AI 只是把一段话展开成初稿，不是使用档案的前提 |
| 短篇模式 | 意林/花火/科幻世界式单篇自足短篇为 **v1.5 第一项**：不依赖跨章连贯层，字数由词数决定（15 词 → 1500–2500 字），"像某杂志"只做**腔调预设**（小人物点题 / 情绪华丽 / 设定先行冷结尾）而不复制其样文。**2026-10-29 已实现（v1.1.0，模拟器验收）**：`chapters.kind = "short"`、章号 0、`genre` 存腔调名；复用章节 JSON 契约，不写故事圣经/人物状态；`ChapterGenerator.generateShort(tone)` 取今日队列前 15 词，`rewrite()` 对短篇保持同腔调同批词。**2026-10-29 腔调 brief 补强**（只描述写法，不复制样文）：EVERYMAN = 具体小事 → 三句内进场景 → 小转折 → 动作或对话点题、不哲理总结；LUSH = 青春都市情感错过、贴身视角、具体物件（校服/车站/旧短信）、画面与音乐感、以没说出口的话或反复意象收尾；COLD = 一句话核心设定、事件中段切入、设定只通过操作与后果交代、有代价的选择、冷处理反转或留白。**2026-10-29 短篇自由度（Room v8）**：三个预设腔调自由度太低，写短篇改为可选填「这篇想写什么？」+ 第四个 chip「自己定腔调」（选中才出现第二个输入框）。`chapters` 新增 `toneText` / `idea`（`NOT NULL DEFAULT ''`，连载用不到），自己定腔调时 `genre` 存 `custom`、原话进 `toneText`；两句跟着这一篇落库，`rewrite()` 原样喂回模型，所以重写不换腔调、不换词。载体是 `core.ai.ShortPlan(tone, toneText, idea)`，`generateShort(plan)` 收这一个对象。见 `docs/adr/0013-embedding-typography-shelf-and-short-freedom.md` |
| 书架与阅读位置 | **书 = 题材**，不新增 `books` 表；短篇单独列一篇。Room v5 新增 `reading_positions`（连载 `book:<题材>`、短篇 `short:<章 id>`，`updatedAtSec` 最新的一行即上次阅读落点）；新建但没写正文的书用 `chapterId = 0` 占位。**2026-10-29 书架重做（ADR 0013）**：不再是阅读页底部的半屏 sheet，改成整页 `feature/reader/ShelfPage.kt`；顶部一张"正在读"大卡（腔调 / 标题 / 连载或短篇 / 继续读），下面「连载」「短篇」两组 **1:1 色块卡**——用户明确否掉"像阅读 app 那样真摆个长方形（书脊/封面）"，太老太土。色块颜色由题材名哈希出的**固定色相**决定（`shelfHue` 返回 0–359 **度**，`Color.hsl` 要度数不是 0–1 比例；`ShelfHueTest` 锁住这一点，之前按 0–1 返回时所有卡片塌成同一酱色），同一本书永远同一个色。深色下饱和度必须撑住（fill 0.50 / ink 0.55），否则蓝和红会塌成同一种酱色。短篇长按多选删除（`deleteShorts`），连载章节仍走本章目录逐章删；不设百分比进度条。关掉应用重开回到同一本同一章 |
| 热力图 | 今日页 13 周日历格，按**当天不重复词数**着色（同一天同一词连点多次只算一个），不按打分次数。learn 与 recall 都算"碰过这个词"。固定阈值 0 / 1–4 / 5–9 / 10–19 / 20+，不用相对分位。纯函数 `core.schedule.heatGridOf`，数据源同 `review_logs` |
| 应用名与规则页（2026-10-29） | 用户可见名「词间」，副标题「词在故事之间」只在关于页出现；包名、`applicationId`、数据库名、WebDAV 目录 `Immerlex` 一律不动，保证覆盖升级与旧备份可用。设置页底部「关于与规则」进入 `feature/settings/AboutScreen.kt`，打卡、热力图/趋势口径、测验与计分（四题型、门禁、答错重练、提前复习、撤销）、提醒、备份、故事档案、书架短篇、词库的完整说明集中在那里；主界面只留操作必需的一句话。见 `docs/adr/0010-rename-cijian-and-about-page.md`。**2026-10-29 桌面图标重画（ADR 0013，第三版才定）**：前景就是应用名的第一个字——思源宋体 Heavy 的「词」，em 46dp、按墨迹包围盒居中，最远一角离圆心 30.6，落在自适应图标 108dp 视口的 66dp 安全圆内；轮廓由 `scratch/icon/make_foreground.py` 从字体抠成 `pathData`（TrueType 二次曲线折成三次贝塞尔，`fillType="evenOdd"` 否则「口」「司」的腔体被涂实）。**横条组合（"三行正文 + 中间一个词"）在 48dp 下一律读成"列表 + 一个色块"、最后退化成汉堡菜单，铁锈橙一并否掉**——小尺寸下唯一站得住的是单个汉字。不画书脊也不画封面——这个应用的对象是"词在句子里"。`drawable/ic_launcher_monochrome.xml` 同一个字形、全白，供 Android 13 主题图标使用；背景色改成纸色 `#F5F4F0`、字形竹青 `#2F7D4F`，包名与 `applicationId` 一律不动 |
| 嵌词排版与状态栏避让（2026-10-29） | 学习模式渲染 `word（释义）`：全角括号紧贴单词、**中间不留 ASCII 空格**，括号与释义一起刷高亮底色（那个空格既是换行点、把释义甩到下一行，又被刷进色块）。复习模式**只画单词、不画 `（　）` 占位**：判断发生在点开之前，空括号是噪声。见 `ReaderText.buildChapterText` + `ReaderTextTest`。根 `ImmerlexRoot` 的 Scaffold 已吃掉状态栏内边距，**嵌套 Scaffold 必须传 `app/ui/RootHandledInsets`（= `WindowInsets(0,0,0,0)`）**，M3 `TopAppBar` 另有自己的 `windowInsets` 参数也要传；漏掉就多出一整个状态栏的空白（词库/阅读/设置/测验/关于/故事档案六页踩过，今日页因为没嵌套所以一直是对的） |
| 统计趋势图（v1.2） | 今日页"近 30 天趋势"柱状图，口径与热力图一致（**当天不重复词数**，learn + recall 都算）；窗口含今天、未来与窗口外一律忽略；无数据的日子画 0 基线。纯函数 `core.schedule.trendSeriesOf`，订阅 `review_logs` 同一数据源，不另建统计表 |
| 词库导出（v1.2） | 只做 CSV 两列 `word,translation`（UTF-8、**无表头**、RFC 4180 转义），Anki 直接导入 CSV 即可，不维护 Anki 自有格式；走 SAF `CreateDocument("text/csv")`，导出**全量词库**、与搜索过滤无关。纯函数 `core.word.wordCsvOf` |
| 只留英文（v1.2） | 阅读页开关：中文字符与释义槽**不删除、不重排**，只画成透明色，所以换行与英文词位置分毫不动；英文保持掌握度高亮、可点开释义卡与打分；开关不改 `ReaderMode`，也不写库。实现是 `ReaderText.buildChapterText(englishOnly)` 的透明 span，切回立即恢复 |
| 易错词与双向易混（2026-10-29 已实现，模拟器验收） | 阅读页词卡可**标为易错**（释义自动取词库→ECDICT），并挂上容易混的词；关系**双向**（点 affect 有 effect，点 effect 也有 affect）但只存一行有序对。系统只**推荐**（常见混淆对 + 拼写邻居，chip 点选后才建立），**绝不自动关联**；也可自己填英文单词，释义自动补全，查不到也允许先加入。Room v6 新增 `tricky_words` + `tricky_links`（无外键，阅读页的词可能不在词库）；链接里不在词库的词只读、不排复习。见 `docs/adr/0011-tricky-words-and-confusable-links.md` |
| 版本切分 | 五刀：**v0.2** 复习计分正确性 + 章节回看/重写/删章（已交付，2026-10-28 模拟器验收）、**v0.3** prompt 层（默认密度与篇幅、故事圣经、人物状态、全书梗概，已交付，2026-10-28 模拟器验收，versionName 0.3.0 / versionCode 4）、**v1.0** 今日任务页 + 通知 + 坚果云自动备份（已交付，2026-10-28 模拟器验收，versionName 1.0.0 / versionCode 5）、**v1.1** PDF 导入 + 书架/阅读位置 + 热力图 + 短篇模式（已交付，2026-10-29 模拟器验收，versionName 1.1.0 / versionCode 6）、**v1.2** 统计趋势图 + 词库 CSV 导出 + 阅读页只留英文（已实现，2026-10-29 模拟器自检，versionName 1.2.0 / versionCode 7；**待真机验收**）、**v1.3** 独立测验 + 统一 learn/recall 记账 + 提前复习/再来一组（versionName 1.3.0 / versionCode 8；**待真机验收**）、**v1.4** 用户真机截图指出的六条：嵌词排版（学习模式无空格、复习模式去括号）+ 六页状态栏双重避让 + 短篇自由度（想写什么 / 自己定腔调，Room v8）+ 书架整页重做（题材色块卡）+ 桌面图标重画（含 monochrome 层）（已实现，2026-10-29 模拟器自检，versionName 1.4.0 / versionCode 9；**待真机验收**）。每刀单独真机验收 |
| prototype/ | 一次性交互原型（仅阅读页视觉/交互规格），非产品代码，禁止进入 app source set |

## 3. 架构边界（防屎山条款）

- 单向数据流：Compose UI → ViewModel(State/Event) → Repository → core 能力（db / ai / fsrs / dict / ocr）。
- 按 feature 分包：`core/*` 提供能力，`feature/*` 提供界面；**feature 之间禁止横向依赖**，共享逻辑一律下沉 core。v1 实到 `feature/wordbank`、`feature/reader`、`feature/settings`、`feature/home`（v1.0 起的底部第四栏；故事档案面板 `BibleScreen` / `BibleViewModel` 住在 settings 里——它是设置页的一个子页，不值得单独成包）；v1.3 新增 `feature/quiz`，由今日页导航进入，**不是第五个底部栏**；生成入口放在 reader 页头（写下一章）与设置页，因为它只是 `core.ai.ChapterGenerator` 的一次调用，不值得单独成包；`feature/generate`（导入嵌词建议）随 v1.5 再建。跨 feature 共用的呈现逻辑放 `app/ui/`（如 `GenerateOutcome.toUiMessage()`、`BackupOutcome.toUiMessage()`）。
- Room 是唯一业务持久化入口；禁止用 SharedPreferences 散存业务状态。
- `core.bible`：故事圣经的领域层——`BibleSection` 枚举与 `BibleDraft` 等模型、`StoryContext` / `StoryContextSource`（生成侧只看这三层）、纯函数 `CastMerger`（合并规则可单测）、`StoryBibleRepository`（唯一写入口，只它碰 Room）。依赖方向单向：`core.ai` → `core.bible`，禁止反向。
- `core.backup`：`DbSnapshot`（`VACUUM INTO` 出一致性快照 + 校验，返回 `SnapshotProblem` 列表而不是布尔）、`WebDavClient`（只做 HTTP，异常带 `BackupFailure` 枚举）、`BackupPlan`（保留策略纯函数）、`BackupService`（编排 + 写 `backup_runs`）。`core.notify`：`ReminderScheduler`（排程，纯函数 `secondsUntilNext`）、`DailyReminderWorker`（只认 `ReminderHost` 接口，不认识 `AppContainer`）、`Notifier`（渠道与文案投递）。二者对外暴露的都是**能力对象**（`ReminderControl` / `BackupService`），`Context` 封装在对象内部，ViewModel 只注入能力对象——这是"ViewModel 禁止持有 Context"的具体实现方式。
- 旧列迁移后**保留但清空**（`story_bibles.charactersJson` / `synopsis`，被 `bible_entries` + `character_states` 取代）：`DROP COLUMN` 在 minSdk 26 上要 SQLite 3.35+，机型不稳；一次性回填（旧人物表与旧梗概搬进新表）放在 `Mutex` 后面，防并发改表把人写乱。
- `core.ai`：prompt 模板为带版本常量（当前 `PromptBuilder.VERSION = 3`：三层上下文 + 章节 JSON 里回报 `changes`；档案展开另带 `BIBLE_SCHEMA`，短篇另带 `SHORT_SCHEMA`）；AI 输出必须通过 kotlinx-serialization schema 校验后才能入库；解析失败走修复重试（最多 2 次），仍失败则向用户明示错误，禁止静默丢弃。缺 `changes` 键的旧 V2 章节行必须照常能读。短篇复用同一套章节契约与校验器，只换 prompt（`ShortTone`），且 `chapter.kind == "short"` 时**不写**故事档案/人物状态。
- `core.ocr`：截图与 PDF 共用 `OcrEngine`（`lines(context, uri)` / `lines(bitmap)`）。`PdfOcr` 只负责逐页渲染与进度回调；`PdfRenderer` 的单线程约束与"先铺白底再渲染"的 ML Kit 前提写在类注释里，改动前先读。
- `core.schedule`：到期队列（`ScheduleService` / `WordQueue`）、learn/recall 记账与撤销（`ScheduleService.learn` / `recall` / `undo`，`WordAccess` 决定词卡给不给评分）、每日连续与补卡（`dailyInfoOf`）、热力图（`heatGridOf`）与 30 天趋势（`trendSeriesOf`，两者都按"当天不重复词数"计，learn + recall 都算）都在这层；今日页与阅读页必须共用同一份到期数，不许各算一套。**撤销只删该词最后一条且只在 `UNDO_WINDOW_SECONDS` 内；评分前快照（`pre*` 列）是撤销的唯一依据，别删。**
- `core.quiz`：独立测验的领域层——`QuizType`（带 `tier` 门禁）、`QuestionBuilder` 扩展点（`QuestionBuilders.kt` 里的英→中/中→英/挖空/释义选词四种，本地干扰项 + `ClozeSentences` 从章节抽句）、纯函数 `QuizPolicy`（`unlockedTier` / `availableTypes` / `orderedTypes`：薄弱度 + 新鲜度 + 刚错换题型）、`QuizService`（今日候选、提前复习 5 词、答错备用题、可选 AI 补全）、`QuizEnricher` / `AiQuizEnricher`（AI 只补缺失释义/例句/干扰项，失败静默降级）。依赖方向：`feature/quiz` → `core.quiz` → `core.schedule` / `core.db` / `core.ai`；`core.ai` 不反向依赖 `core.quiz`。**题型只决定怎么考，不得另建第二套排期或到期日。**
- `core.word`：词条层面的纯逻辑（`WordCsv` 导出文本、掌握度 `mastery()`），WordCsv 只负责字符串，文件读写留在 feature 层走 SAF。
- `feature/reader` 的正文渲染拆成 `ReaderText.kt`（AnnotatedString，含只留英文的透明 span）与 `ReaderBody.kt`（正文/空态组件），`ReaderScreen.kt` 只做页面编排——阅读页曾逼近 400 行红线；书架整页另立 `ShelfPage.kt`（含 `ShortComposer` 写短篇对话框与 `shelfHue` 题材配色），不要把卡片画回 `ReaderScreen`。词卡评分按 `WordAccess` 显示：`Learn`（第一次）/ `Recall`（复习模式且到期，四档）/ `Early`（复习模式未到期，只有同权重的「想起来了」不记账 +「没想起来」记一次 `AGAIN`）/ `ViewOnly`（学习模式，不给评分按钮）；撤销提示与"本次已学/已回忆"也在这层。
- Room v5 起 `chapters.kind`（`serial` / `short`，默认 `serial`）与 `reading_positions` 并存；Room v8 起 `chapters` 另有 `toneText` / `idea`（只服务短篇的自由腔调与"这篇想写什么"，连载章节恒为空串）。**所有连载章节查询必须带 `kind = 'serial'` 过滤**，短篇走 `shortStories()`；新增查询漏掉过滤会把短篇串进章节列表。书 = 题材（`genre`），短篇的 `genre` 存腔调名，用户自己定腔调时存 `custom`、原话在 `toneText`。
- `core.tricky`：易错词领域层——`TrickyWord` / `TrickyCard` / `TrickyDetail` 模型、纯函数 `ConfusableSuggestions`（常见混淆对 + 拼写邻居，只推荐不关联）、`TrickyRepository`（唯一写入口，只它碰 Room）。双向关系存一行有序对（`wordA <= wordB`），查询两个方向都看；建立关系时两侧词都会 ensure 进 `tricky_words`，取消标记会删光该词两侧关系。**不要**把易混推荐做成自动匹配或后台任务，用户明确要求点选确认。
- `core.fsrs`：纯 Kotlin 函数，零 Android 依赖，可直接单测。
- 量化红线：单文件 >400 行或单函数 >80 行必须拆分；禁止 Util 垃圾抽屉包（工具放进所属领域包）；同一逻辑复制粘贴出现第 3 次必须抽象；禁止空 catch 与吞异常；ViewModel 禁止持有 Context / View。

## 4. 代码规范

- Kotlin 官方风格；Compose 状态提升，组合阶段无副作用；跨配置变更的界面状态用 rememberSaveable。
- 用户可见字符串一律进 strings.xml（中文），禁止硬编码 UI 文案。**界面少写字**（用户 2026-10-29 定："less is more"）：词卡、按钮、列表只放操作本身需要的字，凡是"解释软件为什么这样记账/排期"的说明一律集中到设置页的「关于与规则」，不要往主界面里加提示行；要让用户知道后果，就把后果做进按钮本身（如评分下面直接显示"3 天"），而不是再写一句话。具体分工：`core/*` 的异常与校验结果只带**技术标记**（`"PUT $name -> HTTP $code"`、`"user_version"`）和枚举，中文一律在 `app/ui/*Messages.kt` 里按枚举映射到资源 id；需要把一段失败说明嵌进另一句话时，把 `UiMessage` 套在参数里——`Context.textFor()` 会递归解析，这样两层文案都留在 strings.xml。
- 协程只用 viewModelScope / lifecycleScope；IO 与网络在 Repository 层切 Dispatchers.IO。
- API key 存 EncryptedSharedPreferences；禁止打印进日志、禁止进任何提交。
- Room schema 变更必须写 Migration 与迁移测试；**禁止 fallbackToDestructiveMigration**（开发期经用户当场同意除外）。
- 跨层错误用 sealed Result / UiState 表达；禁止裸 Exception 穿层到 UI。
- 日志走统一 Logger，release 剥离 debug 级。
- 默认不写注释；仅在"为什么"不明显时写（隐藏约束、绕过特定 bug 的 workaround）。

## 5. 完成定义（DoD）

- core 层新增/修改逻辑（fsrs、解析器、prompt schema、调度器）必须有单元测试；触碰 FSRS 必须跑参考向量测试。
- 编译 + lint 无新增警告；真机或模拟器走通 golden path。
- UI 变更对照 prototype/ 或设计说明自检。
- 跨模块决策产出 `docs/adr/NNNN-标题.md`（背景 / 决定 / 后果，一页内）。
- 提交信息说明"为什么"；不夹带无关改动；除非用户要求，不主动 commit。

## 6. 常用命令

所有 Gradle 命令在仓库根目录执行，`JAVA_HOME` 固定指向 `D:/dev-tools/jdk-21.0.12.1+1`；SDK 在 `D:/dev-tools/android-sdk`（机器级共用，其他项目可复用）。

```powershell
# 单元测试：FSRS 参考向量（纯 JVM 模块）+ app 侧解析/导入/调度/渲染测试
$env:JAVA_HOME='D:\dev-tools\jdk-21.0.12.1+1'; .\gradlew :core:fsrs:test :app:testDebugUnitTest --no-daemon

# 出发布包（只关心 immerlex-arm64-v8a-release.apk；x86_64 那份只给模拟器用）
$env:JAVA_HOME='D:\dev-tools\jdk-21.0.12.1+1'; .\gradlew :app:assembleRelease --no-daemon
# 产物：app\build\outputs\apk\release\immerlex-arm64-v8a-release.apk

# 模拟器（首次需先启用硬件虚拟化并重启；三个脚本都必须带 -ExecutionPolicy Bypass，否则被策略拦下）
powershell -NoProfile -ExecutionPolicy Bypass -File .\scratch\enable-emulator-hypervisor.ps1   # 管理员，然后重启
powershell -NoProfile -ExecutionPolicy Bypass -File .\scratch\start-emulator.ps1               # 终端 A
powershell -NoProfile -ExecutionPolicy Bypass -File .\scratch\install-and-launch.ps1           # 终端 B
```

Room schema 变更必须留迁移测试：`app/schemas/<数据库全限定名>/<N>.json` 由 KSP 导出版本 N 的 schema（`app/build.gradle.kts` 的 `ksp { arg("room.schemaLocation", ...) }`），旧版本号那份要手工补齐（它记录了升级前真实的列集合——**字段列表和 `createSql` 两处都要删**，`createDatabase(name, 1)` 执行的是 `createSql`），`MigrationTestHelper` 靠这两份 JSON 重放迁移。跑法：

```powershell
$env:JAVA_HOME='D:\dev-tools\jdk-21.0.12.1+1'; .\gradlew :app:connectedDebugAndroidTest --no-daemon   # 需模拟器或真机在线
```

这条命令会往**所有在线设备**装包、跑完再卸载（连应用数据一起没）。用户真机（23116PN5BC）常与模拟器同时在线，**不要**用它跑——除非当场确认过设备列表。改用只打模拟器的设备限定跑法（v0.3 迁移测试即由此验收）：

```powershell
.\gradlew :app:assembleDebugAndroidTest --no-daemon
# 产物 app\build\outputs\apk\androidTest\debug\immerlex-debug-androidTest.apk（还需先装好被测 app 的 debug 包）
adb -s emulator-5554 install -r -t app\build\outputs\apk\androidTest\debug\immerlex-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class com.immerlex.core.db.StoryBibleMigrationTest com.immerlex.app.test/androidx.test.runner.AndroidJUnitRunner
```

Git Bash 下 `adb` 的文件路径参数会被 MSYS 改写，先 `export MSYS_NO_PATHCONV=1`。模拟器是 x86_64，装的是 `immerlex-x86_64-debug.apk`，不是 arm64 那份。

定时通知这类"要等任务到点"的东西不必改代码去验：`pm grant com.immerlex.app android.permission.POST_NOTIFICATIONS` 给权限 → `am force-stop`（不先停，退出时会把 shared_prefs 写回）→ `run-as … sed -i` 把 `reminder_minute` 改成两分钟后 → `am start` 即按新时刻重排 → `dumpsys notification --noredact` 看 `channel=daily_review`。`svc power stayon true` 防打盹拖慢；**模拟器时区是 GMT，比本机慢 8 小时**，动手前先 `shell date`。

未启用 Windows Hypervisor Platform 时模拟器会退回纯软件 TCG，本机内存下启动超过 10 分钟，不要用它做验收。

## 7. 工作流程

- 每次会话：读本文件 → TaskList 看里程碑进度 → 一句话声明本次目标 → 动手。
- 按里程碑 M0–M5 推进（验收标准见任务列表描述）；跨里程碑改动先经用户确认。
- 文件与代码现实冲突时：改代码或改本文件，二选一且当次会话完成，禁止两者皆错地留着。
- 除 docs/ 外不写规划/分析 md；临时产物放 scratch/（已 gitignore）。

## 8. 里程碑

M0 环境与骨架 → M1 词库层 → M2 FSRS 调度 → M3 AI 生成 → M4 阅读层（均已在 2026-10-28 真机验收，唯 txt / 截图导入未跑）→ M5 收尾分三刀：**v0.2** 复习计分正确性 + 章节回看/重写/删章（已交付）→ **v0.3** prompt 层：默认密度与篇幅、故事圣经、人物状态、全书梗概（已交付，2026-10-28 模拟器验收，Room v2→v3 迁移在带真实数据的旧库上实测过）→ **v1.0** 今日任务页 + 通知 + 坚果云自动备份（已交付，2026-10-28 模拟器验收：`DailyLoopMigrationTest` 覆盖 v3→v4 且模拟器上的既有库原地升到了 v4、提醒通知实弹弹出、备份与恢复的校验闸门实测会拒绝坏文件与未配置；**尚未在用户真机上走过**，坚果云真实账号也未实测）→ **v1.1** PDF 导入 + 书架/阅读位置 + 热力图 + 短篇模式（已交付，2026-10-29 模拟器验收：`BookshelfMigrationTest` 覆盖 v4→v5、PDF 端到端导入 3 词、书架切书与杀进程重开仍回原书、短篇入口与 prompt/校验单测；**短篇真实正文尚未生成**，因为模拟器没有 API key）→ **v1.2** 统计趋势图 + 词库 CSV 导出 + 阅读页只留英文（已实现，2026-10-29 模拟器自检：30 天柱状图在带复习记录的库上渲染、CSV 经 SAF 保存到 Downloads 并核对 15 行转义、只留英文与学习模式逐词同位且点词弹卡；**待真机验收**，versionName 1.2.0 / versionCode 7）→ **v1.3** 独立测验 + 统一 learn/recall 记账 + 提前复习/再来一组（已实现，2026-10-29 模拟器验收：Room v6→v7 迁移（旧 82 条记录落到 `kind='recall'`）、到期页入口按词门禁（全生词时"今天没有要测的词"，学过 3 个词后按钮变"开始测验（3 词）"）、四题型出题与即时反馈含全部选项释义、答错换题型重练不写记录且不进得分、测验记录带 `questionType`、阅读 learn/recall 拆分与 8 秒撤销（撤销删最后一条并复原评分前快照）、提前复习一组后"再来一组"；最后一题有重练可看时按钮写"下一题"而不是"完成"；复习模式未到期词的「想起来了」实测不写记录、「没想起来」写一条 `recall` + `AGAIN`（stability 4.13→0.74、到期日从 11-06 提前到次日、lapses+1，与卡上预告的"1 天"一致）且几秒撤销能把快照与"本次已回忆"计数一起还原，学习模式同一词仍只显示释义不计分；**待真机验收**，versionName 1.3.0 / versionCode 8）→ **v1.4** 用户真机截图指出的六条（已实现，2026-10-29 模拟器自检：学习模式 `reluctant（不情愿的）` 释义紧贴不再被甩到下一行、复习模式只剩单词无 `（　）` 占位、词库/阅读/设置/测验/关于五页标题与状态栏间距与今日页一致、写短篇对话框可选「这篇想写什么？」+「自己定腔调」（选"自己定"才出现第二个输入框，四个 chip 用 FlowRow 换行不再被挤成竖条）、书架整页"正在读"大卡 + 题材色方块（科幻蓝 / 宫斗酱红 / 灯下的信绿，深浅色都复查过）、长按短篇进多选态出现勾选与"删除 N 篇"、桌面图标换到第三版（思源宋体「词」单字矢量前景 + 纸底竹青，横条与铁锈橙被否掉）并在启动器 dock 与抽屉里核验 48dp 仍可辨认；`ShortPlanMigrationTest` 覆盖 v7→v8 与 v1→v8 链；**待真机验收**，versionName 1.4.0 / versionCode 9）。v1.5 剩余：导入嵌词。每刀单独真机验收，不许合并跳级。

## 9. 术语表

- 嵌词段：章节 JSON 中的结构化段，正文 `{k:"t",v:"中文…"}` 与词条 `{k:"w",id,en,zh,ipa,st,def,ex}` 交替，渲染为高亮；schema 见 `core.ai.ChapterDoc`，与 `prototype/reading.html` 一致。
- 本章变化（`changes`）：章节 JSON 里模型回报的 `[{who,what}]`——本章里谁的处境变了；`who` 或 `what` 为空的一条直接丢弃，合并进人物状态表。
- 人物状态表：按题材一张表，一人一行（`(genre,name)` 唯一），记当前角色与处境、首次/最近出现的章号、冲突标记。名字写法不同（「落薇」/「落薇 · 皇后」）按规范化折成同一人；重写旧章不会把新章之后的状态往回改。
- 学习模式 / 复习模式：释义可见 / 释义隐藏（点击揭示或挖空产出）。学习模式只负责第一次 learn 与阅读，**不记 recall**；复习模式揭示后打分才是一次独立回忆。复习模式里未到期只有「想起来了」（不记）与「没想起来」（记一次 recall）两个按钮，因为判断发生在点开之前。
- learn / recall：`review_logs.kind` 的两种事件。learn = 第一次接触、答案可见时写，只开始排期；recall = 先藏答案先作答再反馈，阅读复习/测验/提前复习都记。看释义、查词、答错后的当堂重练都不记。见 `docs/adr/0012`。
- 题型：英→中（`en_zh`）/ 中→英（`zh_en`）/ 句子挖空（`cloze`）/ 英文释义选词（`definition`）。题型只决定怎么考，不另建排期；门禁与选择见 `core.quiz.chooseQuizType`。
- 提前复习 / 再来一组：当天碰过、还没到期的 5 个最不稳定词；照常记 recall，一组做完可再来一组，没有每日上限。
- 到期词：FSRS 计算今日应复习的词。
- 故事圣经：按题材存在的三层上下文——用户口述展开出的设定条目（基调/主角/结局倾向/禁止事项）、系统从每章"本章变化"合并出的人物状态表、以及本章全书逐章一行梗概（超过约 60 章才折叠成分卷摘要）。取代原先"只有最近 3 章梗概 + 被覆盖的 synopsis"的做法。取舍见 `docs/adr/0005-story-bible-from-one-prompt.md`；早期角色表 12 条上限与 recap 窗口见 `docs/adr/0002-chapter-continuity.md`（已被 0005 部分修订）。
- 打卡 / 补卡：当天学过（learn）或回忆过（recall）任意 1 个词即算打卡；漏一天不归零，次日清空当日到期词自动补上，每周最多 1 次。见 `docs/adr/0006`、`docs/adr/0012`。
- 章 / 词的单位：章是阅读单位（进度、回看、重写、删除），词是调度单位（到期、计分、复现）。见 `docs/adr/0004-chapter-word-units.md`。
- 书架 / 阅读位置：书 = 题材；`reading_positions` 记上次在看的书与章（连载 `book:<题材>`、短篇 `short:<章 id>`），重开应用回到同一本同一章。见 `docs/adr/0007`。
- 短篇：单篇自足、不参与连载连贯层，章号 0、腔调存 `genre`，篇幅由词数决定（15 词 → 1500–2500 字）。见 `docs/adr/0007`。
- 热力图：今日页 13 周网格，颜色按"当天不重复词数"计，固定阈值 0 / 1–4 / 5–9 / 10–19 / 20+。
- 趋势：今日页近 30 天柱状图，同样按"当天不重复词数"计，窗口固定含今天。
- 只留英文：阅读页把中文（含释义槽）画透明的自测视图；版式不动、英文原位可点，不改变学习/复习模式。
- 易错词：用户手动标记的词（`tricky_words`），释义自动取词库→ECDICT；不是错题记录，不改 FSRS 状态。
- 易混：易错词之间的关系，双向可见（affect ↔ effect），存一行有序对；只能由用户点推荐 chip 或手填英文词建立，系统绝不自动关联。见 `docs/adr/0011`。
- grade：复习打分（忘了 / 困难 / 记得 / 简单），FSRS 输入；行内暴露记低权重 grade。
