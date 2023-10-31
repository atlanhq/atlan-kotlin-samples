/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
package cache

import com.atlan.exception.AtlanException
import com.atlan.model.assets.Asset
import com.atlan.model.assets.Glossary
import mu.KotlinLogging

object GlossaryCache : AssetCache() {

    private val log = KotlinLogging.logger {}

    /** {@inheritDoc}  */
    override fun lookupAsset(identity: String?): Asset? {
        try {
            return Glossary.findByName(identity)
        } catch (e: AtlanException) {
            log.error("Unable to lookup or find glossary: {}", identity, e)
        }
        return null
    }
}
