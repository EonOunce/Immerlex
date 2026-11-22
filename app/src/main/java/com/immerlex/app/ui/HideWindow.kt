package com.immerlex.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.immerlex.app.ImmerlexApplication
import com.immerlex.core.settings.KeyAction
import com.immerlex.core.settings.KeyRecording

/**
 * 那颗「收起窗口」的键：整个窗口交给系统收起来，三星的小窗会缩成一颗可拖动的图标，点它就回来。
 * 拦在两个地方——Activity 那一层管常规页面，[KeyCapture] 管抽屉与弹窗（它们是另一扇窗，
 * 按键根本到不了 Activity），两处都走这一个函数，收起的动作只有一份写法。
 */
fun hideWindow(context: Context) {
    activityOf(context)?.moveTaskToBack(true)
}

/** 按下去的是不是那颗键。录键的时候一律不认，否则想把它改绑成别的键就永远改不成。 */
fun isHideKey(context: Context, keyCode: Int): Boolean {
    if (KeyRecording.active) return false
    val app = context.applicationContext as? ImmerlexApplication ?: return false
    return keyCode == app.container.prefs.keyBindings.value.keyFor(KeyAction.APP_HIDE)
}

private tailrec fun activityOf(context: Context): Activity? = when (context) {
    is Activity -> context
    is ContextWrapper -> activityOf(context.baseContext)
    else -> null
}
