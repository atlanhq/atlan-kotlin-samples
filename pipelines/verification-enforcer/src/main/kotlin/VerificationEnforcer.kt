/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.AtlanClient
import com.atlan.events.AtlanEventHandler
import com.atlan.exception.AtlanException
import com.atlan.exception.ErrorCode
import com.atlan.exception.NotFoundException
import com.atlan.model.assets.Asset
import com.atlan.model.assets.ICatalog
import com.atlan.model.enums.CertificateStatus
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import config.EventConfig
import org.slf4j.Logger

/**
 *
 */
object VerificationEnforcer : AbstractNumaflowHandler(Handler) {

    private lateinit var config: Cfg

    @JvmStatic
    fun main(args: Array<String>) {
        val configCandidate = EventUtils.setEventOps<Cfg>()
        if (configCandidate != null) {
            config = configCandidate
            EventUtils.useApiToken(config.apiTokenId)
            EventUtils.startHandler(this)
        }
    }

    /**
     * Expected configuration for the event processing.
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    data class Cfg(
        @JsonProperty("must-haves") val mustHaves: List<String>?,
        @JsonProperty("enforcement-message") val enforcementMessage: String?,
        @JsonProperty("asset-types") val assetTypes: List<String>?,
        @JsonProperty("api-token") val apiTokenId: String?,
    ) : EventConfig()

    /**
     * Logic for the event processing.
     */
    object Handler : AtlanEventHandler {

        private val REQUIRED_ATTRS = setOf(
            Asset.CERTIFICATE_STATUS.atlanFieldName,
            Asset.DESCRIPTION.atlanFieldName,
            Asset.USER_DESCRIPTION.atlanFieldName,
            Asset.OWNER_USERS.atlanFieldName,
            Asset.OWNER_GROUPS.atlanFieldName,
            Asset.HAS_LINEAGE.atlanFieldName,
            Asset.README.atlanFieldName,
            Asset.ASSIGNED_TERMS.atlanFieldName,
            Asset.ATLAN_TAGS.atlanFieldName,
            ICatalog.INPUT_TO_PROCESSES.atlanFieldName,
            ICatalog.OUTPUT_FROM_PROCESSES.atlanFieldName,
        )

        private val MUST_HAVES = config.mustHaves ?: listOf()
        private val ASSET_TYPES = config.assetTypes ?: listOf()
        private val ENFORCEMENT_MESSAGE = config.enforcementMessage ?: "To be verified, an asset must have a description, at least one owner, and lineage."

        // Note: we can just re-use the default validatePrerequisites

        /** {@inheritDoc}  */
        @Throws(AtlanException::class)
        override fun getCurrentState(client: AtlanClient, fromEvent: Asset, log: Logger): Asset {
            val includeTerms = MUST_HAVES.contains("term")
            val includeTags = MUST_HAVES.contains("tag")
            return AtlanEventHandler.getCurrentViewOfAsset(client, fromEvent, REQUIRED_ATTRS, includeTerms, includeTags)
                ?: throw NotFoundException(
                    ErrorCode.ASSET_NOT_FOUND_BY_QN, fromEvent.qualifiedName, fromEvent.typeName,
                )
        }

        /** {@inheritDoc}  */
        @Throws(AtlanException::class)
        override fun calculateChanges(asset: Asset, log: Logger): Collection<Asset> {
            // We only need to consider enforcement if the asset is currently verified
            if (asset.certificateStatus == CertificateStatus.VERIFIED && asset.typeName in ASSET_TYPES) {
                if (!AtlanEventHandler.hasDescription(asset) ||
                    !AtlanEventHandler.hasOwner(asset) || !asset.typeName.startsWith("AtlasGlossary") && !AtlanEventHandler.hasLineage(
                        asset,
                    )
                ) {
                    return setOf(
                        asset.trimToRequired()
                            .certificateStatus(CertificateStatus.DRAFT)
                            .certificateStatusMessage(ENFORCEMENT_MESSAGE)
                            .build(),
                    )
                } else {
                    log.info(
                        "Asset has all required information present to be verified, no enforcement required: {}",
                        asset.qualifiedName,
                    )
                }
            } else {
                log.info("Asset is no longer verified, no enforcement action to consider: {}", asset.qualifiedName)
            }
            return emptySet()
        }

        // Note: we can just re-use the default hasChanges
        // Note: can reuse default saveChanges

        /**
         * Determine whether the provided asset is valid, according to the criteria supplied
         * in the configuration of the pipeline.
         *
         * @param asset to validate
         */
        private fun valid(asset: Asset): Boolean {
            var overallValid = true
            for (next in MUST_HAVES) {
                overallValid = overallValid && when (next) {
                    "description" -> AtlanEventHandler.hasDescription(asset)
                    "owner" -> AtlanEventHandler.hasOwner(asset)
                    // TODO: once available in next SDK release "readme" -> AtlanEventHandler.hasReadme(asset)
                    "tag" -> AtlanEventHandler.hasAtlanTags(asset)
                    // Only check these last two on non-glossary asset types
                    "lineage" -> !asset.typeName.startsWith("AtlasGlossary") && AtlanEventHandler.hasLineage(asset)
                    "term" -> !asset.typeName.startsWith("AtlasGlossary") && AtlanEventHandler.hasAssignedTerms(asset)
                    else -> false
                }
            }
            return overallValid
        }
    }
}
