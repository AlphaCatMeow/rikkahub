package me.rerere.mediagen.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import me.rerere.mediagen.model.MediaGenerationInput
import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.model.MediaGenerationRequest
import me.rerere.mediagen.model.MediaGenerationRequestPreview
import me.rerere.mediagen.model.MediaGenerationTask
import me.rerere.mediagen.model.RemoteMediaModel
import me.rerere.mediagen.provider.providers.aliyun.AliyunMediaGenerationProvider
import me.rerere.mediagen.provider.providers.minimax.MiniMaxMediaGenerationProvider
import me.rerere.mediagen.provider.providers.openai.OpenAIMediaGenerationProvider
import me.rerere.mediagen.provider.providers.openrouter.OpenRouterMediaGenerationProvider
import me.rerere.mediagen.provider.providers.volcengine.VolcengineMediaGenerationProvider
import okhttp3.OkHttpClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MediaGenerationManager(
    client: OkHttpClient = OkHttpClient(),
) {
    private val openAI = OpenAIMediaGenerationProvider(client)
    private val aliyun = AliyunMediaGenerationProvider(client)
    private val volcengine = VolcengineMediaGenerationProvider(client)
    private val miniMax = MiniMaxMediaGenerationProvider(client)
    private val openRouter = OpenRouterMediaGenerationProvider(client)

    suspend fun create(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Result<MediaGenerationTask> = withContext(Dispatchers.Default) {
        unsupportedKind(setting, model) ?: provider(setting).createUnsafe(setting, model, request)
    }

    suspend fun query(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        taskId: String,
    ): Result<MediaGenerationTask> = withContext(Dispatchers.Default) {
        unsupportedKind(setting, model) ?: provider(setting).queryUnsafe(setting, model, taskId)
    }

    fun supportsModelListing(setting: MediaGenerationProviderSetting): Boolean =
        provider(setting).supportsModelListing

    suspend fun listModels(
        setting: MediaGenerationProviderSetting,
    ): Result<List<RemoteMediaModel>> = withContext(Dispatchers.Default) {
        provider(setting).listModelsUnsafe(setting)
            .map { models -> models.filter { it.kind in setting.supportedKinds } }
    }

    /**
     * 给出 [create] 将要发出的请求，不发出网络请求。素材地址按顺序换成 `asset-1`、`asset-2`……，
     * 不会读取本地文件；适配器的校验照常执行，请求不合法时返回失败。
     */
    fun preview(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Result<MediaGenerationRequestPreview> {
        if (model.kind !in setting.supportedKinds) {
            return Result.failure(
                UnsupportedOperationException("${provider(setting).id} does not support ${model.kind} generation")
            )
        }
        val placeholders = request.copy(
            inputs = request.inputs.mapIndexed { index, input ->
                val url = "$PREVIEW_ASSET_PREFIX${index + 1}"
                when (input) {
                    is MediaGenerationInput.Image -> input.copy(url = url)
                    is MediaGenerationInput.Video -> input.copy(url = url)
                    is MediaGenerationInput.Audio -> input.copy(url = url)
                    is MediaGenerationInput.Document -> input.copy(url = url)
                    is MediaGenerationInput.WebPage -> input.copy(url = url)
                    is MediaGenerationInput.Raw -> input
                }
            },
        )
        return runCatching { provider(setting).previewUnsafe(setting, model, placeholders) }
    }

    /** 见 [MediaGenerationProvider.modelParameters]。 */
    suspend fun modelParameters(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
    ): Result<Set<String>?> = withContext(Dispatchers.Default) {
        if (model.kind !in setting.supportedKinds) return@withContext Result.success(null)
        provider(setting).modelParametersUnsafe(setting, model)
    }

    /**
     * 提交请求并跟踪到终态。同步返回结果的供应商只会发出一次终态任务；异步供应商先发出提交结果，
     * 再按 [interval] 轮询。Flow 被取消时，轮询也会立即停止。
     */
    fun generate(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
        interval: Duration = 15.seconds,
    ): Flow<MediaGenerationTask> = flow {
        require(interval.isPositive()) { "interval must be positive" }
        val task = create(setting, model, request).getOrThrow()
        emit(task)
        if (task.isTerminal) return@flow
        delay(interval)
        emitAll(watch(setting, model, task.id, interval))
    }

    /**
     * 轮询并依次发出服务端状态。Flow 被取消时，轮询也会立即停止。
     */
    fun watch(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        taskId: String,
        interval: Duration = 15.seconds,
    ): Flow<MediaGenerationTask> = flow {
        require(interval.isPositive()) { "interval must be positive" }
        while (true) {
            val task = query(setting, model, taskId).getOrThrow()
            emit(task)
            if (task.isTerminal) return@flow
            delay(interval)
        }
    }

    private fun provider(setting: MediaGenerationProviderSetting): MediaGenerationProvider<*> =
        when (setting) {
            is MediaGenerationProviderSetting.OpenAI -> openAI
            is MediaGenerationProviderSetting.Aliyun -> aliyun
            is MediaGenerationProviderSetting.Volcengine -> volcengine
            is MediaGenerationProviderSetting.MiniMax -> miniMax
            is MediaGenerationProviderSetting.OpenRouter -> openRouter
        }

    // 厂商适配器尚未实现该模型的 kind 时返回失败，否则返回 null
    private fun unsupportedKind(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
    ): Result<MediaGenerationTask>? {
        if (model.kind in setting.supportedKinds) return null
        return Result.failure(
            UnsupportedOperationException("${provider(setting).id} does not support ${model.kind} generation")
        )
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun MediaGenerationProvider<*>.createUnsafe(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Result<MediaGenerationTask> =
        (this as MediaGenerationProvider<MediaGenerationProviderSetting>).create(setting, model, request)

    @Suppress("UNCHECKED_CAST")
    private suspend fun MediaGenerationProvider<*>.queryUnsafe(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        taskId: String,
    ): Result<MediaGenerationTask> =
        (this as MediaGenerationProvider<MediaGenerationProviderSetting>).query(setting, model, taskId)

    @Suppress("UNCHECKED_CAST")
    private suspend fun MediaGenerationProvider<*>.listModelsUnsafe(
        setting: MediaGenerationProviderSetting,
    ): Result<List<RemoteMediaModel>> =
        (this as MediaGenerationProvider<MediaGenerationProviderSetting>).listModels(setting)

    @Suppress("UNCHECKED_CAST")
    private fun MediaGenerationProvider<*>.previewUnsafe(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): MediaGenerationRequestPreview =
        (this as MediaGenerationProvider<MediaGenerationProviderSetting>).preview(setting, model, request)

    @Suppress("UNCHECKED_CAST")
    private suspend fun MediaGenerationProvider<*>.modelParametersUnsafe(
        setting: MediaGenerationProviderSetting,
        model: MediaGenerationModel,
    ): Result<Set<String>?> =
        (this as MediaGenerationProvider<MediaGenerationProviderSetting>).modelParameters(setting, model)

    companion object {
        /** 预览里素材地址的前缀，后面跟从 1 开始的序号。 */
        const val PREVIEW_ASSET_PREFIX = "asset-"
    }
}
