package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import me.rerere.ai.provider.MediaApi
import me.rerere.ai.provider.MediaVendor
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.model.MediaKind
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.data.datastore.migration.SettingsJsonMigrator
import me.rerere.rikkahub.data.datastore.migration.mergeMediaProviders
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class MediaProvidersMigrationTest {
    private val aliyun = MediaGenerationProviderSetting.Aliyun(
        id = Uuid.random(),
        name = "Bailian",
        apiKey = "sk-a",
        workspaceId = "ws1",
        models = listOf(
            MediaGenerationModel(modelId = "qwen-image-3.0", kind = MediaKind.IMAGE),
            MediaGenerationModel(modelId = "wan3.0-video", kind = MediaKind.VIDEO),
        ),
    )

    @Test
    fun `legacy media providers round trip with ids intact`() {
        val chat = ProviderSetting.OpenAI(name = "Chat")
        val merged = mergeMediaProviders(
            JsonInstant.encodeToJsonElement<List<ProviderSetting>>(listOf(chat)) as JsonArray,
            JsonInstant.encodeToJsonElement<List<MediaGenerationProviderSetting>>(listOf(aliyun)),
        )
        val providers = JsonInstant.decodeFromJsonElement<List<ProviderSetting>>(merged)

        assertEquals(listOf(chat.id, aliyun.id), providers.map { it.id })
        // 迁移后的对话地址换回兼容接口，媒体地址单独记着
        assertEquals(
            "https://ws1.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
            (providers[1] as ProviderSetting.OpenAI).baseUrl,
        )
        assertNull(providers[0].toMediaProvider())
        val back = providers[1].toMediaProvider() as MediaGenerationProviderSetting.Aliyun
        assertEquals(aliyun.baseUrl, back.baseUrl)
        assertEquals("ws1", back.workspaceId)
        assertEquals(aliyun.models.map { it.id to it.kind }, back.models.map { it.id to it.kind })
    }

    @Test
    fun `existing provider ids are not duplicated`() {
        val providers = JsonInstant.encodeToJsonElement<List<ProviderSetting>>(listOf(aliyun.toChatProvider())) as JsonArray
        val merged = mergeMediaProviders(
            providers,
            JsonInstant.encodeToJsonElement<List<MediaGenerationProviderSetting>>(listOf(aliyun)),
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun `backup json moves media providers into providers`() {
        val json = JsonInstant.encodeToString(Settings(providers = emptyList()))
            .dropLast(1) + ""","mediaGenerationProviders":${
            JsonInstant.encodeToString<List<MediaGenerationProviderSetting>>(listOf(aliyun))
        }}"""
        val settings = JsonInstant.decodeFromString<Settings>(SettingsJsonMigrator.migrate(json))
        assertEquals(listOf(aliyun.id), settings.mediaGenerationProviders.map { it.id })
    }

    @Test
    fun `chat providers expose image and video models by detected vendor`() {
        val provider = ProviderSetting.OpenAI(
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            apiKey = "k1, k2",
            models = listOf(
                Model(modelId = "qwen3-max"),
                Model(modelId = "wan2.5-t2i", type = ModelType.IMAGE),
            ),
        )
        val media = provider.toMediaProvider() as MediaGenerationProviderSetting.Aliyun
        assertEquals("https://dashscope.aliyuncs.com/api/v1", media.baseUrl)
        assertEquals("k1", media.apiKey)
        assertEquals(listOf("wan2.5-t2i"), media.models.map { it.modelId })

        val relay = provider.copy(
            baseUrl = "https://relay.example.com/minimax/v1",
            mediaApi = MediaApi(vendor = MediaVendor.MINIMAX),
            models = listOf(Model(modelId = "hailuo-3", type = ModelType.VIDEO)),
        )
        assertEquals("https://relay.example.com/minimax/v2", relay.toMediaProvider()!!.baseUrl)
        assertNull(provider.copy(enabled = false).toMediaProvider())
        // OpenAI 接口只支持图像，视频模型不会出现在创作模式里
        assertNull(relay.copy(mediaApi = MediaApi(vendor = MediaVendor.OPENAI)).toMediaProvider())
    }
}
