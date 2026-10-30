package com.immerlex.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.immerlex.app.BuildConfig
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.RootHandledInsets

private data class AboutSection(val title: Int, val body: Int)

private val SECTIONS = listOf(
    AboutSection(R.string.about_checkin_title, R.string.about_checkin_body),
    AboutSection(R.string.about_home_title, R.string.about_home_body),
    AboutSection(R.string.about_reading_title, R.string.about_reading_body),
    AboutSection(R.string.about_quiz_title, R.string.about_quiz_body),
    AboutSection(R.string.about_reminder_title, R.string.about_reminder_body),
    AboutSection(R.string.about_backup_title, R.string.about_backup_body),
    AboutSection(R.string.about_bible_title, R.string.about_bible_body),
    AboutSection(R.string.about_shelf_title, R.string.about_shelf_body),
    AboutSection(R.string.about_room_title, R.string.about_room_body),
    AboutSection(R.string.about_bank_title, R.string.about_bank_body),
)

/** 界面上的说明文字集中放这里，主页、阅读页和设置里只留操作必需的一两句。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = RootHandledInsets,
        topBar = {
            TopAppBar(
                windowInsets = RootHandledInsets,
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.bible_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.app_subtitle),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = ImmerlexColors.Meta,
                )
            }
            Text(
                stringResource(R.string.about_lead),
                style = MaterialTheme.typography.bodyMedium,
                color = ImmerlexColors.Body,
            )
            SECTIONS.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(section.title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(section.body),
                        style = MaterialTheme.typography.bodySmall,
                        color = ImmerlexColors.Body,
                    )
                }
            }
        }
    }
}
