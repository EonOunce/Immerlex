package com.immerlex.core.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class EchoClient(private val responses: List<String>) : LlmClient {
    val prompts = mutableListOf<String>()
    val systems = mutableListOf<String>()
    var calls = 0

    override suspend fun complete(system: String, user: String): String {
        systems += system
        prompts += user
        val response = responses[minOf(calls, responses.lastIndex)]
        calls++
        if (response == THROW) throw LlmException(503, "网关不可用")
        return response
    }

    override suspend fun probe(): ProbeResult = ProbeResult(ProbeFailure.NONE)

    companion object {
        const val THROW = "\u0000throw"
    }
}

private const val GOOD = """
{
  "tone": "克制、冷，情绪藏在动作里",
  "protagonist": "落薇只想活着走出冷宫",
  "ending": "倾向悲剧",
  "forbidden": ["不许出现重生", "不许出现系统面板"],
  "cast": [
    {"name": "落薇", "role": "皇后", "start": "被禁足冷宫，手里握着母亲留下的密信"},
    {"name": "刘禧", "role": "皇帝近侍", "start": "替皇帝盯着尚宫局"}
  ]
}
"""

class BibleExpanderTest {

    @Test
    fun oneCallExpandsTheSeedIntoADraft() = runBlocking {
        val client = EchoClient(listOf(GOOD))
        val expander = BibleExpander(clientFor = { client })

        val outcome = expander.expand("我想看宫斗，女主不想斗")

        val drafted = outcome as BibleOutcome.Drafted
        assertEquals(1, drafted.attempts)
        assertEquals(1, client.calls)
        assertEquals("克制、冷，情绪藏在动作里", drafted.draft.tone)
        assertEquals("落薇只想活着走出冷宫", drafted.draft.protagonist)
        assertEquals("倾向悲剧", drafted.draft.ending)
        assertEquals(listOf("不许出现重生", "不许出现系统面板"), drafted.draft.forbidden)
        assertEquals("落薇", drafted.draft.cast.first().name)
        assertEquals("被禁足冷宫，手里握着母亲留下的密信", drafted.draft.cast.first().start)
        assertTrue("开局想法要原样交给模型", client.prompts.single().contains("我想看宫斗，女主不想斗"))
        assertTrue("只准输出 JSON", client.systems.single().contains("绝不输出 JSON 以外的任何文字"))
    }

    @Test
    fun fencedOutputStillCountsAsOneAttempt() = runBlocking {
        val client = EchoClient(listOf("这是我的方案：\n```json\n$GOOD\n```"))

        val outcome = BibleExpander(clientFor = { client }).expand("宫斗")

        assertEquals(1, (outcome as BibleOutcome.Drafted).attempts)
    }

    @Test
    fun missingSectionsAreSentBackForRepair() = runBlocking {
        val client = EchoClient(listOf("""{"tone":"","protagonist":"只想活着","cast":[]}""", GOOD))
        val expander = BibleExpander(clientFor = { client })

        val outcome = expander.expand("宫斗")

        val drafted = outcome as BibleOutcome.Drafted
        assertEquals(2, drafted.attempts)
        assertTrue(client.prompts[1].contains("missing_tone"))
        assertTrue(client.prompts[1].contains("missing_cast"))
        assertTrue("修复要把上一次的输出带回去", client.prompts[1].contains("上一次输出不合格"))
    }

    @Test
    fun givesUpAfterTheRepairBudget() = runBlocking {
        val client = EchoClient(listOf("这不是 JSON"))

        val outcome = BibleExpander(clientFor = { client }).expand("宫斗")

        val rejected = outcome as BibleOutcome.Rejected
        assertEquals(3, rejected.attempts)
        assertEquals(3, client.calls)
        assertEquals(listOf("unparseable_json"), rejected.issues.map { it.code })
    }

    @Test
    fun transportFailureStopsImmediately() = runBlocking {
        val client = EchoClient(listOf(EchoClient.THROW))

        val outcome = BibleExpander(clientFor = { client }).expand("宫斗")

        val failed = outcome as BibleOutcome.Failed
        assertEquals(ProbeFailure.HTTP_ERROR, failed.failure)
        assertEquals("网关不可用", failed.detail)
        assertEquals("网络坏了不该再重试烧钱", 1, client.calls)
    }

    @Test
    fun longAndNumerousEntriesAreClipped() = runBlocking {
        val cast = (1..15).joinToString(",") { """{"name":"人物$it","role":"身份","start":"处境"}""" }
        val forbidden = (1..12).joinToString(",") { """"禁令$it"""" }
        val client = EchoClient(listOf("""{"tone":"${"长".repeat(400)}","protagonist":"主角","forbidden":[$forbidden],"cast":[$cast]}"""))

        val draft = (BibleExpander(clientFor = { client }).expand("宫斗") as BibleOutcome.Drafted).draft

        assertEquals("单条太长会挤爆 prompt", 160, draft.tone.length)
        assertEquals(8, draft.forbidden.size)
        assertEquals(12, draft.cast.size)
    }
}
