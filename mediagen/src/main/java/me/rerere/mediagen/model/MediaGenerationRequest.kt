package me.rerere.mediagen.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 与供应商无关的媒体生成请求，图像和视频共用。
 *
 * [resolution] 对视频是清晰度档位（如 `1080P`），对图像是档位或像素尺寸（如 `2K`、`1536x1024`）；[count] 是期望的
 * 产出数量；[quality] 是图像的质量档位，取值由厂商决定（如 OpenAI 的 `low` / `medium` / `high`）。
 * 适配器不支持的公共字段会在提交前直接报错，而不是静默忽略。
 *
 * [extraParameters] 用于尚未进入公共抽象的供应商参数。适配器会先写入这些参数，再用公共字段
 * 覆盖同名项，避免调用方绕过公共字段的统一语义。
 */
@Serializable
data class MediaGenerationRequest(
    val prompt: String? = null,
    val inputs: List<MediaGenerationInput> = emptyList(),
    val count: Int? = null,
    val resolution: String? = null,
    val aspectRatio: String? = null,
    val quality: String? = null,
    // 出图前做规划的力度（OpenAI 的 low / medium / high），和质量相互独立
    val complexity: String? = null,
    // 产出的文件格式（png、webp、gif、svg）
    val outputFormat: String? = null,
    val durationSeconds: Int? = null,
    val generateAudio: Boolean? = null,
    val watermark: Boolean? = null,
    val seed: Long? = null,
    val promptEnhancement: Boolean? = null,
    val callbackUrl: String? = null,
    val extraParameters: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(!prompt.isNullOrBlank() || inputs.isNotEmpty()) {
            "prompt and inputs cannot both be empty"
        }
        require(count == null || count > 0) { "count must be positive" }
        require(durationSeconds == null || durationSeconds == -1 || durationSeconds > 0) {
            "durationSeconds must be positive or -1"
        }
        require(seed == null || seed >= 0) { "seed must be non-negative" }
    }
}

/**
 * 适配器将要发出的请求，用于在提交前给用户核对。[body] 是 JSON 请求体；multipart 请求（OpenAI 图像编辑）
 * 把各个表单字段放进 [body]，文件字段的值是素材的占位名。
 *
 * 预览里的素材地址都是占位名（`asset-1` 起），不会读取本地文件，也不含 API Key。
 */
data class MediaGenerationRequestPreview(
    val method: String,
    val url: String,
    val body: JsonObject,
    val multipart: Boolean = false,
)
