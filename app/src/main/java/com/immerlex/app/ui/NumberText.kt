package com.immerlex.app.ui

/**
 * 涨跌都要看得出来：正数补一个加号，负数自带减号，一分没动写"±0"——
 * 光一个 0 摆在那里，读的人不知道该当成没发生还是当成正好持平。
 */
fun signed(change: Int): String = when {
    change > 0 -> "+$change"
    change < 0 -> "$change"
    else -> "±0"
}
