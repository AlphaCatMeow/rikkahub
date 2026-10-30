package me.rerere.mediagen.provider.providers

import me.rerere.mediagen.model.MediaGenerationInput
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.math.sqrt

internal fun imageMimeType(format: String?, fallback: String): String = when (format?.lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    else -> fallback
}

// 结果地址通常带签名参数，扩展名在 path 末尾
internal fun String.urlFileExtension(): String? =
    substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").takeIf(String::isNotEmpty)

/**
 * 把图片输入转成 JSON 接口可直接引用的地址：公网地址和 data URI 原样返回，本地文件读出后编码成 data URI。
 * 会读取整个文件，需要在 IO 线程调用。
 */
internal fun MediaGenerationInput.Image.toUrlOrDataUri(): String {
    if (REMOTE_PREFIXES.any { url.startsWith(it, ignoreCase = true) }) return url
    val file = File(url.removePrefix("file://"))
    require(file.exists()) { "Image file does not exist: $url" }
    val mimeType = LOCAL_IMAGE_MIME_TYPES[file.extension.lowercase()]
        ?: throw IllegalArgumentException("Unsupported image file type: ${file.extension}")
    return "data:$mimeType;base64,${Base64.encode(file.readBytes())}"
}

private val REMOTE_PREFIXES = listOf("http://", "https://", "data:")

private val LOCAL_IMAGE_MIME_TYPES = mapOf(
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "webp" to "image/webp",
    "bmp" to "image/bmp",
    "gif" to "image/gif",
    "tif" to "image/tiff",
    "tiff" to "image/tiff",
    "heic" to "image/heic",
    "heif" to "image/heif",
)

/**
 * 把公共字段里的 resolution 和 aspectRatio 合成一个 size，给只有尺寸字段、没有比例字段的接口用：
 * - 没有比例，或比例是 `auto` 这类非固定取值：resolution 原样返回；
 * - 有比例且 resolution 为空或是档位（`2K`）：按档位换算成像素尺寸，为空时按 [defaultEdge] 换算；
 * - resolution 已经是像素尺寸又给了比例：像素尺寸本身就决定了比例，两者冲突时报错。
 *
 * 换算出的尺寸写作 `宽x高`，宽高都是 16 的倍数，总像素不超过边长为档位的正方形。
 */
internal fun combineImageSize(
    resolution: String?,
    aspectRatio: String?,
    defaultEdge: Int,
    provider: String,
): String? {
    val size = resolution?.trim()?.takeIf(String::isNotEmpty)
    val ratio = aspectRatio?.let(::parseAspectRatio) ?: return size
    require(size == null || !size.isPixelSize()) {
        "$provider image generation cannot take both a pixel resolution ($size) and an aspect ratio ($aspectRatio)"
    }
    val edge = when {
        size == null || size.equals("auto", ignoreCase = true) -> defaultEdge
        else -> requireNotNull(size.tierEdge()) { "$provider image generation does not recognize resolution $size" }
    }
    val (width, height) = pixelSizeOf(ratio, edge)
    return "${width}x$height"
}

/** `1536x1024`、`1536*1024` 这样的像素尺寸。 */
internal fun String.isPixelSize(): Boolean = PIXEL_SIZE.matches(trim())

/** 档位对应的正方形边长：`1K` 是 1024，`1.5K` 是 1536，`512` 是 512；不是档位时返回 null。 */
internal fun String.tierEdge(): Int? {
    val match = SIZE_TIER.matchEntire(trim()) ?: return null
    match.groupValues[1].takeIf(String::isNotEmpty)?.let { return (it.toDouble() * 1024).toInt() }
    return match.groupValues[2].toIntOrNull()
}

/** `16:9` 解析成宽 / 高；`auto`、`adaptive` 这类不是固定比例的取值返回 null。 */
internal fun parseAspectRatio(value: String): Double? {
    val parts = value.split(':')
    if (parts.size != 2) return null
    val width = parts[0].trim().toDoubleOrNull() ?: return null
    val height = parts[1].trim().toDoubleOrNull() ?: return null
    return if (width > 0 && height > 0) width / height else null
}

// 向下取整到 16 的倍数，保证总像素不超过 edge * edge；加一点余量，免得 768.0 算成 767.99 被取整到 752
private fun pixelSizeOf(ratio: Double, edge: Int): Pair<Int, Int> {
    val area = edge.toDouble() * edge
    val width = (sqrt(area * ratio) + 1e-6).toInt() / 16 * 16
    val height = (sqrt(area / ratio) + 1e-6).toInt() / 16 * 16
    return width.coerceAtLeast(16) to height.coerceAtLeast(16)
}

private val PIXEL_SIZE = Regex("""\d+\s*[xX*]\s*\d+""")
private val SIZE_TIER = Regex("""(\d+(?:\.\d+)?)\s*[kK]|(\d+)""")
