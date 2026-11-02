# 0026 素材包清单与 app 内取包（v3.4）

## 背景

出题素材包（ADR 0022 / 0024）一直是"用户在电脑上拿到 JSON、SAF 选文件、导进来"。
考研全量词按 A–Z 分成几十片、每片 100–200 词，这条路每次都要用户手动搬十几份文件，
而且搬错了、漏了一片，本机看不出来。

用户 2026-11-02 定了两件事：**app 里加「从 GitHub 取素材包」**，以及
**本地导入必须保留**（GitHub 拉不动的时候它是唯一退路）。同时明确**素材包等 A–Z
全部齐了再发**——半套发出去，以后加词就成了麻烦。

## 决定

1. **一份清单 + 若干分片，全放同一个 release 目录**
   （`app/src/main/java/com/immerlex/core/material/MaterialManifest.kt`）：

   ```json
   {
     "materialVersion": 1,
     "generatedAt": "2026-11-02",
     "license": "All rights reserved",
     "packs": [
       {"file": "material-pack-01.json", "words": 100, "sha256": "<64 位小写十六进制>"}
     ]
   }
   ```

   - `file` 必须是**裸文件名**：含 `/` `\` `..` 或超过 120 字符直接判读不懂。分片和
     清单同源同目录，允许清单写路径就等于允许清单把人往别处指。
   - `sha256` 必须是 64 位十六进制，读到就折成小写（大写的包发布方照样能用）。
   - `words` 缺省按 0，只用于给用户报"这套一共多少词"，**不参与任何判断**：本机词数
     永远以库里的真实行数为准（`MaterialRepository.count()`），两边不会各说各的。
   - `generatedAt` / `license` 是说明性字段，缺了不影响取包。
   - `materialVersion` 是**格式**版本，不是词的版本。比 `MAX_SUPPORTED_VERSION` 新的
     清单整份拒绝——不要按半套字段猜。

2. **分片按相对地址解析**：取包时把 `file` 拼在清单所在目录下面
   （`MaterialSync` 用 `URI.resolve`）。所以换镜像、换自己的服务器、换成本机 http
   服务，**只改设置里那一个地址**，一整套照样取得到。默认地址是
   `https://github.com/EonOunce/Immerlex/releases/latest/download/manifest.json`
   （`AppPreferences.PRESET_MATERIAL_URL`；`/releases/latest/download/` 自动指向最新
   release，发新包不用改 app）。

3. **校验值不过就不进库**（`MaterialSync.fetchAll`）：一片一片来——下载 → SHA-256
   比对 → `MaterialRepository.import()`（一片一个事务）→ 下一片。校验值对不上直接
   停在这片，已经进库的前面几片**照留**，不回滚、不重试。素材合并是 fill-only
   （ADR 0022 第 5 条），所以"重取一遍"永远安全：同一片只会把同样的字段再写一遍，
   不会把上一片已经写好的东西顶掉，用户看到失败再点一次就是接着往下走。

4. **失败必须说清是哪一类**（`MaterialSyncFailure` 十个机器码 → `strings.xml` 中文）：
   没配地址 / 地址不是 URL / 连不上 / 超时 / TLS / HTTP 状态码 / 清单读不懂 /
   校验值不符 / 这一片被库拒了 / 本机写坏。**网络错和本机错分开**，不能都写成
   "下载失败"让用户以为是自己网不好；`LOCAL` 只给真正落在本机异常路径上的情况。
   跨层只用 sealed `MaterialSyncOutcome`，枚举 + `detail` 穿到 UI 再映射文案，
   与备份那套（`BackupFailure`）同一条规矩。

5. **进度按片报**（`MaterialSyncStep(index, total, file)` → 「正在取第 N/M 片」）：
   十几片、每片几十 KB，用户等得起，但不能等着等着界面什么都不说。取包用独立的
   `fetching` 标志，不与"正在读这个包"的 `busy` 混用——按钮文案不能自相矛盾。

6. **取包只往下读，不往上写**：这条路径只有 `GET`，不上传任何本机数据。
   与坚果云备份（会上传）在隐私说明里分开写。

7. **本地导入原样保留**（用户明确要求）：SAF 选文件走的是同一个
   `MaterialRepository.import()`、同一套校验闸门。GitHub 取不到时它就是退路。

## 后果

- **已发布（2026-11-02）**：素材包 v1 进了 `v3.4.0` release——`manifest.json` + 50 片分片、
  4,699 个词（`materialVersion 1`、`license: all-rights-reserved`），清单地址与应用默认值一致，
  逐片校验值已与服务端算出的摘要核对过。覆盖 ECDICT 考研标签词的 4,699/4,799，缺的 100 个
  是最日常的高频词。
- **扩充（2026-11-02 晚）**：27 片新分片（第 51–78 片、2,462 条）直接补进 `v3.5.0` release——
  先传分片、最后删掉旧清单再传新清单，线上 `releases/latest/download/manifest.json` 与本地
  逐字节一致，77 片的服务端摘要全部对得上。现共 **77 片、7,161 条 / 6,724 个词面**；其中 370 个
  词与旧片重名，导入按清单顺序覆盖（fill-only），所以后补的那一版生效。
- 发布素材包时要做的事：每片算一次 `sha256sum`，把文件名、词数、校验值各写一行进
  `manifest.json`，**和分片一起**传到同一个 release。往后新增词**只加一片新的分片 +
  清单里追加一行**，已有分片一个字节都不用重发——这正是"等齐了再发"之后仍然能小步
  加词的办法。清单由 `scratch/make_manifest.py` 生成（只算校验值与词数，不改分片内容）。
- **默认地址指向 `releases/latest`，所以此后每一个新 release 都必须把 `manifest.json` 与
  当时全部分片（现 77 片）一起带上**（直接 `gh release create --assets` 复制过去即可），否则
  新 release 一变成 latest，app 的默认地址就 404——上线前在设置页点一次取包，看到
  「正在取第 77/77 片」才算出完。
- 素材包仍然**不打进 APK、不进版本库**（ADR 0022）。
- 清单格式改动要升 `materialVersion`，同时把 app 的 `MAX_SUPPORTED_VERSION` 抬上去；
  老 app 遇到新清单是拒绝而不是猜，所以加字段（不改语义）可以先发，改语义必须升版本。
- 相关测试：`MaterialManifestTest`（格式、路径注入、版本、可选字段、SHA-256 已知向量）、
  `MaterialSyncTest`（按顺序取完、校验值不符不入库、清单坏了什么都不进、被拒的那片
  之后的不再取、跨片汇总丢行与坏搭配、地址未配在发第一个请求前就失败、连接错误各自
  保住自己的机器码）。
