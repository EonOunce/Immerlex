# Immerlex 工作指导手册

> **强制阅读**：任何 AI 会话在本仓库开始工作前，必须完整读取本文件。
> 若本文件与用户最新对话冲突，以用户最新决定为准，并在当次会话内把该决定回写本文件或 `docs/adr/`。

## 1. 产品是什么

Immerlex 是一个"把英文单词融入中文小说"的安卓应用：
词库（截图 OCR / txt / CSV 导入，ECDICT 补全释义）→ FSRS 间隔调度 → AI 按题材生成中文章节并按密度行内嵌入"新词 + 到期词"→ 双阅读模式（学习：释义可见 / 复习：释义隐藏、点击揭示或挖空）→ grade 回写 FSRS → 统计趋势。

- v1 范围：词库导入 + FSRS 调度 + AI 生成嵌词 + 双阅读模式。
- v1.5：独立测验会话、统计趋势图、导入嵌词（AI 建议插入点 → 用户确认）、CSV/Anki 导出。

## 2. 已定决策（推翻需用户明确同意）

| 项 | 决策 |
|---|---|
| 技术栈 | Kotlin + Jetpack Compose + Room，离线优先；非密钥设置用 SharedPreferences（`core.settings.AppPreferences`），API key 用 EncryptedSharedPreferences（`core.ai.ApiKeyStore`）——因密钥本就必须走 EncryptedSharedPreferences，两套存储并存比引入 DataStore 更简单；字号/深色走 `AppPreferences.appearance` StateFlow（改存即生效，不等"保存"按钮），主题与阅读页从 `LocalAppearance` / `LocalMasteryTones` 读 |
| 调度算法 | FSRS-6（open-spaced-repetition 当前版本，21 参数），移植参照 py-fsrs scheduler.py；单测必须与参考测试向量一致（向量由 py-fsrs 生成：无学习/重学步、fuzz 关闭、上限 3650 天、DR=0.9）。用户 2026-10-27 确认升级 FSRS-6，见 `docs/adr/0001-fsrs-6-version.md` |
| 词典 | ECDICT（skywind3000/ECDICT）sqlite 精简版，asset 或按需下载 |
| 截图 OCR | ML Kit `text-recognition-chinese` bundled 模型（`core.ocr.MlKitOcr`），离线可用；代价是 debug APK 由 15MB 涨到约 59MB。**待用户确认**：是否换成 play-services 版（包小但需 Google Play）或按需下载模型 |
| 发布包 | 只交付 `app/build/outputs/apk/release/immerlex-arm64-v8a-release.apk`（用户 2026-10-27 指定）；ABI 切分为 arm64-v8a + x86_64（后者只服务模拟器），不出 universal APK；release 用 `keystore.properties` 指向的 `D:/dev-tools/immerlex-release.jks` 签名，该文件与 jks 均不进版本库；R8 保持关闭（未经设备验证不混淆） |
| AI 接入 | OpenAI 兼容协议；baseURL / key / model 由用户首启手动配置；代码与仓库中禁止硬编码 key |
| 语言对 | 仅中文小说嵌英文词 |
| 嵌词方式 | v1 生成嵌词；导入嵌词 v1.5 再做 |
| 密度 | 每千汉字三档：疏 10–15 / 中 20–30 / 密 35–50，默认密；每日新词上限默认 20–30；嵌词槽位 = 新词（带上下文线索）+ 到期词复现（无需线索）混排；词库不足时自动降档 |
| prototype/ | 一次性交互原型（仅阅读页视觉/交互规格），非产品代码，禁止进入 app source set |

## 3. 架构边界（防屎山条款）

- 单向数据流：Compose UI → ViewModel(State/Event) → Repository → core 能力（db / ai / fsrs / dict / ocr）。
- 按 feature 分包：`core/*` 提供能力，`feature/*` 提供界面；**feature 之间禁止横向依赖**，共享逻辑一律下沉 core。v1 实到 `feature/wordbank`、`feature/reader`、`feature/settings`；生成入口放在 reader 页头（写下一章）与设置页，因为它只是 `core.ai.ChapterGenerator` 的一次调用，不值得单独成包——`feature/generate`（导入嵌词建议）与 `feature/home`（今日任务）随 v1.5 再建。跨 feature 共用的呈现逻辑放 `app/ui/`（如 `GenerateOutcome.toUiMessage()`）。
- Room 是唯一业务持久化入口；禁止用 SharedPreferences 散存业务状态。
- `core.ai`：prompt 模板为带版本常量（PROMPT_V1…）；AI 输出必须通过 kotlinx-serialization schema 校验后才能入库；解析失败走修复重试（最多 2 次），仍失败则向用户明示错误，禁止静默丢弃。
- `core.fsrs`：纯 Kotlin 函数，零 Android 依赖，可直接单测。
- 量化红线：单文件 >400 行或单函数 >80 行必须拆分；禁止 Util 垃圾抽屉包（工具放进所属领域包）；同一逻辑复制粘贴出现第 3 次必须抽象；禁止空 catch 与吞异常；ViewModel 禁止持有 Context / View。

## 4. 代码规范

- Kotlin 官方风格；Compose 状态提升，组合阶段无副作用；跨配置变更的界面状态用 rememberSaveable。
- 用户可见字符串一律进 strings.xml（中文），禁止硬编码 UI 文案。
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

# 模拟器（首次需先启用硬件虚拟化并重启）
powershell -NoProfile -ExecutionPolicy Bypass -File .\scratch\enable-emulator-hypervisor.ps1   # 管理员，然后重启
powershell -NoProfile -File .\scratch\start-emulator.ps1                                        # 终端 A
powershell -NoProfile -File .\scratch\install-and-launch.ps1                                    # 终端 B
```

未启用 Windows Hypervisor Platform 时模拟器会退回纯软件 TCG，本机内存下启动超过 10 分钟，不要用它做验收。

## 7. 工作流程

- 每次会话：读本文件 → TaskList 看里程碑进度 → 一句话声明本次目标 → 动手。
- 按里程碑 M0–M5 推进（验收标准见任务列表描述）；跨里程碑改动先经用户确认。
- 文件与代码现实冲突时：改代码或改本文件，二选一且当次会话完成，禁止两者皆错地留着。
- 除 docs/ 外不写规划/分析 md；临时产物放 scratch/（已 gitignore）。

## 8. 里程碑

M0 环境与骨架 → M1 词库层 → M2 FSRS 调度 → M3 AI 生成 → M4 阅读层 → M5 收尾；其后 v1.5。

## 9. 术语表

- 嵌词段：章节 JSON 中的结构化段，正文 `{k:"t",v:"中文…"}` 与词条 `{k:"w",id,en,zh,ipa,st,def,ex}` 交替，渲染为高亮；schema 见 `core.ai.ChapterDoc`，与 `prototype/reading.html` 一致。
- 学习模式 / 复习模式：释义可见 / 释义隐藏（点击揭示或挖空产出）。
- 到期词：FSRS 计算今日应复习的词。
- 故事档案：`story_bibles` 表按题材存的角色表（`charactersJson`，上限 12，新角色优先）与最新梗概；生成时再带上同题材最近 3 章的章节梗概（`chapters.summary`，由旧到新）进 prompt，续写而非重启。取舍见 `docs/adr/0002-chapter-continuity.md`。
- grade：复习打分（忘了 / 困难 / 记得 / 简单），FSRS 输入；行内暴露记低权重 grade。
