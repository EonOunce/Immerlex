package com.immerlex.app.ui

/**
 * 窗口够不够宽，只分四档。阈值沿用 Material 的 600 / 840 / 1200：
 * 阅读页要在 1200dp 才摆第三栏，那时正文还剩 600dp 左右，一行三十来字。
 *
 * 这里不引 material3-adaptive：本项目只用 BOM 里的基础件，分级本来就是一个阈值问题，
 * 写成纯函数还能单测。
 */
enum class WindowClass { COMPACT, MEDIUM, EXPANDED, WIDE }

fun windowClassOf(widthDp: Int): WindowClass = when {
    widthDp >= 1200 -> WindowClass.WIDE
    widthDp >= 840 -> WindowClass.EXPANDED
    widthDp >= 600 -> WindowClass.MEDIUM
    else -> WindowClass.COMPACT
}
