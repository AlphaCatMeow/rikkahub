package me.rerere.mediagen.provider

import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.model.MediaGenerationRequest
import me.rerere.mediagen.model.MediaGenerationRequestPreview
import me.rerere.mediagen.model.MediaGenerationTask
import me.rerere.mediagen.model.RemoteMediaModel

/**
 * 媒体生成供应商只负责任务的提交与查询，不在网络层内部隐式轮询。
 *
 * 一个供应商对应一个厂商，内部按 [MediaGenerationModel.kind] 路由到图像或视频接口。
 * 同步返回结果的接口（如 OpenAI 图像）在 [create] 里直接给出终态任务，无需实现 [query]。
 *
 * 模型的 kind 是否受支持由 [MediaGenerationManager] 按 [MediaGenerationProviderSetting.supportedKinds] 统一校验。
 *
 * 厂商有模型列表接口时覆盖 [supportsModelListing] 和 [listModels]；没有的只能由用户手动填写模型 ID。
 *
 * [preview] 用和 [create] 相同的组装逻辑给出请求，但不发出请求；传入的素材地址已经换成占位名。
 */
interface MediaGenerationProvider<S : MediaGenerationProviderSetting> {
    val id: String

    suspend fun create(
        setting: S,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Result<MediaGenerationTask>

    suspend fun query(
        setting: S,
        model: MediaGenerationModel,
        taskId: String,
    ): Result<MediaGenerationTask> =
        Result.failure(
            UnsupportedOperationException(
                "$id ${model.kind} generation returns results synchronously and has no task to query"
            )
        )

    val supportsModelListing: Boolean get() = false

    /** 只返回适配器支持的 kind 下的模型。 */
    suspend fun listModels(setting: S): Result<List<RemoteMediaModel>> =
        Result.failure(UnsupportedOperationException("$id does not provide a model list"))

    fun preview(setting: S, model: MediaGenerationModel, request: MediaGenerationRequest): MediaGenerationRequestPreview

    /**
     * 模型声明接受的请求字段名。厂商没有逐模型的参数说明时返回 null，表示不知道，由接口决定。
     */
    suspend fun modelParameters(setting: S, model: MediaGenerationModel): Result<Set<String>?> =
        Result.success(null)
}
