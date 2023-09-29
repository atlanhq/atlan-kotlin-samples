/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
package xformers.cell

import com.atlan.model.assets.Asset
import com.atlan.model.core.AtlanTag
import com.atlan.model.enums.AtlanEnum
import com.atlan.model.structs.AtlanStruct
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

object CellXformer {

    const val LIST_DELIMITER = "\n"
    private val LIST_REGEX: String = Pattern.quote(LIST_DELIMITER)

    fun encode(guid: String, value: Any?): String {
        return when (value) {
            is String -> value
            is Collection<*> -> {
                val list = mutableListOf<String>()
                for (element in value) {
                    val encoded = encode(guid, element)
                    list.add(encoded)
                }
                return getDelimitedList(list)
            }
            is Map<*, *> -> {
                val list = mutableListOf<String>()
                for ((key, embeddedValue) in value) {
                    list.add(key.toString() + "=" + encode(guid, embeddedValue))
                }
                return getDelimitedList(list)
            }
            is Asset -> AssetRefXformer.encode(value)
            is AtlanEnum -> EnumXformer.encode(value)
            is AtlanStruct -> StructXformer.encode(value)
            is AtlanTag -> AtlanTagXformer.encode(guid, value)
            is Any -> value.toString()
            else -> ""
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun decode(value: String?, type: Class<*>, innerType: Class<*>?, fieldName: String): Any? {
        return if (value.isNullOrEmpty()) {
            null
        } else if (String::class.java.isAssignableFrom(type)) {
            value
        } else if (Boolean::class.java.isAssignableFrom(type)) {
            value.toBoolean()
        } else if (Integer::class.java.isAssignableFrom(type)) {
            value.toInt()
        } else if (Long::class.java.isAssignableFrom(type)) {
            value.toLong()
        } else if (Double::class.java.isAssignableFrom(type)) {
            value.toDouble()
        } else if (Collection::class.java.isAssignableFrom(type)) {
            // Start by checking whether the list is simple or complex
            val values = parseDelimitedList(value)
            val list = mutableListOf<Any>()
            if (innerType!!.isInterface) {
                // Relationships between assets are defined via interfaces, so this would mean
                // there should be asset references
                for (asset in values) {
                    list.add(AssetRefXformer.decode(asset, fieldName))
                }
            } else if (AtlanEnum::class.java.isAssignableFrom(innerType)) {
                for (enum in values) {
                    val decoded = decode(enum, innerType, null, fieldName)
                    if (decoded != null) {
                        list.add(decoded)
                    }
                }
            } else if (AtlanStruct::class.java.isAssignableFrom(innerType)) {
                for (struct in values) {
                    val decoded = decode(struct, innerType, null, fieldName)
                    if (decoded != null) {
                        list.add(decoded)
                    }
                }
            } else if (AtlanTag::class.java.isAssignableFrom(innerType)) {
                for (tag in values) {
                    val decoded = decode(tag, innerType, null, fieldName)
                    if (decoded != null) {
                        list.add(decoded)
                    }
                }
            }
            when (type) {
                Collection::class.java, List::class.java -> list
                Set::class.java, SortedSet::class.java -> TreeSet(list)
                else -> throw IOException("Unable to deserialize cell to Java class: $type")
            }
        } else if (Map::class.java.isAssignableFrom(type)) {
            TODO("Not yet implemented for import")
        } else if (Asset::class.java.isAssignableFrom(type)) {
            AssetRefXformer.decode(value, fieldName)
        } else if (AtlanEnum::class.java.isAssignableFrom(type)) {
            EnumXformer.decode(value, type as Class<AtlanEnum>)
        } else if (AtlanStruct::class.java.isAssignableFrom(type)) {
            StructXformer.decode(value, type as Class<AtlanStruct>)
        } else if (AtlanTag::class.java.isAssignableFrom(type)) {
            AtlanTagXformer.decode(value)
        } else {
            throw IOException("Unhandled data type for $fieldName: $type")
        }
    }

    private fun getDelimitedList(values: List<String>?): String {
        return if (values.isNullOrEmpty()) {
            ""
        } else {
            values.joinToString(LIST_DELIMITER)
        }
    }

    private fun parseDelimitedList(values: String?): List<String> {
        return if (values.isNullOrEmpty()) {
            listOf()
        } else {
            values.split(LIST_REGEX)
        }
    }
}
