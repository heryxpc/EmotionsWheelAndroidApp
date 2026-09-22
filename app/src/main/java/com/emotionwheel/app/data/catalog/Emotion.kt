package com.emotionwheel.app.data.catalog

import androidx.annotation.StringRes
import com.emotionwheel.app.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The six core families of the wheel, in clockwise order starting at the top.
 * Constants are English; the label shown to the user comes from string resources.
 */
enum class EmotionFamily(
    @param:StringRes val labelRes: Int,
    val emoji: String,
) {
    SURPRISE(R.string.family_surprise, "😮"),
    ANGER(R.string.family_anger, "😠"),
    JOY(R.string.family_joy, "😊"),
    FEAR(R.string.family_fear, "😰"),
    SADNESS(R.string.family_sadness, "😢"),
    DISGUST(R.string.family_disgust, "🤢"),
    ;

    companion object {
        fun fromIdOrNull(id: String): EmotionFamily? =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) }
    }
}

/** Ring the emotion lives on. */
object EmotionLevel {
    const val CORE = 1
    const val MIDDLE = 2
    const val OUTER = 3
}

/**
 * One of the 90 emotions of the wheel. Loaded from `assets/emotions.json`.
 * [label] and [definition] are Spanish content, everything else is structure.
 */
@Serializable
data class Emotion(
    val id: String,
    val label: String,
    @SerialName("family") val familyId: String,
    val level: Int,
    val index: Int,
    val definition: String,
) {
    val family: EmotionFamily
        get() = requireNotNull(EmotionFamily.fromIdOrNull(familyId)) {
            "Unknown family '$familyId' for emotion '$id'"
        }
}

@Serializable
data class EmotionCatalogFile(
    val version: Int,
    val emotions: List<Emotion>,
)
