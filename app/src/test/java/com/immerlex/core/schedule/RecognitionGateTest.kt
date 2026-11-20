package com.immerlex.core.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionGateTest {

    @Test
    fun clozeAndDefinitionAreWorthTwoPoints() {
        assertEquals(RecognitionGate.HEAVY_POINTS, RecognitionGate.pointsFor("CLOZE"))
        assertEquals(RecognitionGate.HEAVY_POINTS, RecognitionGate.pointsFor("DEFINITION"))
    }

    @Test
    fun everyOtherTypeIncludingAnOldRowWithoutOneIsWorthOnePoint() {
        assertEquals(1, RecognitionGate.pointsFor("EN_ZH"))
        assertEquals(1, RecognitionGate.pointsFor("ZH_EN"))
        assertEquals(1, RecognitionGate.pointsFor("COLLOCATION"))
        assertEquals(1, RecognitionGate.pointsFor("MATCH"))
        assertEquals("升级前的老记录没有题型，按最轻的一分算", 1, RecognitionGate.pointsFor(null))
    }

    @Test
    fun graduatingTakesThreePoints() {
        assertEquals(3, RecognitionGate.GOOD_POINTS)
    }
}
