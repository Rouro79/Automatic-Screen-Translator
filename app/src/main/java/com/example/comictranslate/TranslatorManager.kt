package com.example.comictranslate

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

object TranslatorManager {
    private var translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH) // or detect dynamically
            .setTargetLanguage(TranslateLanguage.INDONESIAN)
            .build()
    )

    /**
     * Translates text using coroutines for cleaner asynchronous code.
     * This is a suspend function, so it must be called from a coroutine.
     *
     * @param context The application context.
     * @param text The text to translate.
     * @return The translated string.
     */
    suspend fun translateText(context: Context, text: String): String {
        return try {
            // Ensure the model is downloaded. await() suspends the coroutine until the task is done.
            translator.downloadModelIfNeeded().await()
            // Translate the text and await the result.
            translator.translate(text).await()
        } catch (e: Exception) {
            e.printStackTrace()
            // Return original text or an error message on failure
            text
        }
    }
}