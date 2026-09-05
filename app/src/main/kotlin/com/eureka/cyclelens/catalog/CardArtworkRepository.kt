package com.eureka.cyclelens.catalog

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import cyclelens.core.CardId
import java.io.IOException
import java.util.LinkedHashMap

class CardArtworkRepository(
    assetManager: AssetManager,
    catalog: CardCatalog,
    maxCacheBytes: Int = DEFAULT_CACHE_BYTES,
) {
    private val loader = CachedCardArtworkLoader(
        catalog = catalog,
        maxWeight = maxCacheBytes,
        weightOf = { bitmap -> bitmap.allocationByteCount },
        loadAsset = { assetPath ->
            try {
                assetManager.open(assetPath).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (error: IOException) {
                Log.w(TAG, "Unable to load card artwork: $assetPath", error)
                null
            }
        },
    )

    fun bitmap(cardId: CardId): Bitmap? = loader.load(cardId)

    companion object {
        private const val TAG = "CycleLensArtwork"
        private const val DEFAULT_CACHE_BYTES = 8 * 1024 * 1024
    }
}

internal class CachedCardArtworkLoader<T : Any>(
    private val catalog: CardCatalog,
    maxWeight: Int,
    private val weightOf: (T) -> Int,
    private val loadAsset: (String) -> T?,
) {
    private val cache = BoundedLruCache<CardId, T>(maxWeight, weightOf)
    private val unavailable = mutableSetOf<CardId>()

    @Synchronized
    fun load(cardId: CardId): T? {
        cache[cardId]?.let { cached -> return cached }
        if (cardId in unavailable) {
            return null
        }
        val assetPath = catalog.card(cardId)?.iconAssetPath ?: return null
        val artwork = loadAsset(assetPath)
        if (artwork == null) {
            unavailable += cardId
            return null
        }
        cache.put(cardId, artwork)
        return artwork
    }
}

private class BoundedLruCache<K, V : Any>(
    private val maxWeight: Int,
    private val weightOf: (V) -> Int,
) {
    private val values = LinkedHashMap<K, V>(16, 0.75f, true)
    private var currentWeight = 0

    init {
        require(maxWeight > 0) { "Artwork cache weight must be positive" }
    }

    operator fun get(key: K): V? = values[key]

    fun put(key: K, value: V) {
        val weight = weightOf(value).coerceAtLeast(1)
        values.remove(key)?.let { previous -> currentWeight -= weightOf(previous).coerceAtLeast(1) }
        values[key] = value
        currentWeight += weight
        val iterator = values.entries.iterator()
        while (currentWeight > maxWeight && iterator.hasNext()) {
            currentWeight -= weightOf(iterator.next().value).coerceAtLeast(1)
            iterator.remove()
        }
    }
}
