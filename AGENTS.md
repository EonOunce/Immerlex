# Immerlex 工作指导手册

> **强制阅读**：任何 AI 会话在本仓库开始工作前，必须完整读取本文件。
> 若本文件与用户最新对话冲突，以用户最新决定为准，并在当次会话内把该决定回写本文件或 `docs/adr/`。

## 1. 产品是什么

Immerlex 是一个"把英文单词融入中文小说"的安卓应用：
词库（截图 OCR / txt / CSV 导入，ECDICT 补全释义）→ FSRS 间隔调度 → AI 按题材生成中文章节并按密度行内嵌入"新词 + 到期词"→ 双阅读模式（学习：释义可见 / 复习：释义隐藏、点击揭示或挖空）→ grade 回写 FSRS → 统计趋势。

- v1 范围：词库导入 + FSRS 调度 + AI 生成嵌词 + 双阅读模式 + 章节回看与重写 + 故事圣经 + 今日任务页与通知与坚果云备份（v1.0 收尾）。
- v1.5：**短篇模式（首位）**、独立测验会话、统计趋势图、导入嵌词（AI 建议插入点 → 用户确认）、CSV/Anki 导出。

## 2. 已定决策（推翻需用户明确同意）

| 项 | 决策 |
|---|---|
| 技术栈 | Kotlin + Jetpack Compose + Room，离线优先；非密钥设置用 SharedPreferences（`core.settings.AppPreferences`），API key 用 EncryptedSharedPreferences（`core.ai.ApiKeyStore`）——因密钥本就必须走 EncryptedSharedPreferences，两套存储并存比引入 DataStore 更简单；字号/深色走 `AppPreferences.appearance` StateFlow（改存即生效，不等"保存"按钮），主题与阅读页从 `LocalAppearance` / `LocalMasteryTones` 读 |
| 调度算法 | FSRS-6（open-spaced-repetition 当前版本，21 参数），移植参照 py-fsrs scheduler.py；单测必须与参考测试向量一致（向量由 py-fsrs 生成：无学习/重学步、fuzz 关闭、上限 3650 天、DR=0.9）。用户 2026-10-27 确认升级 FSRS-6，见 `docs/adr/0001-fsrs-6-version.md` |
| 词典 | ECDICT（skywind3000/ECDICT）sqlite 精简版，asset 或按需下载 |
| 截图 OCR | ML Kit `text-recognition-chinese` bundled 模型（`core.ocr.MlKitOcr`），离线可用；arm64 release APK 实测 25.49MB，其中离线中文 OCR 模型占约 11.5MB。用户 2026-10-28 确认**保留 bundled**：主词源是微信/相册截图，必须在无 Google Play 服务的国产 ROM 上离线可用，包体积代价接受 |
| 发布包 | 只交付 `app/build/outputs/apk/release/immerlex-arm64-v8a-release.apk`（用户 2026-10-27 指定）；ABI 切分为 arm64-v8a + x86_64（后者只服务模拟器），不出 universal APK；release 用 `keystore.properties` 指向的 `D:/dev-tools/immerlex-release.jks` 签名，该文件与 jks 均不进版本库；R8 保持关闭（未经设备验证不混淆） |
| AI 接入 | OpenAI 兼容协议；baseURL / key / model 由用户首启手动配置；代码与仓库中禁止硬编码 key |
| 语言对 | 仅中文小说嵌英文词 |
| 嵌词方式 | v1 生成嵌词；导入嵌词 v1.5 再做 |
| 密度 | 每千汉字三档：疏 10–15 / 中 20–30 / 密 35–50，**默认中**（用户 2026-10-28 改，原先默认密）；每日新词上限默认 25；目标篇幅 **千字左右 / 每章 20–30 词**（实测：密度调高到 35–50 时，千字里塞 35+ 词会让模型写成砸词的生硬文本，句子质量优先于密度）；嵌词槽位 = 新词（带上下文线索）+ 到期词复现（无需线索）混排；词库不足时自动降档，**降档静默即可**，不在 UI 里提示（用户 2026-10-28 确认） |
| 章 / 词的单位 | **章是阅读单位，词是调度单位**（用户 2026-10-28 定，见 `docs/adr/0004`）。同一个词在多章复现时，一次打分即清掉这次到期；删章不回滚复习进度；生成下一章不得让旧章无法继续阅读与打分，因此阅读页必须能在本章各章间切换（标题即入口），并提供"重写本章（同词重生成，编号不变）"与"删章" |
| 复习计分 | 未到期词只揭示释义、不计分；到期词打分算一次复习；同日同一词封顶 2 条记录（"不认识"→"学回"算 2 次是合法重学，第三次点击一律**改**最后一条而非新增），实现依赖 `review_logs` 记录评分前的卡片快照以便回滚重算 |
| 备份 | 坚果云 WebDAV 自动备份（用户 2026-10-28 选定）：快照→gzip→上传，**禁止**把正在写入的 SQLite 文件直接交同步通道；WebDAV 无版本历史，故按日期命名并在云端保留最近 30 份 + 每月最后一份；备份**明文、不加密**；AI key 不进备份，恢复完需重填 baseURL/key/model。流量测算：重度使用一年约 1.5MB/份，每日一份约 45MB/月，远低于坚果云免费版 WebDAV 的 1GB/月上行 |
| 通知 | 每日到期提醒（默认 20:30，可改可关）+ 生成超过 10 秒挂进行中通知、完成转"第 N 章已写好"；文案只说真实状态（"有 N 个词到期"），在章节状态存在前不得写"该读第三章了" |
| 今日任务首页 | 底部第四栏 `feature/home`（原计划 v1.5，用户 2026-10-28 提前到 v1.0）：今日到期/已完成、连续天数、一个"开始今日阅读"入口。打卡门槛低——**当天给任意 1 个词打过分即算**；漏一天不归零，次日清空当日到期词即自动补上，每周最多补 1 次；**不做积分/兑换**（外部奖励会给"制造复习次数"的动机，与我们刚定的防刷规则互相拆台） |
| 日常化落地（v1.0 已交付 2026-10-28） | 今日页的到期数**不另算**，取 `core.schedule.ScheduleService.todaysQueue()` 的 `reviewWords + newWords`，与生成下一章喂给模型的是同一份词表；连续天数与补卡是纯函数 `core.schedule.dailyInfoOf(DailyLoopInput)`，Room v4 新增 `daily_loop`（按 `epochDay` 一行）与 `backup_runs`。提醒用**自排程的一次性 unique work**（WorkManager 周期任务只有"每 24 小时"会漂到凌晨），`secondsUntilNext` 纯函数，worker 在 `finally` 里排明天、应用启动再重排，所以改时刻立即生效；当日 0 个到期就不弹，`last_reminded_day` 保证一天最多一条。备份固定为 `VACUUM INTO` 快照 → gzip → PUT，保留策略是纯函数 `core.backup.BackupPlan`；恢复先校验（gzip 头 / `user_version` / `integrity_check` / 必备表），不过就**逐项明说且不动本机库**，过了才换文件并强制"重开应用"。坚果云应用密码与 AI key 同存 EncryptedSharedPreferences，地址预填 `https://dav.jianguoyun.com/dav/`。详见 `docs/adr/0006` |
| 故事圣经 | 长篇的跨章连贯由三层承担（见 `docs/adr/0005`）：① 开局由用户口述一段想法，**一次调用**展开成结构化故事圣经（基调/主角与目标/结局倾向/禁止事项/起始人物），逐条可编辑可重掷；② 生成契约要求模型回报**本章变化**（谁的位置、关系、承诺或信息发生变化），系统合并进人物状态表；③ 本章全书逐章一行梗概全量入 prompt，超过约 60 章才折叠成分卷摘要。用户写过的条目优先级最高，冲突时不覆盖而标提示。**绝不**做聊天式多轮开局界面：入口只到"一段话 → 一份圣经"为止，否则等于为输入再造一个 app |
| 故事圣经落地（v0.3 已交付 2026-10-28） | 三层各一张表，Room v3 起 `bible_entries`（题材分区条目 + `authoredByUser`）与 `character_states`（题材内 `(genre,name)` 唯一）；合并在纯函数 `core.bible.CastMerger`（两条铁律：用户手写状态只标冲突不覆盖、旧章不能把新章之后的人物状态往回改）。展开调用是 `core.ai.BibleExpander`，schema 校验、≤2 次修复、超限即裁剪（条目 160 字 / 禁止事项 8 条 / 起始人物 12 条）。prompt 升到 **PROMPT_V3**：档案 → 人物当前状态 → 全书梗概（由旧到新）三层全量入 prompt，用户条目后挂「（读者设定，务必遵守）」，冲突写「注意：第 N 章把它写成了…」。入口在设置页「查看并编辑故事档案」，**没配 API key 也能逐条手填/删除**——AI 只是把一段话展开成初稿，不是使用档案的前提 |
| 短篇模式 | 意林/花火/科幻世界式单篇自足短篇为 **v1.5 第一项**：不依赖跨章连贯层，字数由词数决定（15 词 → 1500–2500 字），"像某杂志"只做**腔调预设**（小人物点题 / 情绪华丽 / 设定先行冷结尾）而不复制其样文 |
| 版本切分 | 三刀：**v0.2** 复习计分正确性 + 章节回看/重写/删章（已交付，2026-10-28 模拟器验收）、**v0.3** prompt 层（默认密度与篇幅、故事圣经、人物状态、全书梗概，已交付，2026-10-28 模拟器验收，versionName 0.3.0 / versionCode 4）、**v1.0** 今日任务页 + 通知 + 坚果云自动备份（已交付，2026-10-28 模拟器验收，versionName 1.0.0 / versionCode 5）。每刀单独真机验收 |
| prototype/ | 一次性交互原型（仅阅读页视觉/交互规格），非产品代码，禁止进入 app source set |

## 3. 架构边界（防屎山条款）

- 单向数据流：Compose UI → ViewModel(State/Event) → Repository → core 能力（db / ai / fsrs / dict / ocr）。
- 按 feature 分包：`core/*` 提供能力，`feature/*` 提供界面；**feature 之间禁止横向依赖**，共享逻辑一律下沉 core。v1 实到 `feature/wordbank`、`feature/reader`、`feature/settings`、`feature/home`（v1.0 起的底部第四栏；故事档案面板 `BibleScreen` / `BibleViewModel` 住在 settings 里——它是设置页的一个子页，不值得单独成包）；生成入口放在 reader 页头（写下一章）与设置页，因为它只是 `core.ai.ChapterGenerator` 的一次调用，不值得单独成包；`feature/generate`（导入嵌词建议）随 v1.5 再建。跨 feature 共用的呈现逻辑放 `app/ui/`（如 `GenerateOutcome.toUiMessage()`、`BackupOutcome.toUiMessage()`）。
- Room 是唯一业务持久化入口；禁止用 SharedPreferences 散存业务状态。
- `core.bible`：故事圣经的领域层——`BibleSection` 枚举与 `BibleDraft` 等模型、`StoryContext` / `StoryContextSource`（生成侧只看这三层）、纯函数 `CastMerger`（合并规则可单测）、`StoryBibleRepository`（唯一写入口，只它碰 Room）。依赖方向单向：`core.ai` → `core.bible`，禁止反向。
- `core.backup`：`DbSnapshot`（`VACUUM INTO` 出一致性快照 + 校验，返回 `SnapshotProblem` 列表而不是布尔）、`WebDavClient`（只做 HTTP，异常带 `BackupFailure` 枚举）、`BackupPlan`（保留策略纯函数）、`BackupService`（编排 + 写 `backup_runs`）。`core.notify`：`ReminderScheduler`（排程，纯函数 `secondsUntilNext`）、`DailyReminderWorker`（只认 `ReminderHost` 接口，不认识 `AppContainer`）、`Notifier`（渠道与文案投递）。二者对外暴露的都是**能力对象**（`ReminderControl` / `BackupService`），`Context` 封装在对象内部，ViewModel 只注入能力对象——这是"ViewModel 禁止持有 Context"的具体实现方式。
- 旧列迁移后**保留但清空**（`story_bibles.charactersJson` / `synopsis`，被 `bible_entries` + `character_states` 取代）：`DROP COLUMN` 在 minSdk 26 上要 SQLite 3.35+，机型不稳；一次性回填（旧人物表与旧梗概搬进新表）放在 `Mutex` 后面，防并发改表把人写乱。
- `core.ai`：prompt 模板为带版本常量（当前 `PromptBuilder.VERSION = 3`：三层上下文 + 章节 JSON 里回报 `changes`；档案展开另带 `BIBLE_SCHEMA`）；AI 输出必须通过 kotlinx-serialization schema 校验后才能入库；解析失败走修复重试（最多 2 次），仍失败则向用户明示错误，禁止静默丢弃。缺 `changes` 键的旧 V2 章节行必须照常能读。
- `core.fsrs`：纯 Kotlin 函数，零 Android 依赖，可直接单测。
- 量化红线：单文件 >400 行或单函数 >80 行必须拆分；禁止 Util 垃圾抽屉包（工具放进所属领域包）；同一逻辑复制粘贴出现第 3 次必须抽象；禁止空 catch 与吞异常；ViewModel 禁止持有 Context / View。

## 4. 代码规范

- Kotlin 官方风格；Compose 状态提升，组合阶段无副作用；跨配置变更的界面状态用 rememberSaveable。
- 用户可见字符串一律进 strings.xml（中文），禁止硬编码 UI 文案。具体分工：`core/*` 的异常与校验结果只带**技术标记**（`"PUT $name -> HTTP $code"`、`"user_version"`）和枚举，中文一律在 `app/ui/*Messages.kt` 里按枚举映射到资源 id；需要把一段失败说明嵌进另一句话时，把 `UiMessage` 套在参数里——`Context.textFor()` 会递归解析，这样两层文案都留在 strings.xml。
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

M0 环境与骨架 → M1 词库层 → M2 FSRS 调度 → M3 AI 生成 → M4 阅读层（均已在 2026-10-28 真机验收，唯 txt / 截图导入未跑）→ M5 收尾分三刀：**v0.2** 复习计分正确性 + 章节回看/重写/删章（已交付）→ **v0.3** prompt 层：默认密度与篇幅、故事圣经、人物状态、全书梗概（已交付，2026-10-28 模拟器验收，Room v2→v3 迁移在带真实数据的旧库上实测过）→ **v1.0** 今日任务页 + 通知 + 坚果云自动备份（已交付，2026-10-28 模拟器验收：`DailyLoopMigrationTest` 覆盖 v3→v4 且模拟器上的既有库原地升到了 v4、提醒通知实弹弹出、备份与恢复的校验闸门实测会拒绝坏文件与未配置；**尚未在用户真机上走过**，坚果云真实账号也未实测）。下一刀 v1.5 以**短篇模式**开头。每刀单独真机验收，不许合并跳级。

## 9. 术语表

- 嵌词段：章节 JSON 中的结构化段，正文 `{k:"t",v:"中文…"}` 与词条 `{k:"w",id,en,zh,ipa,st,def,ex}` 交替，渲染为高亮；schema 见 `core.ai.ChapterDoc`，与 `prototype/reading.html` 一致。
- 本章变化（`changes`）：章节 JSON 里模型回报的 `[{who,what}]`——本章里谁的处境变了；`who` 或 `what` 为空的一条直接丢弃，合并进人物状态表。
- 人物状态表：按题材一张表，一人一行（`(genre,name)` 唯一），记当前角色与处境、首次/最近出现的章号、冲突标记。名字写法不同（「落薇」/「落薇 · 皇后」）按规范化折成同一人；重写旧章不会把新章之后的状态往回改。
- 学习模式 / 复习模式：释义可见 / 释义隐藏（点击揭示或挖空产出）。
- 到期词：FSRS 计算今日应复习的词。
- 故事圣经：按题材存在的三层上下文——用户口述展开出的设定条目（基调/主角/结局倾向/禁止事项）、系统从每章"本章变化"合并出的人物状态表、以及本章全书逐章一行梗概（超过约 60 章才折叠成分卷摘要）。取代原先"只有最近 3 章梗概 + 被覆盖的 synopsis"的做法。取舍见 `docs/adr/0005-story-bible-from-one-prompt.md`；早期角色表 12 条上限与 recap 窗口见 `docs/adr/0002-chapter-continuity.md`（已被 0005 部分修订）。
- 打卡 / 补卡：当天给任意 1 个词打过分即算打卡；漏一天不归零，次日清空当日到期词自动补上，每周最多 1 次。见 `docs/adr/0006`。
- 章 / 词的单位：章是阅读单位（进度、回看、重写、删除），词是调度单位（到期、计分、复现）。见 `docs/adr/0004-chapter-word-units.md`。
- grade：复习打分（忘了 / 困难 / 记得 / 简单），FSRS 输入；行内暴露记低权重 grade。
