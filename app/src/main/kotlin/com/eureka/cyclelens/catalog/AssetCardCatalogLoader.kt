package com.eureka.cyclelens.catalog

import android.content.Context
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object AssetCardCatalogLoader {
    fun load(context: Context): CardCatalog = context.assets.open(ASSET_NAME).use { stream ->
        InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
            CardCatalogJsonParser.parse(reader)
        }
    }

    private const val ASSET_NAME = "cards.json"
}
