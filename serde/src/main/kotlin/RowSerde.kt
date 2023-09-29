/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.cache.ReflectionCache
import com.atlan.model.assets.Asset
import com.atlan.model.assets.Asset.AssetBuilder
import com.atlan.model.core.CustomMetadataAttributes
import com.atlan.model.core.CustomMetadataAttributes.CustomMetadataAttributesBuilder
import com.atlan.model.fields.AtlanField
import com.atlan.model.fields.CustomMetadataField
import com.atlan.serde.Serde
import mu.KotlinLogging
import xformers.cell.CellXformer
import xformers.cell.CellXformer.LIST_DELIMITER
import java.lang.reflect.Method
import java.util.regex.Pattern

private val log = KotlinLogging.logger {}

const val CM_HEADING_DELIMITER = "::"

fun getHeaderForField(field: AtlanField): String {
    return if (field is CustomMetadataField) {
        // For custom metadata, translate the header to human-readable names
        field.setName + CM_HEADING_DELIMITER + field.attributeName
    } else {
        field.atlanFieldName
    }
}

class RowSerializer(private val asset: Asset, private val fields: List<AtlanField>) {

    fun getRow(): Iterable<String> {
        val row = mutableListOf<String>()
        row.add(FieldSerde.getValueForField(asset, Asset.QUALIFIED_NAME))
        row.add(FieldSerde.getValueForField(asset, Asset.TYPE_NAME))
        for (field in fields) {
            if (field != Asset.QUALIFIED_NAME && field != Asset.TYPE_NAME) {
                row.add(FieldSerde.getValueForField(asset, field))
            }
        }
        return row
    }
}

class RowDeserializer(private val heading: List<String>, private val row: List<String>) {

    private val typeIdx: Int = heading.indexOf(Asset.TYPE_NAME.atlanFieldName)
    private val qnIdx: Int = heading.indexOf(Asset.QUALIFIED_NAME.atlanFieldName)

    fun getAsset(): Asset? {
        val typeName = row.getOrElse(typeIdx) { "" }
        val qualifiedName = row.getOrElse(qnIdx) { "" }
        if (typeName == "" || qualifiedName == "") {
            log.warn("No qualifiedName or typeName found on row, cannot deserialize: {}", row)
        } else {
            val assetClass = Serde.getAssetClassForType(typeName)
            val method = assetClass.getMethod("_internal")
            val builder = method.invoke(null) as AssetBuilder<*, *>
            val customMetadataMap = mutableMapOf<String, CustomMetadataAttributesBuilder<*, *>>()
            for (i in heading.indices) {
                val fieldName = heading[i]
                if (fieldName.isNotEmpty()) {
                    val rValue = row[i]
                    if (fieldName.contains(CM_HEADING_DELIMITER)) {
                        // Custom metadata field...
                        val tokens = fieldName.split(Pattern.quote(CM_HEADING_DELIMITER))
                        val setName = tokens[0]
                        val attrName = tokens[1]
                        if (!customMetadataMap.containsKey(setName)) {
                            customMetadataMap[setName] = CustomMetadataAttributes.builder()
                        }
                        val value: Any? = FieldSerde.getCustomMetadataValueFromString(rValue)
                        customMetadataMap[setName]!!.attribute(attrName, value)
                    } else {
                        // "Normal" field...
                        val deserializedFieldName = ReflectionCache.getDeserializedName(assetClass, fieldName)
                        val setter = ReflectionCache.getSetter(builder.javaClass, deserializedFieldName)
                        if (setter != null) {
                            val value = FieldSerde.getValueFromCell(rValue, setter)
                            if (value != null) {
                                // TODO: handle special cases:
                                //  - READMEs (create & relate)
                                //  - Links (create & relate)
                                //  - AtlanTags (no idempotent method to set them)
                                ReflectionCache.setValue(builder, deserializedFieldName, value)
                            }
                        }
                    }
                }
            }
            if (customMetadataMap.isNotEmpty()) {
                for ((key, value) in customMetadataMap) {
                    builder.customMetadata(key, value.build())
                }
            }
            return builder.build()
            /* This needs to be moved to specific logic of the import
            for (field in getAttributesToOverwrite()) {
                try {
                    val getter = ReflectionCache.getGetter(
                        Serde.getAssetClassForType(candidate.typeName), field.atlanFieldName
                    )
                    // TODO: double-check this works for empty lists, too?
                    if (getter.invoke(candidate) == null) {
                        builder.nullField(field.atlanFieldName)
                    }
                } catch (e: ClassNotFoundException) {
                    log.error(
                        "Unknown type {} — cannot clear {}.",
                        candidate.typeName,
                        field.atlanFieldName,
                        e
                    )
                } catch (e: IllegalAccessException) {
                    log.error(
                        "Unable to clear {} on: {}::{}",
                        field.atlanFieldName,
                        candidate.typeName,
                        candidate.qualifiedName,
                        e
                    )
                } catch (e: InvocationTargetException) {
                    log.error(
                        "Unable to clear {} on: {}::{}",
                        field.atlanFieldName,
                        candidate.typeName,
                        candidate.qualifiedName,
                        e
                    )
                }
            }
            return builder.build()*/
        }
        return null
    }
}

private object FieldSerde {

    fun getValueForField(asset: Asset, field: AtlanField): String {
        val value = if (field is CustomMetadataField) {
            asset.getCustomMetadata(field.setName, field.attributeName)
        } else {
            val deserializedName = ReflectionCache.getDeserializedName(asset.javaClass, field.atlanFieldName)
            ReflectionCache.getValue(asset, deserializedName)
        }
        return CellXformer.encode(asset.guid, value)
    }

    fun getValueFromCell(value: String, setter: Method): Any? {
        val paramClass = ReflectionCache.getParameterOfMethod(setter)
        var innerClass: Class<*>? = null
        val fieldName = setter.name
        if (Collection::class.java.isAssignableFrom(paramClass) || Map::class.java.isAssignableFrom(paramClass)) {
            val paramType = ReflectionCache.getParameterizedTypeOfMethod(setter)
            innerClass = ReflectionCache.getClassOfParameterizedType(paramType)
        }
        return CellXformer.decode(value, paramClass, innerClass, fieldName)
    }

    fun getCustomMetadataValueFromString(value: String?): Any? {
        return if (value.isNullOrEmpty()) {
            null
        } else if (value.contains(LIST_DELIMITER)) {
            getMultiValuedCustomMetadata(value)
        } else {
            value
        }
    }

    private fun getMultiValuedCustomMetadata(value: String?): List<String> {
        return if (value.isNullOrEmpty()) {
            listOf()
        } else {
            value.split(Pattern.quote(LIST_DELIMITER))
        }
    }
}
