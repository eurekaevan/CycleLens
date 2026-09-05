package com.eureka.cyclelens.catalog

import cyclelens.core.CardId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardArtworkRepositoryTest {
    @Test
    fun `existing artwork is loaded through the injected asset source`() {
        val loader = loader { path -> "decoded:$path" }

        assertEquals("decoded:card-icons/1.png", loader.load(CardId("one")))
    }

    @Test
    fun `repeated artwork request decodes only once`() {
        var decodes = 0
        val loader = loader {
            decodes += 1
            "decoded"
        }

        assertEquals("decoded", loader.load(CardId("one")))
        assertEquals("decoded", loader.load(CardId("one")))
        assertEquals(1, decodes)
    }

    @Test
    fun `missing artwork returns stable fallback without repeated decode attempts`() {
        var attempts = 0
        val loader = loader {
            attempts += 1
            null
        }

        assertNull(loader.load(CardId("one")))
        assertNull(loader.load(CardId("one")))
        assertEquals(1, attempts)
    }

    @Test
    fun `bounded cache evicts the least recently used artwork`() {
        val decodes = mutableMapOf<String, Int>()
        val loader = CachedCardArtworkLoader(
            catalog = catalog(),
            maxWeight = 2,
            weightOf = { _: String -> 1 },
            loadAsset = { path ->
                decodes[path] = decodes.getOrDefault(path, 0) + 1
                path
            },
        )

        loader.load(CardId("one"))
        loader.load(CardId("two"))
        loader.load(CardId("one"))
        loader.load(CardId("three"))
        loader.load(CardId("two"))

        assertEquals(1, decodes["card-icons/1.png"])
        assertEquals(2, decodes["card-icons/2.png"])
        assertEquals(1, decodes["card-icons/3.png"])
    }

    @Test
    fun `application artwork repository has no Activity or Context dependency`() {
        val constructorTypes = CardArtworkRepository::class.java.declaredConstructors
            .flatMap { constructor -> constructor.parameterTypes.toList() }
            .map(Class<*>::getName)

        assertTrue("android.app.Activity" !in constructorTypes)
        assertTrue("android.content.Context" !in constructorTypes)
    }

    private fun loader(loadAsset: (String) -> String?) = CachedCardArtworkLoader(
        catalog = catalog(),
        maxWeight = 2,
        weightOf = { _: String -> 1 },
        loadAsset = loadAsset,
    )

    private fun catalog(): CardCatalog {
        val cards = (1L..3L).map { number ->
            CardDefinition(
                id = CardId(
                    when (number) {
                        1L -> "one"
                        2L -> "two"
                        else -> "three"
                    },
                ),
                supercellId = number,
                displayName = "Card $number",
                shortName = "C$number",
                type = CardType.TROOP,
                elixir = 1,
                cycleEligible = true,
                iconAssetPath = "card-icons/$number.png",
            )
        }
        return CardCatalog(
            definitions = cards,
            visualFormDefinitions = cards.map { card ->
                VisualFormDefinition(
                    id = "${card.id.value}_normal",
                    canonicalCardId = card.id,
                    form = CardForm.NORMAL,
                    displayName = card.displayName,
                )
            },
        )
    }
}
