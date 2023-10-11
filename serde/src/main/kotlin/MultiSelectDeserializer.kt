/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * Utility class to deserialize the results of a multi-select widget, since it can return
 * either a plain string or a JSON array (string-encoded) depending on how many elements are
 * actually selected in the UI.
 */
class MultiSelectDeserializer : StdDeserializer<List<String>>(TypeFactory.defaultInstance().constructCollectionType(List::class.java, String::class.java)) {

    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): List<String> {
        val root = p?.codec?.readTree<JsonNode>(p)
        if (root != null && !root.isNull && root.isTextual) {
            val string = root.textValue()
            return if (string.startsWith("[")) {
                // TODO: probably a better way to get the mapper from the parser or context,
                //  but far less hassle to just create one...
                return jacksonObjectMapper().readValue<List<String>>(string)
            } else {
                listOf(string)
            }
        }
        return listOf()
    }
}