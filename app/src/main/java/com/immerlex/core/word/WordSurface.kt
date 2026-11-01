package com.immerlex.core.word

/**
 * 按词存的东西（出题素材、易错标记、易混关系）都用这一个函数把用户输入折成主键：
 * 去掉首尾空格、统一小写。大小写不同的两种写法在库里必须是同一行，否则素材会存两份、
 * 查一份、漏一份。
 */
fun wordSurface(raw: String): String = raw.trim().lowercase()
