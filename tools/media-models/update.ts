// 从 LiteLLM 的模型元数据（https://github.com/BerriAI/litellm）取出图像/视频生成模型，
// 生成 ai 模块的 MediaModelCatalog.kt，用于添加模型时自动标成图像或视频类型。
// 用法：bun tools/media-models/update.ts

const SOURCE = "https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json";
const OUTPUT = `${import.meta.dir}/../../ai/src/main/java/me/rerere/ai/registry/MediaModelCatalog.kt`;

// LiteLLM 里有些条目的最后一段只是通用词，不能拿来认模型
const GENERIC = new Set(["text-to-image", "image-to-video", "text-to-video", "reference-to-video"]);

// 和 Kotlin 侧 MediaModelCatalog.normalize 保持一致
export function normalize(modelId: string): string {
  return modelId
    .toLowerCase()
    .split("/")
    .at(-1)!
    .replace(/-(\d{4}-\d{2}-\d{2}|\d{6}|\d{8})$/, "");
}

// 太短、没有版本号或分隔符的（dev、fast、ultra、v3……）会误伤别的模型
function usable(id: string): boolean {
  return id.length >= 5 && /[a-z]/.test(id) && /[\d.\-_]/.test(id) && !GENERIC.has(id);
}

const response = await fetch(SOURCE);
if (!response.ok) throw new Error(`LiteLLM responded ${response.status}`);
const models = (await response.json()) as Record<string, { mode?: string }>;

const image = new Set<string>();
const video = new Set<string>();
for (const [key, model] of Object.entries(models)) {
  const id = normalize(key);
  if (!usable(id)) continue;
  if (model?.mode === "image_generation") image.add(id);
  if (model?.mode === "video_generation") video.add(id);
}

const list = (ids: Set<string>) => [...ids].sort().map((it) => `        "${it}",`).join("\n");
await Bun.write(
  OUTPUT,
  `package me.rerere.ai.registry

// 由 tools/media-models/update.ts 从 LiteLLM 模型元数据生成，不要手改
internal object MediaModelCatalog {
    val IMAGE = setOf(
${list(image)}
    )

    val VIDEO = setOf(
${list(video)}
    )
}
`,
);
console.log(`image ${image.size}, video ${video.size} -> ${OUTPUT}`);
