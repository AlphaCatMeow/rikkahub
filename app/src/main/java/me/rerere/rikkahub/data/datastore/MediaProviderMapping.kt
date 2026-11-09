package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.MediaApi
import me.rerere.ai.provider.MediaVendor
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.model.MediaKind
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 对话提供商里的图像、视频模型转成 mediagen 的配置。提供商 id 和模型 id 原样沿用，
 * 创作记录、草稿里存的 id 因此仍然有效。没有图像/视频模型、已停用或协议不支持时返回 null。
 */
fun ProviderSetting.toMediaProvider(): MediaGenerationProviderSetting? {
    if (this !is ProviderSetting.OpenAI || !enabled) return null
    val mediaModels = models.mapNotNull { model ->
        val kind = when (model.type) {
            ModelType.IMAGE -> MediaKind.IMAGE
            ModelType.VIDEO -> MediaKind.VIDEO
            else -> return@mapNotNull null
        }
        MediaGenerationModel(modelId = model.modelId, kind = kind, displayName = model.displayName, id = model.id)
    }
    if (mediaModels.isEmpty()) return null

    val vendor = mediaApi?.vendor ?: detectMediaVendor(baseUrl)
    val mediaBaseUrl = mediaApi?.baseUrl?.takeIf { it.isNotBlank() } ?: defaultMediaBaseUrl(vendor, baseUrl)
    // shortcut: 多个 key 时固定用第一个，提交和轮询必须是同一个账号；需要轮换时再接 KeyRoulette
    val key = apiKey.split(KEY_SEPARATOR).firstOrNull { it.isNotBlank() } ?: apiKey
    val setting = when (vendor) {
        MediaVendor.OPENAI -> MediaGenerationProviderSetting.OpenAI(id, name, key, mediaBaseUrl, mediaModels)
        MediaVendor.ALIYUN -> MediaGenerationProviderSetting.Aliyun(
            id, name, key, mediaApi?.workspaceId.orEmpty(), mediaBaseUrl, mediaModels,
        )
        MediaVendor.VOLCENGINE -> MediaGenerationProviderSetting.Volcengine(id, name, key, mediaBaseUrl, mediaModels)
        MediaVendor.MINIMAX -> MediaGenerationProviderSetting.MiniMax(id, name, key, mediaBaseUrl, mediaModels)
        MediaVendor.OPENROUTER -> MediaGenerationProviderSetting.OpenRouter(id, name, key, mediaBaseUrl, mediaModels)
    }
    // 接口不支持的类型（如 OpenAI 接口下的视频模型）不列出来，免得选了也用不了
    val usable = setting.models.filter { it.kind in setting.supportedKinds }
    return if (usable.isEmpty()) null else setting.copyProvider(models = usable)
}

/** 旧的独立媒体提供商迁移成对话提供商，id 不变，接口显式写明，不再靠域名判断 */
fun MediaGenerationProviderSetting.toChatProvider(): ProviderSetting.OpenAI {
    val vendor = when (this) {
        is MediaGenerationProviderSetting.OpenAI -> MediaVendor.OPENAI
        is MediaGenerationProviderSetting.Aliyun -> MediaVendor.ALIYUN
        is MediaGenerationProviderSetting.Volcengine -> MediaVendor.VOLCENGINE
        is MediaGenerationProviderSetting.MiniMax -> MediaVendor.MINIMAX
        is MediaGenerationProviderSetting.OpenRouter -> MediaVendor.OPENROUTER
    }
    return ProviderSetting.OpenAI(
        id = id,
        name = name.ifBlank { this::class.simpleName.orEmpty() },
        apiKey = apiKey,
        // 对话走的兼容接口和媒体接口只差版本段，这里换回对话的；媒体仍用下面显式记下的原地址
        baseUrl = when (vendor) {
            MediaVendor.ALIYUN -> baseUrl
                .replace(MediaGenerationProviderSetting.Aliyun.WORKSPACE_PLACEHOLDER, (this as MediaGenerationProviderSetting.Aliyun).workspaceId)
                .replaceApiSuffix(listOf("/api/v1"), "/compatible-mode/v1")
            MediaVendor.MINIMAX -> baseUrl.replaceApiSuffix(listOf("/v2"), "/v1")
            else -> baseUrl
        },
        models = models.map { model ->
            Model(
                modelId = model.modelId,
                displayName = model.displayName.ifBlank { model.modelId },
                id = model.id,
                type = if (model.kind == MediaKind.VIDEO) ModelType.VIDEO else ModelType.IMAGE,
            )
        },
        mediaApi = MediaApi(
            vendor = vendor,
            baseUrl = baseUrl,
            workspaceId = (this as? MediaGenerationProviderSetting.Aliyun)?.workspaceId.orEmpty(),
        ),
    )
}

internal fun detectMediaVendor(baseUrl: String): MediaVendor {
    val host = baseUrl.toHttpUrlOrNull()?.host?.lowercase() ?: return MediaVendor.OPENAI
    return when {
        host == "openrouter.ai" || host.endsWith(".openrouter.ai") -> MediaVendor.OPENROUTER
        host.endsWith("volces.com") || host.contains("byteplus") -> MediaVendor.VOLCENGINE
        host.endsWith("aliyuncs.com") -> MediaVendor.ALIYUN
        host.contains("minimax") -> MediaVendor.MINIMAX
        else -> MediaVendor.OPENAI
    }
}

// 百炼、MiniMax 的媒体接口和对话的兼容接口不在同一路径下，其余厂商共用对话的 baseUrl
internal fun defaultMediaBaseUrl(vendor: MediaVendor, chatBaseUrl: String): String = when (vendor) {
    MediaVendor.ALIYUN -> chatBaseUrl.replaceApiSuffix(listOf("/compatible-mode/v1", "/v1"), "/api/v1")
    MediaVendor.MINIMAX -> chatBaseUrl.replaceApiSuffix(listOf("/v1"), "/v2")
    else -> chatBaseUrl
}

// 只换路径末尾的版本段，中转站带的路径前缀保留；没有匹配的后缀时直接接在后面
private fun String.replaceApiSuffix(from: List<String>, to: String): String {
    val url = toHttpUrlOrNull() ?: return this
    val path = url.encodedPath.trimEnd('/')
    val prefix = from.firstOrNull { path.endsWith(it) }?.let { path.removeSuffix(it) } ?: path
    return url.newBuilder().encodedPath(prefix + to).build().toString()
}

private val KEY_SEPARATOR = Regex("[\\s,]+")
