package com.example.comictranslate

import androidx.collection.LruCache

object CacheManager {
    // A cache that holds the 100 most recently used translations.
    // The key is the original text (String), and the value is the translated text (String).
    private val cache = LruCache<String, String>(100)

    /**
     * Adds a new translation to the cache.
     * @param key The original text.
     * @param value The translated text.
     */
    fun put(key: String, value: String) {
        cache.put(key, value)
    }

    /**
     * Retrieves a translation from the cache.
     * @param key The original text to look up.
     * @return The translated text if it exists in the cache, otherwise null.
     */
    fun get(key: String): String? {
        return cache.get(key)
    }
}