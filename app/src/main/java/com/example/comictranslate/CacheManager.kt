package com.example.comictranslate

import androidx.collection.LruCache

object CacheManager {
    private val cache = LruCache<String, String>(100)

    fun put(key: String, value: String) {
        cache.put(key, value)
    }

    fun get(key: String): String? {
        return cache.get(key)
    }
}
