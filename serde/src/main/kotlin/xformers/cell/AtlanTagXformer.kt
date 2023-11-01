/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
package xformers.cell

import com.atlan.model.core.AtlanTag
import com.atlan.model.core.AtlanTag.AtlanTagBuilder

object AtlanTagXformer {

    private const val SETTINGS_DELIMITER = ">>"

    fun encode(fromGuid: String, atlanTag: AtlanTag): String {
        val direct = fromGuid == atlanTag.entityGuid
        return if (direct) {
            listOf(
                atlanTag.typeName,
                encodePropagation(atlanTag),
            ).joinToString(SETTINGS_DELIMITER)
        } else {
            ""
        }
    }

    fun decode(atlanTag: String): AtlanTag {
        val tokens = atlanTag.split(SETTINGS_DELIMITER)
        val builder = AtlanTag.builder()
            .typeName(tokens[0])
        return decodePropagation(tokens, builder)
    }

    private fun encodePropagation(atlanTag: AtlanTag): String {
        return if (atlanTag.propagate) {
            return when {
                atlanTag.removePropagationsOnEntityDelete && !atlanTag.restrictPropagationThroughLineage -> "FULL"
                atlanTag.removePropagationsOnEntityDelete && atlanTag.restrictPropagationThroughLineage -> "HIERARCHY_ONLY"
                else -> ""
            }
        } else {
            // Nothing to propagate, so leave out all options
            ""
        }
    }

    private fun decodePropagation(atlanTagTokens: List<String>, builder: AtlanTagBuilder<*, *>): AtlanTag {
        if (atlanTagTokens.size > 1) {
            when (atlanTagTokens[1].uppercase()) {
                "FULL" -> builder.propagate(true).removePropagationsOnEntityDelete(true).restrictPropagationThroughLineage(false)
                "HIERARCHY_ONLY" -> builder.propagate(true).removePropagationsOnEntityDelete(true).restrictPropagationThroughLineage(true)
                else -> builder.propagate(false)
            }
        } else {
            // If there is no propagation option specified, turn off propagation
            builder.propagate(false)
        }
        return builder.build()
    }
}
