package com.immerlex.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.immerlex.app.ui.hideWindow
import com.immerlex.app.ui.isHideKey

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ImmerlexRoot() }
    }

    /**
     * 课上偷背用的那颗键：整个窗口交给系统收起来。
     * 拦在 Activity 这一层而不是界面里的焦点捕获：任何一屏都得收得到，
     * 不能因为此刻焦点落在某个按钮上就漏掉。抽屉与弹窗是另一扇窗，那一半由 [com.immerlex.app.ui.KeyCapture] 认。
     * 用 [onKeyDown] 而不是覆写 dispatchKeyEvent：后者是 androidx 的限制接口，lint 直接判错。
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isHideKey(this, keyCode)) {
            hideWindow(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
