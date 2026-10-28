package com.immerlex.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors

@Composable
fun HomeScreen(vm: HomeViewModel, onStartReading: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    // 阅读页打完分回到这一栏，数字必须跟着重算，否则"今日完成"会停在进来时的值。
    LaunchedEffect(Unit) { vm.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineSmall)

        StreakCard(state)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountCard(stringResource(R.string.home_due), state.due, Modifier.weight(1f))
            CountCard(stringResource(R.string.home_done), state.done, Modifier.weight(1f))
        }
        if (state.newWords > 0) {
            Text(
                stringResource(R.string.home_new_words, state.newWords),
                style = MaterialTheme.typography.bodySmall,
                color = ImmerlexColors.Meta,
            )
        }
        MakeupLine(state)

        Button(
            onClick = onStartReading,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
        ) {
            Text(
                stringResource(
                    when {
                        state.loading -> R.string.home_loading
                        !state.hasChapter -> R.string.home_start_first
                        else -> R.string.home_start_reading
                    },
                ),
            )
        }
        if (state.hasChapter) {
            Text(
                stringResource(R.string.home_continue, state.chapterNumber),
                style = MaterialTheme.typography.bodySmall,
                color = ImmerlexColors.Meta,
            )
        }
        Text(
            stringResource(R.string.home_explain),
            style = MaterialTheme.typography.bodySmall,
            color = ImmerlexColors.Meta,
        )
    }
}

@Composable
private fun StreakCard(state: HomeState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.home_streak, state.streak),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(if (state.checkedIn) R.string.home_checked_in else R.string.home_not_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.checkedIn) MaterialTheme.colorScheme.primary else ImmerlexColors.Meta,
            )
        }
    }
}

@Composable
private fun CountCard(label: String, value: Int, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = ImmerlexColors.Meta)
        }
    }
}

/** 补卡是"漏一天不归零"的唯一机制，所以它用没用过要摆在明面上。 */
@Composable
private fun MakeupLine(state: HomeState) {
    val text = when {
        state.makeupPending -> R.string.home_makeup_pending
        state.makeupUsedThisWeek -> R.string.home_makeup_used
        else -> R.string.home_makeup_unused
    }
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = ImmerlexColors.Body)
}
