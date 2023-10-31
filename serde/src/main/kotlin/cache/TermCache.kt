/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
package cache

import com.atlan.exception.AtlanException
import com.atlan.model.assets.Asset
import com.atlan.model.assets.GlossaryTerm
import mu.KotlinLogging
import xformers.cell.AssignedTermXformer

object TermCache : AssetCache() {

    private val log = KotlinLogging.logger {}

    /** {@inheritDoc}  */
    override fun lookupAsset(identity: String?): Asset? {
        val tokens = identity?.split(AssignedTermXformer.TERM_GLOSSARY_DELIMITER)
        if (tokens?.size == 2) {
            val termName = tokens[0]
            val glossaryName = tokens[1]
            val glossary = GlossaryCache[glossaryName]
            if (glossary != null) {
                try {
                    return GlossaryTerm.findByNameFast(termName, glossary.qualifiedName)
                } catch (e: AtlanException) {
                    log.error("Unable to lookup or find term: {}", identity, e)
                }
            } else {
                log.error("Unable to find glossary {} for term reference: {}", glossaryName, identity)
            }
        } else {
            log.error("Unable to lookup or find term, unexpected reference: {}", identity)
        }
        return null
    }
}
