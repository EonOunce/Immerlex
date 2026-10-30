package com.immerlex.core.roommate

import com.immerlex.core.db.RM_CHANNEL_GIFT
import com.immerlex.core.db.RM_CHANNEL_NEAR
import com.immerlex.core.db.RM_CHANNEL_RECIPE
import com.immerlex.core.db.RM_CHANNEL_STALL
import com.immerlex.core.db.RoommateOfferEntity
import com.immerlex.core.db.RoommateWorkEntity

/** 把一包内容摊成数据库里的商品行；价格与隐藏规则在这里一次定死。 */
fun packOffers(
    pack: DailyPack,
    day: Long,
    nearPrice: Int,
    atSec: Long,
    source: String,
): List<RoommateOfferEntity> {
    val gifts = pack.gifts.mapIndexed { index, gift ->
        RoommateOfferEntity(
            offerId = giftId(day, index),
            dayKey = day,
            channel = RM_CHANNEL_GIFT,
            name = gift.name,
            story = gift.story,
            reaction = gift.reaction,
            followUp = gift.followUp,
            price = OfferPolicy.giftPrice(gift.tier),
            source = source,
            createdAtSec = atSec,
        )
    }
    val stall = pack.stall.mapIndexed { index, item ->
        RoommateOfferEntity(
            offerId = "d$day:s$index",
            dayKey = day,
            channel = RM_CHANNEL_STALL,
            name = item.name,
            story = item.story,
            reaction = item.reaction,
            followUp = "",
            price = OfferPolicy.STALL_PRICES[index],
            source = source,
            createdAtSec = atSec,
        )
    }
    val recipe = pack.recipe?.let { plan ->
        RoommateOfferEntity(
            offerId = recipeId(day),
            dayKey = day,
            channel = RM_CHANNEL_RECIPE,
            name = plan.name,
            story = plan.story,
            reaction = plan.freeLine,
            followUp = plan.restLine,
            price = OfferPolicy.RECIPE_PRICE,
            hidden = true,
            requires = plan.giftIndices.joinToString(",") { giftId(day, it) },
            source = source,
            createdAtSec = atSec,
        )
    }
    val near = pack.near?.let { plan ->
        RoommateOfferEntity(
            offerId = "near:$day",
            dayKey = day,
            channel = RM_CHANNEL_NEAR,
            name = plan.name,
            story = plan.story,
            reaction = plan.reaction,
            followUp = plan.followUp,
            price = nearPrice,
            source = source,
            createdAtSec = atSec,
        )
    }
    return gifts + stall + listOfNotNull(recipe, near)
}

fun giftId(day: Long, index: Int): String = "d$day:g$index"

fun recipeId(day: Long): String = "d$day:r"

fun RoommateOfferEntity.toCard(balance: Int): OfferCard = OfferCard(
    offerId = offerId,
    channel = channel,
    name = name,
    story = story,
    price = price,
    purchased = purchased,
    // 菜谱可以反复做，买过不等于不能再买。
    affordable = balance >= price && (channel == RM_CHANNEL_RECIPE || !purchased),
    gap = if (channel == RM_CHANNEL_NEAR) OfferPolicy.nearGap(price, balance) else 0,
)

fun shopOf(
    offers: List<RoommateOfferEntity>,
    near: RoommateOfferEntity?,
    works: List<RoommateWorkEntity>,
    balance: Int,
): ShopView {
    val visible = offers.filterNot { it.hidden }
    return ShopView(
        balance = balance,
        gifts = visible.filter { it.channel == RM_CHANNEL_GIFT }.sortedBy { it.offerId }.map { it.toCard(balance) },
        stall = visible.filter { it.channel == RM_CHANNEL_STALL }.sortedBy { it.offerId }.map { it.toCard(balance) },
        recipe = visible.firstOrNull { it.channel == RM_CHANNEL_RECIPE }?.toCard(balance),
        near = near?.toCard(balance),
        works = works,
    )
}
