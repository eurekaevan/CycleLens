package com.eureka.cyclelens.catalog

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import cyclelens.core.CardId
import java.io.Reader

object CardCatalogJsonParser {
    fun parse(json: String): CardCatalog = parse(json.reader())

    fun parse(reader: Reader): CardCatalog {
        val root = try {
            JsonParser.parseReader(reader)
        } catch (error: JsonParseException) {
            throw IllegalArgumentException("Invalid card catalog JSON", error)
        }
        require(root.isJsonObject) { "Card catalog JSON root must be an object" }

        val rootObject = root.asJsonObject
        val cards = rootObject.requiredArray("cards").mapIndexed { index, element ->
            require(element.isJsonObject) { "Card at index $index must be an object" }
            element.asJsonObject.toCardDefinition(index)
        }
        val visualForms = rootObject.requiredArray("visualForms").mapIndexed { index, element ->
            require(element.isJsonObject) { "Visual form at index $index must be an object" }
            element.asJsonObject.toVisualFormDefinition(index)
        }
        return CardCatalog(cards, visualForms)
    }

    private fun JsonObject.toCardDefinition(index: Int): CardDefinition = CardDefinition(
        id = CardId(requiredString("id", "Card", index)),
        supercellId = requiredNullableLong("supercellId", "Card", index),
        displayName = requiredString("displayName", "Card", index),
        shortName = requiredString("shortName", "Card", index),
        type = requiredNullableEnum<CardType>("type", "Card", index),
        elixir = requiredNullableInt("elixir", "Card", index),
        cycleEligible = requiredBoolean("cycleEligible", "Card", index),
        iconAssetPath = requiredNullableString("iconAssetPath", "Card", index),
    )

    private fun JsonObject.toVisualFormDefinition(index: Int): VisualFormDefinition =
        VisualFormDefinition(
            id = requiredString("id", "Visual form", index),
            canonicalCardId = CardId(
                requiredString("canonicalCardId", "Visual form", index),
            ),
            form = requiredEnum("form", "Visual form", index),
            displayName = requiredString("displayName", "Visual form", index),
        )

    private fun JsonObject.requiredArray(field: String) = get(field).let { element ->
        require(element != null && element.isJsonArray) {
            "Card catalog requires array field '$field'"
        }
        element.asJsonArray
    }

    private fun JsonObject.requiredString(
        field: String,
        itemName: String,
        index: Int,
    ): String {
        val element = get(field)
        require(element != null && element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            "$itemName at index $index requires string field '$field'"
        }
        return element.asString
    }

    private fun JsonObject.requiredBoolean(
        field: String,
        itemName: String,
        index: Int,
    ): Boolean {
        val element = get(field)
        require(element != null && element.isJsonPrimitive && element.asJsonPrimitive.isBoolean) {
            "$itemName at index $index requires boolean field '$field'"
        }
        return element.asBoolean
    }

    private fun JsonObject.requiredNullableString(
        field: String,
        itemName: String,
        index: Int,
    ): String? {
        val element = requiredNullableField(field, itemName, index)
        if (element.isJsonNull) {
            return null
        }
        require(element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            "$itemName at index $index field '$field' must be a string or null"
        }
        return element.asString
    }

    private fun JsonObject.requiredNullableLong(
        field: String,
        itemName: String,
        index: Int,
    ): Long? {
        val element = requiredNullableField(field, itemName, index)
        if (element.isJsonNull) {
            return null
        }
        require(element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
            "$itemName at index $index field '$field' must be an integer or null"
        }
        return requireNotNull(element.asString.toLongOrNull()) {
            "$itemName at index $index field '$field' must be an integer or null"
        }
    }

    private fun JsonObject.requiredNullableInt(
        field: String,
        itemName: String,
        index: Int,
    ): Int? {
        val element = requiredNullableField(field, itemName, index)
        if (element.isJsonNull) {
            return null
        }
        require(element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
            "$itemName at index $index field '$field' must be an integer or null"
        }
        return requireNotNull(element.asString.toIntOrNull()) {
            "$itemName at index $index field '$field' must be an integer or null"
        }
    }

    private inline fun <reified T : Enum<T>> JsonObject.requiredNullableEnum(
        field: String,
        itemName: String,
        index: Int,
    ): T? {
        val element = requiredNullableField(field, itemName, index)
        if (element.isJsonNull) {
            return null
        }
        require(element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            "$itemName at index $index field '$field' must be a string or null"
        }
        return enumValue<T>(element.asString, field, itemName, index)
    }

    private inline fun <reified T : Enum<T>> JsonObject.requiredEnum(
        field: String,
        itemName: String,
        index: Int,
    ): T {
        val value = requiredString(field, itemName, index)
        return enumValue(value, field, itemName, index)
    }

    private inline fun <reified T : Enum<T>> enumValue(
        value: String,
        field: String,
        itemName: String,
        index: Int,
    ): T = try {
        enumValueOf<T>(value)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException(
            "$itemName at index $index has unknown $field '$value'",
            error,
        )
    }

    private fun JsonObject.requiredNullableField(
        field: String,
        itemName: String,
        index: Int,
    ): JsonElement = requireNotNull(get(field)) {
        "$itemName at index $index requires field '$field'"
    }
}
