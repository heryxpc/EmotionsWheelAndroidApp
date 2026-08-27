package com.emotionwheel.app.data.catalog

import android.content.Context
import kotlinx.serialization.json.Json
import java.text.Normalizer

/**
 * The 90 emotions of the wheel, read once from `assets/emotions.json`.
 * Immutable and cheap to query — the whole catalog is a few kilobytes.
 */
class EmotionCatalog private constructor(val emotions: List<Emotion>) {

    private val byId: Map<String, Emotion> = emotions.associateBy { it.id }

    private val byFamily: Map<EmotionFamily, List<Emotion>> =
        emotions.groupBy { it.family }.mapValues { (_, list) -> list.sortedWith(ringOrder) }

    /** Search index: accent-free label -> emotion, so "desilusion" finds "desilusión". */
    private val searchKeys: List<Pair<String, Emotion>> =
        emotions.map { normalize(it.label) to it }

    operator fun get(id: String): Emotion? = byId[id]

    fun require(id: String): Emotion =
        byId[id] ?: error("Unknown emotion id '$id'")

    fun core(family: EmotionFamily): Emotion =
        byFamily.getValue(family).first { it.level == EmotionLevel.CORE }

    /** All 15 emotions of a family, core first then middle ring then outer ring. */
    fun family(family: EmotionFamily): List<Emotion> = byFamily.getValue(family)

    /** The seven emotions of one ring of one family, in clockwise order. */
    fun ring(family: EmotionFamily, level: Int): List<Emotion> =
        byFamily.getValue(family).filter { it.level == level }

    /** Resolves a name typed by hand or read from a CSV, ignoring case and accents. */
    fun findByLabel(label: String): Emotion? {
        val key = normalize(label)
        return searchKeys.firstOrNull { it.first == key }?.second
    }

    /** Substring match over labels, ordered so shorter (closer) matches come first. */
    fun search(query: String): List<Emotion> {
        val key = normalize(query)
        if (key.isBlank()) return emptyList()
        return searchKeys
            .filter { it.first.contains(key) }
            .sortedWith(compareBy({ !it.first.startsWith(key) }, { it.first.length }))
            .map { it.second }
    }

    companion object {
        private const val ASSET_NAME = "emotions.json"
        private val json = Json { ignoreUnknownKeys = true }

        @Volatile
        private var instance: EmotionCatalog? = null

        fun get(context: Context): EmotionCatalog =
            instance ?: synchronized(this) {
                instance ?: load(context.applicationContext).also { instance = it }
            }

        private fun load(context: Context): EmotionCatalog =
            parse(context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() })

        /** Builds a catalog straight from the asset's text, so tests need no Context. */
        fun parse(text: String): EmotionCatalog {
            val file = json.decodeFromString<EmotionCatalogFile>(text)
            check(file.emotions.size == 90) {
                "emotions.json holds ${file.emotions.size} emotions, expected 90"
            }
            return EmotionCatalog(file.emotions)
        }

        /** Core first, then rings, each ring clockwise. */
        private val ringOrder = compareBy<Emotion>({ it.level }, { it.index })

        fun normalize(value: String): String =
            Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
    }
}
