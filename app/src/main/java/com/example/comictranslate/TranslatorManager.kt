package com.example.comictranslate

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

object TranslatorManager {
    private var translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.INDONESIAN)
            .build()
    )

    // Custom dictionary untuk istilah komik
    private val comicDictionary = mapOf(
        "senpai" to "senpai",
        "sunbae" to "sunbae",
        "oppa" to "oppa",
        "noona" to "noona",
        "hyung" to "hyung",
        "sensei" to "sensei",
        "sama" to "sama",
        "chan" to "chan",
        "kun" to "kun",
        "onee" to "onee",
        "oni" to "oni",
        "ah" to "ah",
        "eh" to "eh",
        "uh" to "uh",
        "hm" to "hm",
        "hmm" to "hmm",
        "ha" to "ha",
        "heh" to "heh",
        "hehe" to "hehe",
        "haha" to "haha",
        "kyaa" to "kyaa",
        "nyaa" to "nyaa",
        "wan" to "wan",
        "kuku" to "kuku",
        "ufufu" to "ufufu",
        "ahaha" to "ahaha",
        "ehh" to "ehh",
        "ehhh" to "ehhh"
    )

    suspend fun translateText(context: Context, text: String, surroundingText: String = ""): String {
        val lowerText = text.lowercase().trim()
        if (comicDictionary.containsKey(lowerText)) {
            return comicDictionary[lowerText]!!
        }

        if (isInterjection(text)) {
            return text
        }

        return try {
            translator.downloadModelIfNeeded().await()
            val rawTranslation = translator.translate(text).await()
            postProcessTranslation(rawTranslation)
        } catch (e: Exception) {
            e.printStackTrace()
            text
        }
    }

    private fun isInterjection(text: String): Boolean {
        val interjections = listOf("ah", "eh", "uh", "hm", "hmm", "ha", "heh", "kyaa", "nya", "wan")
        return text.lowercase().trim() in interjections
    }

    private fun postProcessTranslation(translation: String): String {
        return translation
            .replace("aku aku", "aku")
            .replace("saya aku", "aku")
            .replace("saya saya", "saya")
            .replace(" kamu ", " kau ")
            .replace("saya ingin", "aku mau")
            .replace("saya mau", "aku mau")
            .trim()
    }
}
