package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.TranslationHistoryEntity
import me.rerere.rikkahub.data.model.MediaCreationSession
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.pages.mediacreation.MediaCreationSessionsVM
import me.rerere.rikkahub.ui.pages.mediacreation.RenameSessionDialog
import me.rerere.rikkahub.utils.toLocalString
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class DrawerSection { CHAT, IMAGE, TRANSLATION }

@Composable
internal fun MediaDrawerList(
    sessions: List<MediaCreationSession>?,
    vm: MediaCreationSessionsVM,
    navController: Navigator,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    var renaming by remember { mutableStateOf<MediaCreationSession?>(null) }
    var deleting by remember { mutableStateOf<MediaCreationSession?>(null) }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (header != null) item(key = "header") { header() }
        item(key = "new") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        vm.newSession { id ->
                            navController.navigate(Screen.MediaCreation(id.toString())) { launchSingleTop = true }
                        }
                    },
                ) { Text("新建生图") }
                IconButton(onClick = { navController.navigate(Screen.SettingMedia) }) {
                    Icon(
                        HugeIcons.Settings03,
                        contentDescription = stringResource(R.string.media_creation_page_provider_settings),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (sessions == null) {
            item(key = "loading") { ContainedLoadingIndicator() }
        } else if (sessions.isEmpty()) {
            item(key = "empty") { DrawerEmptyState("暂无生图会话") }
        }
        itemsIndexed(sessions.orEmpty(), key = { _, session -> session.id.toString() }) { index, session ->
            Column {
                val date = session.updateAt.drawerDate()
                if (index == 0 || sessions?.get(index - 1)?.updateAt?.drawerDate() != date) {
                    DrawerDateHeader(date)
                }
                DrawerSessionRow(
                    title = session.title.ifBlank { "新建生图" },
                    running = session.activeCount > 0,
                    onClick = {
                        navController.navigate(Screen.MediaCreation(session.id.toString())) { launchSingleTop = true }
                    },
                    onRename = { renaming = session },
                    onDelete = { deleting = session },
                )
            }
        }
    }
    renaming?.let { session ->
        RenameSessionDialog(
            initialTitle = session.title,
            onConfirm = { vm.rename(session.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { session ->
        DrawerDeleteDialog(
            text = stringResource(
                if (session.activeCount > 0) R.string.media_creation_page_delete_session_active_text
                else R.string.media_creation_page_delete_session_text,
                session.title.ifBlank { "新建生图" },
                session.nodeCount,
            ),
            onConfirm = { vm.delete(session.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
internal fun TranslationDrawerList(
    entries: LazyPagingItems<TranslationHistoryEntity>,
    navController: Navigator,
    onDelete: (TranslationHistoryEntity) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    var deleting by remember { mutableStateOf<TranslationHistoryEntity?>(null) }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (header != null) item(key = "header") { header() }
        item(key = "new") {
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { navController.navigate(Screen.Translator) },
            ) { Text("新建翻译") }
        }
        if (entries.loadState.refresh is LoadState.Loading) {
            item(key = "loading") { ContainedLoadingIndicator() }
        } else if (entries.loadState.refresh is LoadState.Error) {
            item(key = "error") { TextButton(onClick = { entries.retry() }) { Text("加载失败，点击重试") } }
        } else if (entries.itemCount == 0) {
            item(key = "empty") { DrawerEmptyState("暂无翻译记录") }
        }
        items(count = entries.itemCount, key = entries.itemKey { it.id }) { index ->
            val entry = entries[index] ?: return@items
            Column {
                val date = Instant.ofEpochMilli(entry.createAt).drawerDate()
                val previous = if (index > 0) entries.peek(index - 1) else null
                if (previous == null || Instant.ofEpochMilli(previous.createAt).drawerDate() != date) {
                    DrawerDateHeader(date)
                }
                DrawerSessionRow(
                    title = entry.sourceText.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                    onClick = { navController.navigate(Screen.TranslationHistory(entry.id)) },
                    onDelete = { deleting = entry },
                )
            }
        }
        if (entries.loadState.append is LoadState.Error) {
            item(key = "retry_append") { TextButton(onClick = { entries.retry() }) { Text("加载失败，点击重试") } }
        }
    }
    deleting?.let { entry ->
        DrawerDeleteDialog(
            text = "删除这条翻译记录？",
            onConfirm = { onDelete(entry); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun DrawerSessionRow(
    title: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (() -> Unit)? = null,
    running: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = { expanded = true },
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (running) ContainedLoadingIndicator(modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onRename != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_rename)) },
                    onClick = { expanded = false; onRename() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

private fun Instant.drawerDate(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun DrawerDateHeader(date: LocalDate) {
    val today = LocalDate.now()
    Text(
        text = when (date) {
            today -> stringResource(R.string.chat_page_today)
            today.minusDays(1) -> stringResource(R.string.chat_page_yesterday)
            else -> date.toLocalString(date.year != today.year)
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun DrawerEmptyState(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun DrawerDeleteDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_delete)) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
