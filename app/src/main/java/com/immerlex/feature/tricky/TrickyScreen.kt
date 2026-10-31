package com.immerlex.feature.tricky

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.immerlex.app.R
import com.immerlex.app.ui.ImmerlexColors
import com.immerlex.app.ui.RootHandledInsets
import com.immerlex.app.ui.textFor
import com.immerlex.core.tricky.TrickyGroup

private const val MAX_BATCH_TEXT = 4_000
private const val MAX_NEW_WORD = 40

@Composable
fun TrickyScreen(vm: TrickyViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    // 弹层状态放在页面这一层：转屏不该丢掉正在打的字。
    var showBatch by rememberSaveable { mutableStateOf(false) }
    var batchText by rememberSaveable { mutableStateOf("") }
    var addTarget by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> snackbar.showSnackbar(context.textFor(msg)) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = RootHandledInsets) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    stringResource(R.string.tricky_page_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    batchText = ""
                    showBatch = true
                }) {
                    Text(stringResource(R.string.tricky_batch))
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.groups, key = { it.key }) { group ->
                    GroupCard(
                        group = group,
                        onAdd = { addTarget = group.key },
                        onRemove = { vm.removeFromGroup(group, it) },
                        onDelete = { vm.deleteWord(it) },
                    )
                }
                if (state.groups.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.tricky_page_empty),
                            color = ImmerlexColors.Meta,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        )
                    }
                }
            }
        }
    }

    if (showBatch) {
        BatchDialog(
            text = batchText,
            onTextChange = { batchText = it },
            onDismiss = { showBatch = false },
            onSubmit = {
                showBatch = false
                vm.addGroups(batchText)
            },
        )
    }

    addTarget?.let { key ->
        val group = state.groups.firstOrNull { it.key == key }
        if (group == null) {
            addTarget = null
        } else {
            AddWordDialog(
                group = group,
                onDismiss = { addTarget = null },
                onConfirm = {
                    addTarget = null
                    vm.addToGroup(group, it)
                },
            )
        }
    }
}

@Composable
private fun GroupCard(
    group: TrickyGroup,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val paired = group.members.size > 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.tricky_group_size, group.members.size),
                style = MaterialTheme.typography.labelLarge,
                color = ImmerlexColors.Meta,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAdd) { Text(stringResource(R.string.tricky_group_add)) }
        }
        group.members.forEach { member ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(member.word, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        member.gloss.ifBlank { stringResource(R.string.tricky_no_gloss) },
                        style = MaterialTheme.typography.bodySmall,
                        color = ImmerlexColors.Meta,
                    )
                }
                IconButton(onClick = { if (paired) onRemove(member.word) else onDelete(member.word) }) {
                    Icon(
                        if (paired) Icons.Filled.Close else Icons.Filled.Delete,
                        contentDescription = stringResource(
                            if (paired) R.string.tricky_member_remove else R.string.tricky_word_delete,
                        ),
                        tint = ImmerlexColors.Meta,
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tricky_batch)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { onTextChange(it.take(MAX_BATCH_TEXT)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    label = { Text(stringResource(R.string.tricky_batch_label)) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.tricky_batch_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = ImmerlexColors.Meta,
                )
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.tricky_manual_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun AddWordDialog(group: TrickyGroup, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var typed by remember(group.key) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tricky_group_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(MAX_NEW_WORD) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.tricky_manual_label)) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.tricky_manual_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = ImmerlexColors.Meta,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(typed) }, enabled = typed.isNotBlank()) {
                Text(stringResource(R.string.tricky_manual_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
