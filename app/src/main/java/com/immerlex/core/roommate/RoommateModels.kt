package com.immerlex.core.roommate

import com.immerlex.core.db.RoommateWorkEntity

/** 三个固定互动。普通陪伴不收小鱼干，也不写学习记录。 */
enum class RoomAction(val key: String, val keywords: List<String>) {
    TOUCH("touch", listOf("摸", "眨", "摇铃", "吹鼻", "提示")),
    TALK("talk", listOf("摇铃", "吹鼻", "眨", "通知")),
    OUTING("outing", listOf("通知", "提示", "星星", "收藏")),
    ;

    companion object {
        fun of(key: String?): RoomAction = entries.firstOrNull { it.key == key } ?: TOUCH
    }
}

/** 初识时选的三项底色；之后兴趣、习惯、关系可以变，这三项是它说话的底子。 */
data class Persona(
    val name: String,
    val nature: String,
    val pace: String,
    val face: String,
) {
    val card: String get() = PersonaCards.build(this)
}

/**
 * 三个维度的取值是**机器码**，界面文案在 strings.xml 里按码取；这里只放写进 prompt 的中文描述。
 * 这样改一处 UI 文案不会让已建好的室友变回默认值。
 */
object PersonaCards {
    const val DIRECT = "direct"
    const val INDIRECT = "indirect"
    const val QUIET = "quiet"
    const val SLOW = "slow"
    const val FAST = "fast"
    const val MOOD = "mood"
    const val OPEN = "open"
    const val PLAIN = "plain"
    const val PERFORMS = "performs"

    private val natures = mapOf(
        DIRECT to "有话就说，不绕弯子",
        INDIRECT to "先把话在心里过一遍，再说半句",
        QUIET to "大多时候只是看着你，偶尔才开口",
    )
    private val paces = mapOf(
        SLOW to "做什么都慢半拍，但会做完",
        FAST to "想到就动手，做完才想起累",
        MOOD to "兴致来了能忙一下午，没兴致就躺着",
    )
    private val faces = mapOf(
        OPEN to "高兴和生气都藏不住",
        PLAIN to "表情很淡，得看它手上的动作",
        PERFORMS to "喜欢把小事演得像一场戏",
    )

    val natureKeys = natures.keys.toList()
    val paceKeys = paces.keys.toList()
    val faceKeys = faces.keys.toList()

    fun build(persona: Persona): String {
        val a = natures[persona.nature] ?: natures.getValue(DIRECT)
        val b = paces[persona.pace] ?: paces.getValue(SLOW)
        val c = faces[persona.face] ?: faces.getValue(OPEN)
        return "它$a；$b。$c。"
    }
}

/** 刚刚发生的一件事：它说的话进对话框，旁白落在房间下面。空字段就不显示那一样。 */
data class RoomMoment(val line: String, val narration: String)

/** 一天里能看到的全部东西，由 ViewModel 一次性取好交给界面。 */
data class RoomView(
    val profile: Persona?,
    val setting: String,
    val episode: Episode?,
    val balance: Int,
    val earnedToday: Int,
    val habits: List<String>,
    val notebookCount: Int,
    val missingAssets: Int,
    val assetsImported: Int,
    val petGiftCount: Int,
    val petGiftToday: PetGiftView?,
    val near: OfferCard?,
)

/** 铺子里的一件东西；界面只读这一层，不直接碰 Room 行。 */
data class OfferCard(
    val offerId: String,
    val channel: String,
    val name: String,
    val story: String,
    val price: Int,
    val purchased: Boolean,
    val affordable: Boolean,
    /** 攒钱目标专用：还差多少条；其它渠道为 0。 */
    val gap: Int,
)

data class ShopView(
    val balance: Int,
    val gifts: List<OfferCard>,
    val stall: List<OfferCard>,
    val recipe: OfferCard?,
    val near: OfferCard?,
    val works: List<RoommateWorkEntity>,
)

data class Episode(
    val id: Long,
    val line: String,
    val choiceA: String,
    val choiceB: String,
    val chosen: Int,
    val assetId: String?,
)
