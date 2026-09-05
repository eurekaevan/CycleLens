package com.eureka.cyclelens.catalog

import cyclelens.core.CardId
import java.util.Collections

class CardCatalog(
    definitions: Iterable<CardDefinition>,
    visualFormDefinitions: Iterable<VisualFormDefinition>,
) {
    private val cardsById: Map<CardId, CardDefinition>
    private val visualFormsById: Map<String, VisualFormDefinition>
    private val cards: List<CardDefinition>
    private val cycleCards: List<CardDefinition>
    private val visualForms: List<VisualFormDefinition>

    init {
        val definitionList = definitions.toList()
        require(definitionList.all { CARD_ID_PATTERN.matches(it.id.value) }) {
            "Card IDs must use lowercase snake_case"
        }
        require(definitionList.all { it.displayName.isNotBlank() }) {
            "Card display names must not be blank"
        }
        require(definitionList.all { it.shortName.isNotBlank() }) {
            "Card short names must not be blank"
        }
        require(definitionList.all { it.elixir == null || it.elixir in ELIXIR_RANGE }) {
            "Card elixir must be between ${ELIXIR_RANGE.first} and ${ELIXIR_RANGE.last}, or null"
        }
        require(definitionList.all { it.supercellId == null || it.supercellId > 0 }) {
            "Supercell IDs must be positive or null"
        }
        require(definitionList.all { definition ->
            definition.iconAssetPath == null || ASSET_PATH_PATTERN.matches(
                definition.iconAssetPath,
            )
        }) {
            "Card icon asset paths must use card-icons/<positive Supercell ID>.png or null"
        }
        require(definitionList.all { definition ->
            definition.iconAssetPath == null ||
                definition.iconAssetPath == "card-icons/${definition.supercellId}.png"
        }) {
            "Card icon asset paths must match the card Supercell ID"
        }
        require(definitionList.none { definition ->
            definition.type == CardType.TOWER_TROOP && definition.cycleEligible
        }) {
            "Tower Troops must not be cycle eligible"
        }

        val indexedDefinitions = definitionList.associateBy(CardDefinition::id)
        require(indexedDefinitions.size == definitionList.size) {
            "Card IDs must be unique"
        }
        val supercellIds = definitionList.mapNotNull(CardDefinition::supercellId)
        require(supercellIds.distinct().size == supercellIds.size) {
            "Non-null Supercell IDs must be unique"
        }

        val visualFormList = visualFormDefinitions.toList()
        require(visualFormList.all { CARD_ID_PATTERN.matches(it.id) }) {
            "Visual form IDs must use lowercase snake_case"
        }
        require(visualFormList.all { it.displayName.isNotBlank() }) {
            "Visual form display names must not be blank"
        }
        val indexedVisualForms = visualFormList.associateBy(VisualFormDefinition::id)
        require(indexedVisualForms.size == visualFormList.size) {
            "Visual form IDs must be unique"
        }
        require(visualFormList.all { visualForm ->
            visualForm.canonicalCardId in indexedDefinitions
        }) {
            "Every visual form must reference an existing canonical card"
        }
        definitionList.forEach { definition ->
            val normalForms = visualFormList.filter { visualForm ->
                visualForm.canonicalCardId == definition.id && visualForm.form == CardForm.NORMAL
            }
            require(normalForms.size == 1) {
                "Canonical card '${definition.id.value}' must have exactly one NORMAL form"
            }
            require(normalForms.single().displayName == definition.displayName) {
                "NORMAL form display name must match its canonical card"
            }
        }

        cards = immutableCopy(definitionList)
        cycleCards = immutableCopy(definitionList.filter(CardDefinition::cycleEligible))
        visualForms = immutableCopy(visualFormList)
        cardsById = indexedDefinitions
        visualFormsById = indexedVisualForms
    }

    fun card(id: CardId): CardDefinition? = cardsById[id]

    operator fun get(id: CardId): CardDefinition? = card(id)

    fun allCycleCards(): List<CardDefinition> = cycleCards

    fun allCards(): List<CardDefinition> = cards

    fun allVisualForms(): List<VisualFormDefinition> = visualForms

    fun visualForm(id: String): VisualFormDefinition? = visualFormsById[id]

    fun canonicalizeVisualForm(id: String): CardId? = visualFormsById[id]?.canonicalCardId

    fun isCycleEligible(id: CardId): Boolean = cardsById[id]?.cycleEligible == true

    private fun <T> immutableCopy(items: List<T>): List<T> =
        Collections.unmodifiableList(items.toList())

    private companion object {
        val CARD_ID_PATTERN = Regex("[a-z0-9]+(?:_[a-z0-9]+)*")
        val ASSET_PATH_PATTERN = Regex("card-icons/[1-9][0-9]*\\.png")
        val ELIXIR_RANGE = 1..10
    }
}
