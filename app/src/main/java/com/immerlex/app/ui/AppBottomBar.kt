package com.immerlex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 底部导航的一格。列表正中间那一格会画成抬起的主色圆，其余四格是图标 + 小字。 */
data class NavSlot(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

/**
 * 五格中间主页的底栏：一条浮起的圆角胶囊，正中一格是一枚压在胶囊上沿的主色圆，
 * 一眼就知道"主页在中间"。选中项用主色、未选用灰，胶囊本身跟随深浅色。
 */
@Composable
fun AppBottomBar(
    slots: List<NavSlot>,
    currentRoute: String?,
    onSelect: (NavSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val centerIndex = slots.size / 2
    val bottomInset = NavigationBarDefaults.windowInsets.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp + bottomInset),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp + bottomInset)
                .fillMaxWidth()
                .height(58.dp)
                .shadow(8.dp, RoundedCornerShape(29.dp))
                .clip(RoundedCornerShape(29.dp))
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp + bottomInset)
                .fillMaxWidth()
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            slots.forEachIndexed { index, slot ->
                val selected = currentRoute == slot.route
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (index == centerIndex) {
                        CenterItem(
                            selected = selected,
                            icon = slot.icon,
                            labelRes = slot.labelRes,
                            onClick = { onSelect(slot) },
                        )
                    } else {
                        SideItem(
                            selected = selected,
                            icon = slot.icon,
                            labelRes = slot.labelRes,
                            onClick = { onSelect(slot) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterItem(selected: Boolean, icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .offset(y = (-8).dp)
            .size(54.dp)
            .shadow(if (selected) 10.dp else 5.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = stringResource(labelRes), tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun SideItem(selected: Boolean, icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = stringResource(labelRes), tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            stringResource(labelRes),
            fontSize = 11.sp,
            color = tint,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
