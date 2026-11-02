package com.immerlex.core.settings

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyMapTest {

    @Test
    fun theDefaultBindingsAreTheOnesTheUserAskedFor() {
        val map = KeyMap.DEFAULT
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, map.keyFor(KeyAction.READER_PREV_WORD))
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, map.keyFor(KeyAction.READER_NEXT_WORD))
        assertEquals(KeyEvent.KEYCODE_SPACE, map.keyFor(KeyAction.READER_OPEN_WORD))
        // 从熟到不熟：1 是简单，4 是忘了（与 FSRS 自己的编号方向相反）。
        assertEquals(KeyEvent.KEYCODE_1, map.keyFor(KeyAction.READER_EASY))
        assertEquals(KeyEvent.KEYCODE_2, map.keyFor(KeyAction.READER_GOOD))
        assertEquals(KeyEvent.KEYCODE_3, map.keyFor(KeyAction.READER_HARD))
        assertEquals(KeyEvent.KEYCODE_4, map.keyFor(KeyAction.READER_AGAIN))
        assertEquals(KeyEvent.KEYCODE_1, map.keyFor(KeyAction.QUIZ_OPTION_A))
        assertEquals(KeyEvent.KEYCODE_4, map.keyFor(KeyAction.QUIZ_OPTION_D))
        assertEquals(KeyEvent.KEYCODE_0, map.keyFor(KeyAction.QUIZ_DOUBT))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, map.keyFor(KeyAction.QUIZ_NEXT))
    }

    @Test
    fun theSameKeyCanMeanDifferentThingsInTwoScreens() {
        assertEquals(KeyAction.READER_EASY, KeyMap.DEFAULT.actionFor(KeyContext.READER, KeyEvent.KEYCODE_1))
        assertEquals(KeyAction.QUIZ_OPTION_A, KeyMap.DEFAULT.actionFor(KeyContext.QUIZ, KeyEvent.KEYCODE_1))
    }

    @Test
    fun bindingAKeySomeoneElseHoldsMovesThatActionToTheOldKey() {
        val map = KeyMap.DEFAULT.bind(KeyAction.READER_NEXT_WORD, KeyEvent.KEYCODE_SPACE)
        assertEquals(KeyEvent.KEYCODE_SPACE, map.keyFor(KeyAction.READER_NEXT_WORD))
        // 空格原先归"打开词卡"，现在换到下键上；不会两个动作一起抢空格。
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, map.keyFor(KeyAction.READER_OPEN_WORD))
        assertEquals(KeyAction.READER_NEXT_WORD, map.actionFor(KeyContext.READER, KeyEvent.KEYCODE_SPACE))
        assertNull(
            "同一个场景里不会有两个动作共用空格",
            KeyAction.of(KeyContext.READER)
                .firstOrNull { it != KeyAction.READER_NEXT_WORD && map.keyFor(it) == KeyEvent.KEYCODE_SPACE },
        )
    }

    @Test
    fun bindingOneScreenNeverTouchesTheOther() {
        val map = KeyMap.DEFAULT.bind(KeyAction.QUIZ_NEXT, KeyEvent.KEYCODE_N)
        assertEquals(KeyEvent.KEYCODE_N, map.keyFor(KeyAction.QUIZ_NEXT))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, KeyMap.DEFAULT.keyFor(KeyAction.QUIZ_NEXT))
        // 阅读页的"下键"一个字都没动。
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, map.keyFor(KeyAction.READER_NEXT_WORD))
    }

    @Test
    fun aRebindingSurvivesTheRoundTripThroughPreferences() {
        val map = KeyMap.DEFAULT
            .bind(KeyAction.QUIZ_NEXT, KeyEvent.KEYCODE_N)
            .bind(KeyAction.READER_AGAIN, KeyEvent.KEYCODE_9)
        val decoded = KeyMap.decode(map.encode())
        KeyAction.entries.forEach { assertEquals("$it", map.keyFor(it), decoded.keyFor(it)) }
    }

    @Test
    fun aBrokenSettingFallsBackToTheKeyItCouldNotRead() {
        val decoded = KeyMap.decode("READER_EASY=xyz;NOPE=12;QUIZ_NEXT=0;READER_GOOD=29")
        assertEquals(KeyEvent.KEYCODE_1, decoded.keyFor(KeyAction.READER_EASY))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, decoded.keyFor(KeyAction.QUIZ_NEXT))
        assertEquals(29, decoded.keyFor(KeyAction.READER_GOOD))
        assertEquals(KeyMap.DEFAULT, KeyMap.decode(""))
    }
}
