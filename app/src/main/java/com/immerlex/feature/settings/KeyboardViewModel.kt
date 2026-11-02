package com.immerlex.feature.settings

import androidx.lifecycle.ViewModel
import com.immerlex.core.settings.AppPreferences
import com.immerlex.core.settings.KeyAction
import com.immerlex.core.settings.KeyMap
import kotlinx.coroutines.flow.StateFlow

/** 改键位就地生效：阅读页与测验页订阅同一份 [AppPreferences.keyBindings]，不用重启。 */
class KeyboardViewModel(private val prefs: AppPreferences) : ViewModel() {

    val bindings: StateFlow<KeyMap> = prefs.keyBindings

    fun bind(action: KeyAction, keyCode: Int) = prefs.bindKey(action, keyCode)

    fun reset() = prefs.resetKeyBindings()
}
