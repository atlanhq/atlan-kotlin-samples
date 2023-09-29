/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
package xformers.cell

import com.atlan.Atlan
import com.atlan.model.assets.Asset
import com.atlan.model.assets.Link
import com.atlan.model.assets.Readme
import com.atlan.serde.Serde
import java.util.regex.Pattern

object AssetRefXformer {

    private const val TYPE_QN_DELIMITER = "@"
    private val ASSET_REF_REGEX: String = Pattern.quote(TYPE_QN_DELIMITER)

    fun encode(asset: Asset): String {
        // Handle some assets as direct embeds
        return when (asset) {
            is Readme -> asset.description
            is Link -> {
                // Transform to a set of useful, non-overlapping info
                Link._internal()
                    .name(asset.name)
                    .link(asset.link)
                    .build()
                    .toJson(Atlan.getDefaultClient())
            }
            else -> {
                var qualifiedName = asset.qualifiedName
                if (asset.qualifiedName.isNullOrEmpty() && asset.uniqueAttributes != null) {
                    qualifiedName = asset.uniqueAttributes.qualifiedName
                }
                "${asset.typeName}$TYPE_QN_DELIMITER$qualifiedName"
            }
        }
    }

    fun decode(assetRef: String, fieldName: String): Asset {
        return when (fieldName) {
            "readme" -> Readme._internal().description(assetRef).build()
            "links" -> Atlan.getDefaultClient().readValue(assetRef, Link::class.java)
            else -> {
                val tokens = assetRef.split(ASSET_REF_REGEX)
                val typeName = tokens[0]
                val assetClass = Serde.getAssetClassForType(typeName)
                val method = assetClass.getMethod("refByQualifiedName", String::class.java)
                val qualifiedName = tokens.subList(1, tokens.size).joinToString(TYPE_QN_DELIMITER)
                method.invoke(null, qualifiedName) as Asset
            }
        }
    }
}
