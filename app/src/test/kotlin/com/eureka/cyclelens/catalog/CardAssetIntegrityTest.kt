package com.eureka.cyclelens.catalog

import com.google.gson.JsonParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardAssetIntegrityTest {
    private val assetsDirectory = File("src/main/assets")
    private val iconDirectory = File(assetsDirectory, "card-icons")
    private val catalog = File(assetsDirectory, "cards.json").reader().use { reader ->
        CardCatalogJsonParser.parse(reader)
    }

    @Test
    fun `every cycle card has complete canonical metadata`() {
        catalog.allCycleCards().forEach { card ->
            assertTrue(card.id.value.isNotBlank(), "Missing canonical ID")
            assertTrue(card.supercellId != null, "Missing Supercell ID for ${card.id.value}")
            assertTrue(card.displayName.isNotBlank(), "Missing display name for ${card.id.value}")
            assertTrue(card.shortName.isNotBlank(), "Missing short name for ${card.id.value}")
            assertTrue(
                card.type in setOf(CardType.TROOP, CardType.SPELL, CardType.BUILDING),
                "Invalid cycle type for ${card.id.value}: ${card.type}",
            )
            assertEquals(
                1,
                catalog.allVisualForms().count { form ->
                    form.canonicalCardId == card.id && form.form == CardForm.NORMAL
                },
                "Expected one NORMAL form for ${card.id.value}",
            )
        }
    }

    @Test
    fun `icon paths are unique and exactly match their Supercell IDs`() {
        val paths = catalog.allCards().mapNotNull(CardDefinition::iconAssetPath)

        assertEquals(paths.size, paths.toSet().size, "Icon asset paths must be unique")
        catalog.allCards().filter { card -> card.iconAssetPath != null }.forEach { card ->
            assertEquals("card-icons/${card.supercellId}.png", card.iconAssetPath)
        }
    }

    @Test
    fun `all referenced artwork is nonempty PNG data`() {
        catalog.allCards().mapNotNull(CardDefinition::iconAssetPath).forEach { assetPath ->
            val file = File(assetsDirectory, assetPath)
            assertTrue(file.isFile, "Missing referenced artwork: $assetPath")
            assertTrue(file.length() > PNG_SIGNATURE.size, "Empty artwork: $assetPath")
            assertTrue(
                file.inputStream().use { stream ->
                    stream.readNBytes(PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)
                },
                "Artwork is not PNG: $assetPath",
            )
        }
    }

    @Test
    fun `missing cycle artwork is explicitly whitelisted`() {
        val missingIds = catalog.allCycleCards()
            .filter { card ->
                card.iconAssetPath == null || !File(assetsDirectory, card.iconAssetPath).isFile
            }
            .map { card -> card.id.value }
            .sorted()

        assertEquals(readMissingArtworkWhitelist(), missingIds)
        assertEquals(0, missingIds.size, "Update this visible count when the whitelist changes")
    }

    @Test
    fun `card icon directory contains no orphan PNG files`() {
        val referencedNames = catalog.allCards()
            .mapNotNull(CardDefinition::iconAssetPath)
            .map { path -> path.substringAfterLast('/') }
            .toSet()
        val packagedNames = iconDirectory.listFiles()
            .orEmpty()
            .filter { file -> file.isFile && file.extension.equals("png", ignoreCase = true) }
            .map(File::getName)
            .toSet()

        assertEquals(emptySet(), packagedNames - referencedNames)
    }

    private fun readMissingArtworkWhitelist(): List<String> {
        val file = File("../tools/update-card-catalog/artwork-missing-whitelist.json")
        val root = file.reader().use(JsonParser::parseReader).asJsonObject
        return root.getAsJsonArray("cardIds").map { element -> element.asString }.sorted()
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    }
}
