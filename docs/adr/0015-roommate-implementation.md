# 0015 室友 v2.0 落地：钱、素材与恢复的边界

日期：2026-10-30。状态：已实现，模拟器验收（待真机）。前置方向见 [ADR 0014](0014-roommate-system-and-learning-rewards.md)。

## 背景

ADR 0014 只定了方向，并留下两条硬前置：备份要真正可回滚，`ScheduleService` 的两次 DAO
写要进 Room 事务。v2.0 三刀把它们连同室友本体一起做完：第一刀是本地生活（认识、房间、
免费互动、小本子），第二刀是奖励（小鱼干、礼物、摆摊卖作品），第三刀是备份换机与长期手感。
危险点集中在四处：钱由可撤销的学习记录驱动；购买是多步写入；恢复要换整个数据库文件；
素材是用户手机里的 GIF，不在库里。

## 决定

1. **钱只由账本决定**。`rm_ledger` 的 `amount` 求和就是余额，不另存"当前余额"列。
   `dedupeKey` 唯一：同一个词同一天一次（`w:<天>:<规范化词形>`）、里程碑一次
   （`m:<天>:5`）、每件礼物 / 作品一次（`buy:<id>` / `work:<id>`）。公式
   `R(N)=min(N,30)+5·[N≥5]` 在 `RewardPolicy`，纯函数可测。
2. **结算必须等过撤销窗口**。`RoommateSettlement.settle(now)` 以 `lastSettledDay` 为水位线：
   首次启用从**昨天**起算（不倒补历史巨款）；已封口的整天用 `Long.MAX_VALUE` 截止；
   今天只结算"最后一次碰到该词早于 `now − 8s`"的记录。撤销只删日志，所以 8 秒内删掉的
   记录永远在结算前消失；反过来，已发的钱不会因事后删除而收回——这就是窗口存在的意义。
   源词来自 `review_logs JOIN words`，按 `lower(trim(word))` 分组取 `MAX(reviewEpochSec)`，
   learn 与 recall 都算、同词多章多次只算一个。
3. **室友的所有多步写入走同一个 Room 事务**，唯一写入口 `RoommateRepository`：
   购买在事务内查所有权、查余额、记负账、发库存、写事件；余额不足返回 `TooPoor` 且不留
   任何行，重复购买返回 `AlreadyOwned`，余额不可能为负。`ScheduleService` 原本的
   "改卡 + 写日志"两步已收进 `database.withTransaction { … }`（`write()`），不再只靠
   Mutex。
4. **恢复是换文件，不是删了再复制**。`DbFileSwap.install` 先把新库复制成 `.new` 暂存，
   再把旧的主库与 `-wal`/`-shm` 改名为 `.prev`，`rename` 就位后才删 `.prev`；任何一步
   失败都把旧库放回。进程恰好被杀，`ImmerlexDatabase.create` 开库前先 `recover()`。
   `DbSnapshot.install` 已委托给它，备份校验闸门不变。
5. **素材按内容寻址，缺了靠重导修复**。SAF 选目录（`DocumentsContract`，不用 DocumentFile）
   导入，边读边算 SHA-256，文件名取前 32 位存 `filesDir/roommate/<hash>.gif`；同一张图从
   别的文件夹再导一次仍是同一行。动作名从文件名解析。已导过的文件被删时，`missing()`
   列出、界面提示"素材缺失"，**重新导入同一文件夹即修复**（复制步骤会补回缺失的目标），
   不需要单独修复代码。备份只含数据库：换机后小本子、选择、钱都在，GIF 要重导，
   因为文件名就是内容哈希，重导后旧事件自动指回同一张图。
6. **GIF 单次播放、停末帧**（**已被 [ADR 0016](0016-roommate-v21-daily-life-and-looping.md)
   取代：v2.1 起改为循环播放**）。API 28+ 用 `ImageDecoder` → `AnimatedImageDrawable`
   （`repeatCount = 0` + 动画回调）；API 26–27 用 `Movie` + `ValueAnimator` 兜底。
   解码在 IO 线程，但**动画回调必须在 Looper 线程注册**——之前在线程池里 `registerAnimationCallback`
   抛异常被吞掉，舞台只剩空渐变；现在 `GifStage` 解码回主线程后才挂回调。`playing` 在解码
   成功后才置位，播放中的连点直接忽略；播放结束停在末帧，下一次有效互动才从头播。

## 后果

- 热力图的"当天不同词数"和钱包的"当天入账"可能不同：撤销窗口、启用边界、每日上限都会
  造成差异。规则页已写明，不能拿热力图当账本。（每日上限已被 ADR 0016 取消，改用收益递减。）
- 结算失败或进程被杀不会少发：唯一索引与水位线让重复结算安全，下一次打开页面会补上。
- `DbFileSwap` 的多文件改名不是跨进程原子操作，但 `recover()` 覆盖了"改名到一半被杀"；
  恢复仍要求应用重启，旧连接不能继续用。
- 不包含媒体与密钥：新手机的 GIF 要重导，AI key 与坚果云密码要重填。
- 验收证据：`RoommateLedgerTest`（6 条仪器测试：购买不会扣成负数 / 不扣两次、作品购买
  守卫、8 秒窗口、同词不重复发钱、首次启用不倒补、每日 30 封顶仍发里程碑）、
  `DbFileSwapTest`、`RoommatePolicyTest`、`RoommateAssetsTest`、`RoommateMigrationTest`
  （v8→v9 与 v1→v9 全链），其余 24 条仪器测试回归通过；模拟器上实走导入、播放、
  选择、本子、小铺与重开持久化。
