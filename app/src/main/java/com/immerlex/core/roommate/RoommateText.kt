package com.immerlex.core.roommate

/**
 * 离线时也能过日子：没有 API key、断网、模型超时，都用这里的本地内容顶上。
 * 这些句子是**内容**，不是界面文案；真正的按钮文字在 strings.xml。
 */
object RoommateText {

    data class LocalEpisode(
        val line: String,
        val choiceA: String,
        val choiceB: String,
        val habit: String,
    )

    private val episodes = listOf(
        LocalEpisode("它把窗台上的灰擦了一道，又坐回去，说今天不想干活。", "那就歇着", "问它在想什么", "擦窗台"),
        LocalEpisode("它出门了一趟，回来时手里多了一颗别人给的糖。", "问是谁给的", "让它留着慢慢吃", "出门"),
        LocalEpisode("它把昨天没弄好的东西又摆了一遍，这次没出声。", "搭把手", "让它自己来", "收拾东西"),
        LocalEpisode("它今天话很少，只在你看过去的时候抬了下眼。", "陪它坐一会儿", "先不打扰它", ""),
        LocalEpisode("它把攒的小东西摊在桌上数了一遍，又一样样收回去。", "问它最舍不得哪样", "由它去", "数东西"),
        LocalEpisode("外面下雨，它搬了张凳子在窗边坐了一会儿。", "给它递条毯子", "也坐下听雨", "看雨"),
        LocalEpisode("它学着你的样子翻了翻桌上的纸，其实一个字也不认识。", "读给它听", "让它自己翻", "翻书"),
        LocalEpisode("它把伞拿出来看了看天，又放了回去。", "问它在等什么", "随它放", "看天"),
    )

    private val replies = listOf(
        "它点了点头，没多说，但手上的动作轻了一点。",
        "它想了一会儿，给了一个很短的回答，然后又低下头。",
        "它没立刻答应，过了一会儿才把手里的东西放下。",
        "它看了你一眼，像是在确认你是认真的。",
    )

    fun episode(dayKey: Long, followUp: String?): LocalEpisode {
        val base = episodes[Math.floorMod(dayKey, episodes.size.toLong()).toInt()]
        return if (followUp.isNullOrBlank()) base else base.copy(line = "$followUp${base.line}")
    }

    fun choiceReply(dayKey: Long, chosen: Int): String =
        replies[Math.floorMod(dayKey + chosen, replies.size.toLong()).toInt()]

    private val introLines = listOf(
        "它把行李箱的拉链拉上，抬头看了你一眼，说以后请多关照。",
        "它站在门口没敢直接进，先把鞋摆整齐，才问你坐哪儿合适。",
        "它把随身的东西一样样摆上桌，摆完又觉得太满，收回了两样。",
        "它话不多，先把你桌上的杯子洗干净了，才说要住哪一边。",
        "它进门就看见了窗边那块地方，问能不能归它。",
    )

    /** 建档时它说的第一句；没有 API key 也要有这句话，否则房间空着不像刚认识。 */
    fun introLine(dayKey: Long): String =
        introLines[Math.floorMod(dayKey, introLines.size.toLong()).toInt()]

    /** 离线时它自己"写"的东西。有正文才能上架，不拿空卡片占位。 */
    fun work(dayKey: Long): Pair<String, String> {
        val title = listOf("窗边手记", "今天没做成的事", "一把伞的下午", "数到第七样")[
            Math.floorMod(dayKey, 4L).toInt(),
        ]
        val body = buildString {
            append("今天我把窗台擦了一遍。抹布拧不干，水顺着桌角往下滴，我就拿袖子去接。\n\n")
            append("擦到一半，发现窗框缝里卡着一小片纸，是上次包糖的那种。我没舍得扔，夹进了本子里。\n\n")
            append("后来天阴了。我把伞拿出来看了看，又放回去。伞骨上还有一道没修好的地方，我想等一个不着急的下午。\n\n")
            append("你要是有空，可以陪我把那道伞骨弄完。不着急，明天也行。")
        }
        return title to body
    }

    fun missingAssets(missing: Int): String =
        if (missing <= 0) "" else "有 $missing 张素材暂时找不到，重新导入原来的文件夹就能接上。"

    /** 台词本兜底：每个动作六句，按顺序轮换，不用随机。 */
    fun linebook(): Map<RoomAction, List<String>> = mapOf(
        RoomAction.TOUCH to listOf(
            "它眯起眼睛，往你手心里蹭了一下。",
            "它假装没看见，尾巴却动了动。",
            "它把脑袋低下来，让你摸到耳朵后面。",
            "它小声哼了一下，像在抱怨又不像。",
            "它翻了个身，把最软的那块肚皮露出来一点。",
            "它伸出爪子，轻轻按住你的手指。",
        ),
        RoomAction.TALK to listOf(
            "它听得很认真，最后只「嗯」了一声。",
            "它想了想，问你今天累不累。",
            "它没接话，过了一会儿才说「我知道了」。",
            "它把你说的话重复了一遍，自己先笑了。",
            "它歪着头，好像在等你继续说。",
            "它说它今天也遇到了一件小事，但先听你的。",
        ),
        RoomAction.OUTING to listOf(
            "它出门了，脚步声在楼梯间响了一会儿。",
            "它回来说楼下有只猫占了它的位置。",
            "它带回来一身风，还有路边摊的味道。",
            "它说今天没走远，只在附近转了转。",
            "它把口袋里的东西掏出来，又一样样装回去。",
            "它出门前回头看了你一眼，才把门带上。",
        ),
    )

    private val coquetryLines = listOf(
        "今天可以多陪我一会儿吗。",
        "我把最好看的那块地方留给你了。",
        "你回来得比平时晚了一点点。",
        "我没什么事，就是想叫叫你。",
        "外面风大，你进来再待一会儿。",
        "我今天表现得很好，你看见了吗。",
        "你要是忙，我就在旁边不说话。",
        "我留了半块点心，等你一起。",
    )

    fun petGift(dayKey: Long): PetGiftPlan =
        petGiftPlans[Math.floorMod(dayKey, petGiftPlans.size.toLong()).toInt()]

    private val petGiftPlans = listOf(
        PetGiftPlan("一颗圆石子", "它在河边挑了最久的一颗，说这颗最像月亮。", "这个给你。别问为什么，我就是想给。"),
        PetGiftPlan("备用纽扣", "从它那件旧外套上拆下来的，边缘已经被摸得发亮。", "你衣服上那颗松了，先用这个。"),
        PetGiftPlan("写着字的糖纸", "皱巴巴的糖纸，背面写了你名字的最后一个字。", "我练了好几遍，还是有点歪。"),
        PetGiftPlan("半截蜡笔", "它说这个颜色最像傍晚，画什么都不会太难看。", "你拿去用，我还有很多。"),
        PetGiftPlan("一根羽毛", "早上在窗台上捡到的，放了一整天，等你回来。", "它落在窗台上，我觉得应该给你。"),
        PetGiftPlan("折好的小纸船", "用的是不要的旧纸，折了拆、拆了折，边角有点毛。", "等下雨的时候，我们把它放进水洼里。"),
    )

    private data class GiftSet(
        val gifts: List<GiftPlan>,
        val recipe: RecipePlan,
    )

    private val giftSets = listOf(
        GiftSet(
            listOf(
                GiftPlan("海苔", "脆得会掉渣，它用两只手捧着走。", "它把海苔举到光里看了看，说像一小片夜。", "它把剩下的海苔夹进了本子里。", "small"),
                GiftPlan("米饭", "还温着，装在它自己的小碗里。", "它先闻了闻，才很小心地吃了一口。", "它说这碗米是今天最踏实的东西。", "medium"),
                GiftPlan("三文鱼", "它盯着看了半天，才舍得推到你面前。", "它吃得很快，吃完又有点不好意思。", "它把盘子收走时，悄悄舔了一下手指。", "large"),
            ),
            RecipePlan(listOf(0, 1, 2), "寿司", "海苔裹住米饭和三文鱼，切得不太齐，但很香。", "第一口给你。我第一次做，你别说不好吃。", "又做了一份，这次切得比上次好。"),
        ),
        GiftSet(
            listOf(
                GiftPlan("毛线", "颜色不算好看，但摸起来很软。", "它把毛线绕在手腕上，比了比长短。", "毛线在它椅子腿上缠了两圈，它没急着解。", "small"),
                GiftPlan("黄铜纽扣", "旧衣服上拆下来的，擦过之后亮了一点。", "它把纽扣扣在自己袖口，抬起来给你看。", "它今天一直在摸那颗纽扣。", "medium"),
                GiftPlan("旧布头", "洗得发白，边上还留着一道原来的线。", "它把布头铺在腿上，认真想了一会儿。", "布头被它收进了那个装宝贝的盒子。", "large"),
            ),
            RecipePlan(listOf(0, 1, 2), "拼布小包", "三样东西缝在一起，针脚歪歪扭扭，但很结实。", "第一件给你用。线头我没藏好，你别笑。", "又缝了一个，这次多了一道内袋。"),
        ),
        GiftSet(
            listOf(
                GiftPlan("柠檬", "表皮有点皱，但切开很香。", "它被酸得眯起眼睛，还是说好吃。", "它把柠檬籽留下来，说要试着种。", "small"),
                GiftPlan("蜂蜜", "罐子上沾了一圈，它舔了半天瓶口。", "它舀了一小勺，先递到你嘴边。", "它今天说话都带着一点甜味。", "medium"),
                GiftPlan("玻璃罐", "洗得很干净，对着窗能看见一小块变形。", "它把罐子举起来，透过它看你。", "罐子被它摆在窗台上，装了几颗小石子。", "large"),
            ),
            RecipePlan(listOf(0, 1, 2), "蜂蜜柠檬", "酸里带甜，凉了一下午才最好喝。", "第一杯给你，我多放了一点蜂蜜。", "第二杯冰过了，比刚才那杯好喝。"),
        ),
        GiftSet(
            listOf(
                GiftPlan("蜡烛", "很短的一截，灯芯是新的。", "它点蜡烛时屏住呼吸，怕吹灭。", "蜡烛烧到一半，它说什么也不肯再点。", "small"),
                GiftPlan("火柴", "盒子有点潮，划到第三根才着。", "它举着火柴，愣了一下才想起要做什么。", "它把剩下的火柴收进了抽屉最里面。", "medium"),
                GiftPlan("小陶碟", "边上有一道小缺口，正好放得下一根蜡烛。", "它把碟子擦了又擦，才放上蜡烛。", "碟子在桌上留了一圈浅浅的印子。", "large"),
            ),
            RecipePlan(listOf(0, 1, 2), "一小碟烛光", "蜡烛坐在陶碟里，光晕刚好照到桌角。", "第一晚先给你点。你坐着，我去关灯。", "今晚还可以再点一次，我留了火柴。"),
        ),
        GiftSet(
            listOf(
                GiftPlan("旧邮票", "边角有点卷，上面的图案已经看不清了。", "它把邮票对着光，猜那上面原来是什么。", "邮票被它压在书页里，说等干了再收。", "small"),
                GiftPlan("牛皮纸", "裁得不太齐，但摸起来很舒服。", "它把纸铺平，先用指甲划出折痕。", "纸上还留着它没写完的一行字。", "medium"),
                GiftPlan("细麻绳", "绕成一小团，闻起来有太阳的味道。", "它把麻绳在手指上绕了一圈，打了个结。", "麻绳被它挂在门后，说下次出门要带上。", "large"),
            ),
            RecipePlan(listOf(0, 1, 2), "一叠小信", "三样东西凑成一套，信封上还系了细麻绳。", "第一封信给你。我写得很慢，你别催。", "又写了一封，这封比上封长一点。"),
        ),
        GiftSet(
            listOf(
                GiftPlan("奶油", "装在小盒子里，路上被它护得很好。", "它用手指抹了一点，先让你尝。", "奶油盒被它刮得很干净，还是舍不得扔。", "small"),
                GiftPlan("草莓", "挑了最红的几颗，叶子还带着水。", "它吃了一颗，酸得皱起脸，又拿了第二颗。", "剩下的草莓被它摆在窗边，说要多看一会儿。", "medium"),
                GiftPlan("饼干", "边缘有点碎，闻起来很香。", "它把碎掉的那块先吃了，完整的留给你。", "饼干屑在桌上排成一小条，它没舍得擦。", "large"),
            ),
            RecipePlan(listOf(0, 1, 2), "草莓夹心", "两片饼干夹着奶油和草莓，做得很小心。", "第一块给你，我特意挑了最大的草莓。", "还有两块，我们一人一块。"),
        ),
    )

    private val stallPool = listOf(
        StallPlan("晒干的橘皮", "它说泡水喝会有一点太阳味。", "它把橘皮装进纸袋，又多塞了两片。"),
        StallPlan("系了绳的小石头", "从外面捡回来的，绳子是它自己编的。", "它说这个可以挂在包上，不容易丢。"),
        StallPlan("画坏的明信片", "正面画歪了，背面倒是留了很大一块空白。", "它有点不好意思，说你可以从背面开始用。"),
        StallPlan("玻璃瓶里的雨声", "瓶子是空的，它说摇一摇就能听见那天的雨。", "它让你摇一下，然后自己先笑了。"),
        StallPlan("压平的叶子", "夹在旧书里压了很久，叶脉还很清楚。", "它说这片叶子的形状像一只短短的手。"),
        StallPlan("补过的布袋", "布是自己染的，针脚不太直。", "它把袋子撑开给你看，里面还缝了一个小口袋。"),
        StallPlan("用剩的线轴", "绕得整整齐齐，颜色是它最喜欢的那个。", "它说线用完了也别扔，可以拿来滚。"),
        StallPlan("小纸盒", "折得方方正正，盖子上画了一个歪太阳。", "它把盒子递给你，说可以装那些舍不得丢的小东西。"),
    )

    private val nearCheap = listOf(
        NearPlan("一颗圆石子", "它在河边挑了很久，说这颗最像月亮。", "它把石子放进你手心，又假装去看别处。", "石子后来一直放在你的桌角，它每次路过都要看一眼。"),
        NearPlan("带绳的小铃铛", "铃铛很小，声音也轻，得安静下来才听得见。", "它把铃铛系在你包上，退后两步看效果。", "你走快的时候，它会响一下；它听见过好几次。"),
        NearPlan("半截蜡笔", "它说这个颜色最像傍晚，画什么都好看。", "它把蜡笔塞进你手里，说别省着用。", "蜡笔短了一截，它说那是画过东西的证据。"),
    )

    private val nearMedium = listOf(
        NearPlan("搪瓷杯", "杯口磕掉了一小块，倒水时刚好避开。", "它用这个杯子给你倒了一杯温水。", "杯子后来一直放在窗台上，杯底积了一圈茶色。"),
        NearPlan("厚一点的毯子", "它挑了很久，说冬天坐在地板上会冷。", "它把毯子抖开，先披在你肩上。", "毯子的一角被它缝了一个小口袋，说可以放糖。"),
        NearPlan("小木凳", "它自己打磨过，摸起来不扎手。", "它把凳子搬到窗边，说这里光最好。", "凳面上有了一道浅浅的划痕，它说那是你坐出来的。"),
    )

    private val nearHigh = listOf(
        NearPlan("带抽屉的小木柜", "柜子不大，抽屉推起来有木头的声音。", "它把柜子擦了一遍，才让你拉开第一个抽屉。", "抽屉里慢慢有了东西：线、糖纸、你写坏的便签。"),
        NearPlan("一盏旧台灯", "灯罩有点旧，光却是暖的。", "它把灯打开，屋子里一下子安静下来。", "灯整晚亮着，它说这样你回来时就不会觉得屋里没人。"),
        NearPlan("能摇的躺椅", "放在窗边刚好，摇起来会轻轻响。", "它先坐上去试了试，然后拍了拍旁边的位置。", "躺椅后来成了你们分着用的地方，谁先到谁躺。"),
    )

    fun dailyPack(dayKey: Long, followUp: String?, nearPrice: Int): DailyPack {
        val set = giftSets[Math.floorMod(dayKey, giftSets.size.toLong()).toInt()]
        val stall = (0..1).map {
            stallPool[Math.floorMod(dayKey + it, stallPool.size.toLong()).toInt()]
        }
        val base = episode(dayKey, followUp)
        val coquetry = (0..2).map {
            coquetryLines[Math.floorMod(dayKey + it, coquetryLines.size.toLong()).toInt()]
        }
        val near = when {
            nearPrice <= 12 -> nearCheap[Math.floorMod(dayKey, nearCheap.size.toLong()).toInt()]
            nearPrice <= 50 -> nearMedium[Math.floorMod(dayKey, nearMedium.size.toLong()).toInt()]
            else -> nearHigh[Math.floorMod(dayKey, nearHigh.size.toLong()).toInt()]
        }
        return DailyPack(
            episode = GeneratedEpisode(base.line, base.choiceA, base.choiceB, base.habit.ifBlank { null }, null),
            gifts = set.gifts,
            stall = stall,
            recipe = set.recipe,
            petGift = petGiftPlans[Math.floorMod(dayKey, petGiftPlans.size.toLong()).toInt()],
            coquetry = coquetry,
            near = near,
        )
    }
}
