/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
package xformers.cell

import com.atlan.model.core.AtlanTag
import java.util.regex.Pattern

object AtlanTagXformer {

    private const val SETTINGS_DELIMITER = ">>"
    private val SETTINGS_REGEX: String = Pattern.quote(SETTINGS_DELIMITER)

    fun encode(fromGuid: String, atlanTag: AtlanTag): String {
        val direct = fromGuid == atlanTag.entityGuid
        return if (direct) {
            listOf(
                atlanTag.typeName,
                atlanTag.propagate,
                atlanTag.removePropagationsOnEntityDelete,
                atlanTag.restrictPropagationThroughLineage,
            ).joinToString(SETTINGS_DELIMITER)
        } else {
            ""
        }
    }

    fun decode(atlanTag: String): AtlanTag {
        val tokens = atlanTag.split(SETTINGS_REGEX)
        return AtlanTag.builder()
            .typeName(tokens[0])
            .propagate(tokens[1].toBoolean())
            .removePropagationsOnEntityDelete(tokens[2].toBoolean())
            .restrictPropagationThroughLineage(tokens[3].toBoolean())
            .build()
    }
}
