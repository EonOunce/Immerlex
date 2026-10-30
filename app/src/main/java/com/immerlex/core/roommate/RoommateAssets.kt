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
        val exact = assets.filter { asset -> action.keywords.any { asset.tag.contains(it) } }
        val pool = exact.ifEmpty { assets }
        return pool.minByOrNull { spin(it.assetId, action, seed) }
    }

    private fun spin(assetId: String, action: RoomAction, seed: Long): Int =
        Math.floorMod((assetId + '|' + action.key + '|' + seed).hashCode(), 10_000)
}
