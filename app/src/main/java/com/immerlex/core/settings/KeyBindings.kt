package com.immerlex.core.settings

import android.view.KeyEvent

/** 键盘只在这两处有绑定：阅读页与测验页。同一个键在两边可以各干各的。 */
enum class KeyContext { READER, QUIZ }

/**
 * 一个动作的默认键位。阅读页数字 1–4 从"简单"到"忘了"——与 FSRS 自己的编号方向相反，
 * 是用户 2026-11-02 明确要的顺序。
 */
enum class KeyAction(val context: KeyContext, val defaultKeyCode: Int) {
    READER_PREV_WORD(KeyContext.READER, KeyEvent.KEYCODE_DPAD_UP),
    READER_NEXT_WORD(KeyContext.READER, KeyEvent.KEYCODE_DPAD_DOWN),
    READER_OPEN_WORD(KeyContext.READER, KeyEvent.KEYCODE_SPACE),
    READER_EASY(KeyContext.READER, KeyEvent.KEYCODE_1),
    READER_GOOD(KeyContext.READER, KeyEvent.KEYCODE_2),
    READER_HARD(KeyContext.READER, KeyEvent.KEYCODE_3),
    READER_AGAIN(KeyContext.READER, KeyEvent.KEYCODE_4),
    QUIZ_OPTION_A(KeyContext.QUIZ, KeyEvent.KEYCODE_1),
    QUIZ_OPTION_B(KeyContext.QUIZ, KeyEvent.KEYCODE_2),
    QUIZ_OPTION_C(KeyContext.QUIZ, KeyEvent.KEYCODE_3),
    QUIZ_OPTION_D(KeyContext.QUIZ, KeyEvent.KEYCODE_4),
    QUIZ_DOUBT(KeyContext.QUIZ, KeyEvent.KEYCODE_0),
    QUIZ_NEXT(KeyContext.QUIZ, KeyEvent.KEYCODE_DPAD_RIGHT),
    ;

    companion object {
        fun of(context: KeyContext): List<KeyAction> = entries.filter { it.context == context }
    }
}

/**
 * 一组键位。同一个场景里一个键只做一件事：改绑到别人正用着的键时，两个动作互换，
 * 不会留下两个动作抢同一个键的状态。
 */
data class KeyMap private constructor(private val codes: Map<KeyAction, Int>) {

    fun keyFor(action: KeyAction): Int = codes[action] ?: action.defaultKeyCode

    fun actionFor(context: KeyContext, keyCode: Int): KeyAction? =
        KeyAction.of(context).firstOrNull { keyFor(it) == keyCode }

    fun bind(action: KeyAction, keyCode: Int): KeyMap {
        val holder = actionFor(action.context, keyCode)?.takeIf { it != action }
        val next = codes.toMutableMap()
        if (holder != null) next[holder] = keyFor(action)
        next[action] = keyCode
        return KeyMap(next)
    }

    fun encode(): String = KeyAction.entries.joinToString(SEPARATOR) { "${it.name}$PAIR${keyFor(it)}" }

    companion object {
        private const val SEPARATOR = ";"
        private const val PAIR = "="
        val DEFAULT = KeyMap(emptyMap())

        /** 认不出的名字、坏掉的数字都跳过、退回默认键：一份坏设置不该让键盘失灵。 */
        fun decode(raw: String): KeyMap {
            if (raw.isBlank()) return DEFAULT
            return raw.split(SEPARATOR).fold(DEFAULT) { map, entry ->
                val at = entry.indexOf(PAIR)
                if (at <= 0) return@fold map
                val action = KeyAction.entries.firstOrNull { it.name == entry.take(at) } ?: return@fold map
                val code = entry.substring(at + 1).trim().toIntOrNull() ?: return@fold map
                if (code <= 0) map else map.bind(action, code)
            }
        }
    }
}
