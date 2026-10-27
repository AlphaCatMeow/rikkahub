package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.CloudDownload
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.model.MediaKind
import me.rerere.mediagen.model.RemoteMediaModel
import me.rerere.mediagen.provider.MediaGenerationManager
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.cardGroupItemShape
import org.koin.compose.koinInject

val MediaGenerationProviderSetting.typeName: String
    @Composable get() = when (this) {
        is MediaGenerationProviderSetting.OpenAI -> "OpenAI"
        is MediaGenerationProviderSetting.Aliyun -> stringResource(R.string.media_provider_type_aliyun)
        is MediaGenerationProviderSetting.Volcengine -> stringResource(R.string.media_provider_type_volcengine)
        is MediaGenerationProviderSetting.MiniMax -> "MiniMax"
        is MediaGenerationProviderSetting.OpenRouter -> "OpenRouter"
    }

val MediaKind.label: String
    @Composable get() = when (this) {
        MediaKind.IMAGE -> stringResource(R.string.media_kind_image)
        MediaKind.VIDEO -> stringResource(R.string.video)
    }

// 各分页共用：可滚动，底部留出悬浮工具栏的位置
@Composable
private fun ConfigureColumn(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 128.dp),
        content = content,
    )
}

@Composable
fun MediaGenerationProviderBasicConfigure(
    setting: MediaGenerationProviderSetting,
    modifier: Modifier = Modifier,
    onValueChange: (MediaGenerationProviderSetting) -> Unit
) {
    ConfigureColumn(modifier) {
        CardGroup {
            // 类型在新建时选定，之后不能改
            item(
                headlineContent = { Text(stringResource(R.string.setting_media_page_provider_type)) },
                trailingContent = { CardGroupValue(setting.typeName) },
            )

            formItem(
                headlineContent = { Text(stringResource(R.string.setting_media_page_provider_name)) },
            ) {
                OutlinedTextField(
                    value = setting.name,
                    onValueChange = { onValueChange(setting.copyProvider(name = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            formItem(headlineContent = { Text("API Key") }) {
                OutlinedTextField(
                    value = setting.apiKey,
                    onValueChange = { onValueChange(setting.copyProvider(apiKey = it.trim())) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            formItem(headlineContent = { Text("Base URL") }) {
                OutlinedTextField(
                    value = setting.baseUrl,
                    onValueChange = { onValueChange(setting.copyProvider(baseUrl = it.trim())) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (setting is MediaGenerationProviderSetting.Aliyun) {
                formItem(
                    headlineContent = { Text(stringResource(R.string.setting_media_page_workspace_id)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.setting_media_page_workspace_id_desc,
                                MediaGenerationProviderSetting.Aliyun.WORKSPACE_PLACEHOLDER,
                            )
                        )
                    }
                ) {
                    OutlinedTextField(
                        value = setting.workspaceId,
                        onValueChange = { onValueChange(setting.copy(workspaceId = it.trim())) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }
    }
}

@Composable
fun MediaGenerationProviderModelsConfigure(
    setting: MediaGenerationProviderSetting,
    modifier: Modifier = Modifier,
    manager: MediaGenerationManager = koinInject(),
    onValueChange: (MediaGenerationProviderSetting) -> Unit
) {
    val models = setting.models
    var showRemoteModels by remember { mutableStateOf(false) }
    // 按枚举顺序排列，保证按钮组的顺序稳定
    val kinds = MediaKind.entries.filter { it in setting.supportedKinds }

    fun updateModels(models: List<MediaGenerationModel>) {
        onValueChange(setting.copyProvider(models = models))
    }

    ConfigureColumn(modifier) {
        CardGroup {
            models.forEach { model ->
                formItem {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = model.modelId,
                            onValueChange = { value ->
                                updateModels(models.map { if (it.id == model.id) model.copy(modelId = value.trim()) else it })
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.setting_media_page_model_id)) },
                            singleLine = true
                        )
                        IconButton(onClick = { updateModels(models.filter { it.id != model.id }) }) {
                            Icon(HugeIcons.Delete01, stringResource(R.string.delete))
                        }
                    }
                    // 只支持一种类型的厂商不需要选择
                    if (kinds.size > 1) {
                        ConnectedChoiceButtons(
                            options = kinds,
                            selected = model.kind,
                            onSelect = { kind ->
                                updateModels(models.map { if (it.id == model.id) model.copy(kind = kind) else it })
                            },
                        ) { kind, selected ->
                            ConnectedChoiceLabel(text = kind.label, selected = selected)
                        }
                    }
                }
            }

            if (manager.supportsModelListing(setting)) {
                item(
                    onClick = { showRemoteModels = true },
                    headlineContent = { Text(stringResource(R.string.setting_media_page_fetch_models)) },
                    supportingContent = { Text(stringResource(R.string.setting_media_page_fetch_models_desc)) },
                    leadingContent = { CardGroupIcon(HugeIcons.CloudDownload) },
                )
            }

            item(
                onClick = if (kinds.isNotEmpty()) {
                    { updateModels(models + MediaGenerationModel(modelId = "", kind = kinds.first())) }
                } else null,
                headlineContent = { Text(stringResource(R.string.setting_media_page_add_model)) },
                supportingContent = { Text(stringResource(R.string.setting_media_page_models_desc)) },
                leadingContent = { CardGroupIcon(HugeIcons.Add01) },
            )
        }
    }

    if (showRemoteModels) {
        RemoteMediaModelSheet(
            setting = setting,
            manager = manager,
            onToggle = { remote, add ->
                updateModels(
                    if (add) {
                        models + remote.toModel()
                    } else {
                        models.filterNot { it.matches(remote) }
                    }
                )
            },
            onDismiss = { showRemoteModels = false },
        )
    }
}

private fun MediaGenerationModel.matches(remote: RemoteMediaModel): Boolean =
    modelId == remote.modelId && kind == remote.kind

private sealed interface RemoteModelsState {
    data object Loading : RemoteModelsState
    data class Loaded(val models: List<RemoteMediaModel>) : RemoteModelsState
    data class Failed(val message: String) : RemoteModelsState
}

// 用的是详情页里还没保存的草稿凭据，填完 Key 不用先保存就能拉
@Composable
private fun RemoteMediaModelSheet(
    setting: MediaGenerationProviderSetting,
    manager: MediaGenerationManager,
    onToggle: (RemoteMediaModel, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var reloadKey by remember { mutableIntStateOf(0) }
    val state by produceState<RemoteModelsState>(RemoteModelsState.Loading, reloadKey) {
        value = RemoteModelsState.Loading
        value = manager.listModels(setting).fold(
            onSuccess = { RemoteModelsState.Loaded(it) },
            onFailure = { RemoteModelsState.Failed(it.message ?: it.toString()) },
        )
    }
    var filterText by remember { mutableStateOf("") }
    var showUnrecognized by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(8.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_provider_page_avaliable_models),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )

            when (val current = state) {
                RemoteModelsState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator()
                }

                is RemoteModelsState.Failed -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        text = stringResource(R.string.setting_media_page_fetch_failed, current.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = { reloadKey++ }) {
                        Text(stringResource(R.string.setting_media_page_retry))
                    }
                }

                is RemoteModelsState.Loaded -> {
                    val keywords = filterText.split(" ").filter { it.isNotBlank() }
                    val unrecognizedCount = current.models.count { !it.recognized }
                    val visible = current.models.filter { model ->
                        (showUnrecognized || model.recognized) && keywords.all { keyword ->
                            model.modelId.contains(keyword, ignoreCase = true) ||
                                model.displayName.contains(keyword, ignoreCase = true)
                        }
                    }

                    if (unrecognizedCount > 0) {
                        FilterChip(
                            selected = showUnrecognized,
                            onClick = { showUnrecognized = !showUnrecognized },
                            label = {
                                Text(stringResource(R.string.setting_media_page_show_unrecognized, unrecognizedCount))
                            },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }

                    if (visible.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.setting_media_page_fetch_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                            contentPadding = PaddingValues(8.dp),
                        ) {
                            itemsIndexed(visible, key = { _, model -> "${model.kind}:${model.modelId}" }) { index, model ->
                                RemoteModelRow(
                                    model = model,
                                    added = setting.models.any { it.matches(model) },
                                    showKind = setting.supportedKinds.size > 1,
                                    shape = cardGroupItemShape(index, visible.size),
                                    onToggle = { add -> onToggle(model, add) },
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                label = { Text(stringResource(R.string.setting_provider_page_filter_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun RemoteModelRow(
    model: RemoteMediaModel,
    added: Boolean,
    showKind: Boolean,
    shape: Shape,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        ) {
            AutoAIIcon(model.modelId, Modifier.size(32.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(text = model.modelId, style = MaterialTheme.typography.titleSmall)
                if (model.displayName.isNotBlank() && model.displayName != model.modelId) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showKind) {
                    Tag(type = TagType.INFO) { Text(model.kind.label) }
                }
            }
            FilledTonalIconToggleButton(checked = added, onCheckedChange = onToggle) {
                Icon(if (added) HugeIcons.Tick01 else HugeIcons.Add01, null)
            }
        }
    }
}
