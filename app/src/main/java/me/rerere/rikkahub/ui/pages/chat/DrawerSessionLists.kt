package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Translate
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.TranslationHistoryEntity
import me.rerere.rikkahub.data.model.MediaCreationSession
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.pages.mediacreation.MediaCreationSessionsVM
import me.rerere.rikkahub.ui.pages.mediacreation.RenameSessionDialog
import me.rerere.rikkahub.utils.toLocalString
import me.rerere.ui.components.Tooltip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * 侧边栏三个分区（聊天、生图、翻译）共用同一套布局，切换分区时只有列表内容变化：
 * 分区切换按钮 → 固定高度的操作栏（新建按钮 + 筛选 + 工具按钮）→ 列表。
 * 列表里的行、日期分组头、空状态和加载状态都用这里的组件，保证行高和间距一致。
 */

internal enum class DrawerSection { CHAT, IMAGE, TRANSLATION }

internal val DrawerSection.label: String
    @Composable get() = stringResource(
        when (this) {
            DrawerSection.CHAT -> R.string.drawer_section_chat
            DrawerSection.IMAGE -> R.string.drawer_section_image
            DrawerSection.TRANSLATION -> R.string.drawer_section_translation
        }
    )

internal val DrawerSection.icon: ImageVector
    get() = when (this) {
        DrawerSection.CHAT -> HugeIcons.BubbleChat
        DrawerSection.IMAGE -> HugeIcons.Image02
        DrawerSection.TRANSLATION -> HugeIcons.Translate
    }

/** 操作栏的高度，各分区相同。 */
private val DrawerSectionBarHeight = 40.dp

/** 列表行的最小高度，各分区相同。 */
private val DrawerItemMinHeight = 44.dp

/** 列表行和分组头里文字的左右边距。 */
private val DrawerItemHorizontalPadding = 16.dp

/** 列表项之间的间距。 */
internal val DrawerListSpacing = 8.dp

/**
 * 分区的操作栏：左边是新建按钮，中间是可横向滚动的筛选项，右边是工具按钮。没有筛选项或工具按钮时位置留空，
 * 高度固定，切换分区时下面的列表不会上下移动。
 */
@Composable
internal fun DrawerSectionBar(
    newLabel: String,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
    filters: (LazyListScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DrawerSectionBarHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onNew,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.height(DrawerSectionBarHeight),
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(HugeIcons.Add01, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = newLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
        if (filters != null) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = filters,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        actions()
    }
}

/** 操作栏右侧的圆形工具按钮。 */
@Composable
internal fun DrawerToolButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(DrawerSectionBarHeight),
    ) {
        Tooltip(tooltip = { Text(contentDescription) }) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * 列表里的一行。[menu] 放长按后弹出的菜单，菜单锚定在这一行上。
 */
@Composable
internal fun DrawerItemRow(
    title: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
    menu: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DrawerItemMinHeight)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DrawerItemHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        menu()
    }
}

/** 列表里的分组头：日期或「置顶」。 */
@Composable
internal fun DrawerGroupHeader(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DrawerItemHorizontalPadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun DrawerEmptyState(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = DrawerItemHorizontalPadding, vertical = 16.dp),
        )
    }
}

@Composable
internal fun DrawerLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        ContainedLoadingIndicator()
    }
}

@Composable
private fun DrawerRetry(onRetry: () -> Unit) {
    TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.drawer_load_failed_retry))
    }
}

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
    val untitled = stringResource(R.string.media_creation_page_new_session)
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DrawerListSpacing),
    ) {
        if (header != null) item(key = "header", contentType = "header") { header() }
        if (sessions == null) {
            item(key = "loading", contentType = "state") { DrawerLoading() }
        } else if (sessions.isEmpty()) {
            item(key = "empty", contentType = "state") {
                DrawerEmptyState(stringResource(R.string.drawer_empty_images))
            }
        }
        itemsIndexed(
            items = sessions.orEmpty(),
            key = { _, session -> session.id.toString() },
            contentType = { _, _ -> "item" },
        ) { index, session ->
            Column(verticalArrangement = Arrangement.spacedBy(DrawerListSpacing)) {
                val date = session.updateAt.drawerDate()
                if (index == 0 || sessions?.get(index - 1)?.updateAt?.drawerDate() != date) {
                    DrawerDateHeader(date)
                }
                DrawerSessionRow(
                    title = session.title.ifBlank { untitled },
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
                session.title.ifBlank { untitled },
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
        verticalArrangement = Arrangement.spacedBy(DrawerListSpacing),
    ) {
        if (header != null) item(key = "header", contentType = "header") { header() }
        when {
            entries.loadState.refresh is LoadState.Loading && entries.itemCount == 0 ->
                item(key = "loading", contentType = "state") { DrawerLoading() }

            entries.loadState.refresh is LoadState.Error ->
                item(key = "error", contentType = "state") { DrawerRetry { entries.retry() } }

            entries.loadState.refresh is LoadState.NotLoading && entries.itemCount == 0 ->
                item(key = "empty", contentType = "state") {
                    DrawerEmptyState(stringResource(R.string.drawer_empty_translations))
                }
        }
        items(
            count = entries.itemCount,
            key = entries.itemKey { it.id },
            contentType = { "item" },
        ) { index ->
            val entry = entries[index] ?: return@items
            Column(verticalArrangement = Arrangement.spacedBy(DrawerListSpacing)) {
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
            item(key = "retry_append", contentType = "state") { DrawerRetry { entries.retry() } }
        }
    }
    deleting?.let { entry ->
        DrawerDeleteDialog(
            text = stringResource(R.string.drawer_delete_translation_confirm),
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
    DrawerItemRow(
        title = title,
        onClick = onClick,
        onLongClick = { expanded = true },
        trailing = {
            if (running) ContainedLoadingIndicator(modifier = Modifier.size(20.dp))
        },
        menu = {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (onRename != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_rename)) },
                        leadingIcon = { Icon(HugeIcons.PencilEdit01, null) },
                        onClick = { expanded = false; onRename() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    leadingIcon = { Icon(HugeIcons.Delete01, null) },
                    onClick = { expanded = false; onDelete() },
                )
            }
        },
    )
}

private fun Instant.drawerDate(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun DrawerDateHeader(date: LocalDate) {
    val today = LocalDate.now()
    DrawerGroupHeader(
        label = when (date) {
            today -> stringResource(R.string.chat_page_today)
            today.minusDays(1) -> stringResource(R.string.chat_page_yesterday)
            else -> date.toLocalString(date.year != today.year)
        },
    )
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
