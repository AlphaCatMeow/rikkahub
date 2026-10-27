package me.rerere.mediagen.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** 模型产出的媒体类型。 */
@Serializable
enum class MediaKind {
    IMAGE,
    VIDEO,
}

/**
 * 厂商下的一个模型。[modelId] 是下发给接口的模型名，[kind] 决定适配器走图像还是视频协议。
 */
@Serializable
data class MediaGenerationModel(
    val modelId: String,
    val kind: MediaKind,
    val displayName: String = "",
    val id: Uuid = Uuid.random(),
)

/**
 * 从厂商模型列表接口拉到的候选模型，由用户挑选后转成 [MediaGenerationModel] 保存。
 *
 * [recognized] 为 false 表示列表接口没有区分模型类型，按名字也看不出是图像/视频模型
 * （比如 OpenAI 兼容中转返回的聊天模型），界面默认收起这些项。
 */
data class RemoteMediaModel(
    val modelId: String,
    val kind: MediaKind,
    val displayName: String = "",
    val recognized: Boolean = true,
) {
    fun toModel(): MediaGenerationModel =
        MediaGenerationModel(modelId = modelId, kind = kind, displayName = displayName)
}
