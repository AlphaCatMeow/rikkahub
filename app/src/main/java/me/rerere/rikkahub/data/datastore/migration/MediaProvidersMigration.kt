package me.rerere.rikkahub.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.provider.ProviderSetting
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.data.datastore.DEFAULT_PROVIDERS
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.toChatProvider
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull

/**
 * 独立的媒体提供商并入对话提供商列表。只看旧 key 在不在，不占用 data_version，免得和上游后续的版本号撞车。
 */
class MediaProvidersMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.contains(SettingsStore.MEDIA_GENERATION_PROVIDERS)

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        val media = prefs[SettingsStore.MEDIA_GENERATION_PROVIDERS]
        val stored = prefs[SettingsStore.PROVIDERS]
        // providers 还没写过盘时，读设置用的是内置默认列表，这里同样以它为底
        val providers = if (stored == null) {
            JsonInstant.encodeToJsonElement<List<ProviderSetting>>(DEFAULT_PROVIDERS) as JsonArray
        } else {
            // 提供商列表本身解析不了时什么都不动，旧 key 留着，别把媒体提供商丢了
            runCatching { JsonInstant.parseToJsonElement(stored) as JsonArray }.getOrElse { return currentData }
        }
        val legacy = media?.let { runCatching { JsonInstant.parseToJsonElement(it) }.getOrNull() }
        if (legacy != null) {
            prefs[SettingsStore.PROVIDERS] = JsonInstant.encodeToString(mergeMediaProviders(providers, legacy))
        }
        prefs.remove(SettingsStore.MEDIA_GENERATION_PROVIDERS)
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}

/** 把旧格式的媒体提供商追加到提供商列表末尾；id 已存在的跳过，逐个解析，坏掉的单条跳过 */
internal fun mergeMediaProviders(providers: JsonArray, media: JsonElement): JsonArray {
    val legacy = (media as? JsonArray).orEmpty().mapNotNull {
        runCatching { JsonInstant.decodeFromJsonElement<MediaGenerationProviderSetting>(it) }.getOrNull()
    }
    val existingIds = providers.mapNotNull {
        (it as? JsonObject)?.get("id")?.jsonPrimitiveOrNull?.contentOrNull
    }.toSet()
    val added = legacy
        .filter { it.id.toString() !in existingIds }
        .map { JsonInstant.encodeToJsonElement<ProviderSetting>(it.toChatProvider()) }
    return JsonArray(providers + added)
}
