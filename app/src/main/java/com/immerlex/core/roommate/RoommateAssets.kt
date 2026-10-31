package com.immerlex.core.roommate

import com.immerlex.core.db.RoommateAssetEntity

/**
 * 素材从文件名里读一个动作名：`米提亚_吹鼻子_2026-10-30-00-18-07.gif` → `吹鼻子`。
 * 命名只是线索，接入前仍要人眼看一遍；读不出来就归到「其他」，不阻止导入。
 */
object RoommateAssets {
    private val DATE = Regex("""^\d{4}[-_年]?\d{1,2}[-_月]?\d{1,2}.*$""")

    fun isGif(fileName: String): Boolean = fileName.substringAfterLast('.', "").equals("gif", true)

    fun tagOf(fileName: String): String {
        val stem = fileName.substringBeforeLast('.').substringAfterLast('/').substringAfterLast('\\')
        val parts = stem.split('_').map { it.trim() }.filter { it.isNotEmpty() }
        val withoutDates = parts.filterNot { DATE.matches(it) || it.all(Char::isDigit) }
        // 第一个通常是角色名（米提亚），后面才是动作；只有一个片段时别把它当名字丢掉。
        val candidates = if (withoutDates.size >= 2) withoutDates.drop(1) else withoutDates
        val raw = candidates.firstOrNull() ?: return "其他"
        val clean = raw.replace(Regex("""[\s\d]+$"""), "").trim()
        return clean.ifEmpty { "其他" }
    }

    /**
     * 同一动作优先用对得上的素材；没有对得上的就退回全部素材。
     * 用固定哈希挑选而不是 RANDOM()，同一天的同一动作重绘结果不变。
     */
    fun pick(assets: List<RoommateAssetEntity>, action: RoomAction, seed: Long): RoommateAssetEntity? {
        if (assets.isEmpty()) return null
        return matching(assets, action).minByOrNull { spin(it.assetId, action, seed) }
    }

    /**
     * 按稳定顺序轮换素材：一次互动一张新脸，绝不重复台上正在放的那一张。
     * 池子里只有一张时无处可换，就让它重播——这比停在旧文件上好。
     */
    fun next(
        assets: List<RoommateAssetEntity>,
        action: RoomAction?,
        cursor: Int,
        onStage: String?,
    ): RoommateAssetEntity? {
        if (assets.isEmpty()) return null
        val pool = (if (action == null) assets else matching(assets, action)).sortedBy { it.assetId }
        if (pool.size == 1) return pool.first()
        val start = Math.floorMod(cursor, pool.size)
        val rotated = pool.drop(start) + pool.take(start)
        return rotated.firstOrNull { it.assetId != onStage } ?: rotated.first()
    }

    private fun matching(assets: List<RoommateAssetEntity>, action: RoomAction) =
        assets.filter { asset -> action.keywords.any { asset.tag.contains(it) } }.ifEmpty { assets }

    private fun spin(assetId: String, action: RoomAction, seed: Long): Int =
        Math.floorMod((assetId + '|' + action.key + '|' + seed).hashCode(), 10_000)
}
