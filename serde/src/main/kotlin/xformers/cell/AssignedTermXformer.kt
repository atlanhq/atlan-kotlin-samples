/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
package xformers.cell

import cache.TermCache
import com.atlan.model.assets.Asset
import com.atlan.model.assets.GlossaryTerm

/**
 * Static object to transform term assignment references.
 */
object AssignedTermXformer {

    const val TERM_GLOSSARY_DELIMITER = "@@@"

    /**
     * Encodes (serializes) a term assignment into a string form.
     *
     * @param asset to be encoded
     * @return the string-encoded form for that asset
     */
    fun encode(asset: Asset): String {
        // Handle some assets as direct embeds
        return when (asset) {
            is GlossaryTerm -> {
                "${asset.name}$TERM_GLOSSARY_DELIMITER${asset.anchor.name}"
            }
            else -> AssetRefXformer.encode(asset)
        }
    }

    /**
     * Decodes (deserializes) a string form into a term assignment object.
     *
     * @param assetRef the string form to be decoded
     * @param fieldName the name of the field containing the string-encoded value
     * @return the term assignment represented by the string
     */
    fun decode(assetRef: String, fieldName: String): Asset {
        return when (fieldName) {
            "meanings" -> {
                TermCache[assetRef]?.trimToReference()!!
            }
            else -> AssetRefXformer.decode(assetRef, fieldName)
        }
    }
}
