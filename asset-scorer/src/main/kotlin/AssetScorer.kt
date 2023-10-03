/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.Atlan
import com.atlan.AtlanClient
import com.atlan.events.AtlanEventHandler
import com.atlan.exception.AtlanException
import com.atlan.exception.ConflictException
import com.atlan.exception.ErrorCode
import com.atlan.exception.NotFoundException
import com.atlan.model.assets.Asset
import com.atlan.model.assets.Badge
import com.atlan.model.assets.GlossaryTerm
import com.atlan.model.assets.ICatalog
import com.atlan.model.assets.Readme
import com.atlan.model.core.CustomMetadataAttributes
import com.atlan.model.enums.AtlanCustomAttributePrimitiveType
import com.atlan.model.enums.AtlanIcon
import com.atlan.model.enums.AtlanTagColor
import com.atlan.model.enums.BadgeComparisonOperator
import com.atlan.model.enums.BadgeConditionColor
import com.atlan.model.enums.CertificateStatus
import com.atlan.model.events.AtlanEvent
import com.atlan.model.structs.BadgeCondition
import com.atlan.model.typedefs.AttributeDef
import com.atlan.model.typedefs.CustomMetadataDef
import com.atlan.model.typedefs.CustomMetadataOptions
import io.numaproj.numaflow.function.FunctionServer
import org.slf4j.Logger

object AssetScorer : AbstractNumaflowHandler(Handler) {

    @JvmStatic
    fun main(args: Array<String>) {
        FunctionServer().registerMapHandler(AssetScorer).start()
    }

    object Handler : AtlanEventHandler {

        private const val CM_DAAP = "DaaP"
        private const val CM_ATTR_DAAP_SCORE = "Score"
        private val SCORED_ATTRS = setOf(
            Asset.DESCRIPTION.atlanFieldName,
            Asset.USER_DESCRIPTION.atlanFieldName,
            Asset.OWNER_USERS.atlanFieldName,
            Asset.OWNER_GROUPS.atlanFieldName,
            Asset.ASSIGNED_TERMS.atlanFieldName,
            Asset.HAS_LINEAGE.atlanFieldName,
            Asset.ATLAN_TAGS.atlanFieldName,
            ICatalog.INPUT_TO_PROCESSES.atlanFieldName,
            ICatalog.OUTPUT_FROM_PROCESSES.atlanFieldName,
            GlossaryTerm.ASSIGNED_ENTITIES.atlanFieldName,
            GlossaryTerm.SEE_ALSO.atlanFieldName,
            GlossaryTerm.LINKS.atlanFieldName,
        )

        /** {@inheritDoc}  */
        override fun validatePrerequisites(event: AtlanEvent, log: Logger): Boolean {
            return createCMIfNotExists(log) != null && event.payload != null && event.payload.asset != null
        }

        /** {@inheritDoc}  */
        @Throws(AtlanException::class)
        override fun getCurrentState(client: AtlanClient, fromEvent: Asset, log: Logger): Asset {
            val searchAttrs = SCORED_ATTRS.toMutableSet()
            searchAttrs.addAll(client.customMetadataCache.getAttributesForSearchResults(CM_DAAP))
            return AtlanEventHandler.getCurrentViewOfAsset(client, fromEvent, searchAttrs, true, true)
                ?: throw NotFoundException(
                    ErrorCode.ASSET_NOT_FOUND_BY_QN, fromEvent.qualifiedName, fromEvent.typeName,
                )
        }

        /** {@inheritDoc}  */
        @Throws(AtlanException::class)
        override fun calculateChanges(asset: Asset, log: Logger): Collection<Asset> {
            // Calculate the score
            val score = if (asset is GlossaryTerm) {
                val sDescription = if (AtlanEventHandler.hasDescription(asset)) 15 else 0
                val sRelatedTerm = if (!asset.seeAlso.isNullOrEmpty()) 10 else 0
                val sLinks = if (!asset.links.isNullOrEmpty()) 10 else 0
                val sRelatedAsset = if (!asset.assignedEntities.isNullOrEmpty()) 20 else 0
                val sCertificate = when (asset.certificateStatus) {
                    CertificateStatus.DRAFT -> 15
                    CertificateStatus.VERIFIED -> 25
                    else -> 0
                }
                var sReadme = 0
                if (asset.readme?.guid != null) {
                    val readme = Readme.get(asset.readme.guid)
                    sReadme = when {
                        readme.description.length > 1000 -> 20
                        readme.description.length > 500 -> 10
                        readme.description.length > 100 -> 5
                        else -> 0
                    }
                }
                (sDescription + sRelatedTerm + sLinks + sRelatedAsset + sCertificate + sReadme).toDouble()
            } else if (!asset.typeName.startsWith("AtlasGlossary")) {
                // We will not score glossaries or categories
                val sDescription = if (AtlanEventHandler.hasDescription(asset)) 20 else 0
                val sOwner = if (AtlanEventHandler.hasOwner(asset)) 20 else 0
                val sTerms = if (AtlanEventHandler.hasAssignedTerms(asset)) 20 else 0
                val sTags = if (AtlanEventHandler.hasAtlanTags(asset)) 20 else 0
                val sLineage = if (AtlanEventHandler.hasLineage(asset)) 20 else 0
                (sDescription + sOwner + sLineage + sTerms + sTags).toDouble()
            } else {
                -1.0
            }
            return if (score >= 0) {
                val cma = CustomMetadataAttributes.builder()
                    .attribute(CM_ATTR_DAAP_SCORE, score)
                    .build()
                val revised = asset.trimToRequired().customMetadata(CM_DAAP, cma).build()
                if (hasChanges(asset, revised, log)) setOf(revised) else emptySet()
            } else {
                emptySet()
            }
        }

        /** {@inheritDoc}  */
        override fun hasChanges(original: Asset, modified: Asset, log: Logger?): Boolean {
            val scoreOriginal = if (original.customMetadataSets?.containsKey(CM_DAAP) == true) {
                original.customMetadataSets[CM_DAAP]!!.attributes[CM_ATTR_DAAP_SCORE] ?: -1.0
            } else {
                -1.0
            }
            val scoreModified = if (modified.customMetadataSets?.containsKey(CM_DAAP) == true) {
                modified.customMetadataSets[CM_DAAP]!!.attributes[CM_ATTR_DAAP_SCORE] ?: -1.0
            } else {
                -1.0
            }
            return scoreOriginal != scoreModified
        }

        // Note: can reuse default saveChanges

        /**
         * Check if the custom metadata already exists, and if so simply return.
         * If not, go ahead and create the custom metadata structure and an associated badge.
         *
         * @param log for logging information
         * @return the internal hashed-string name of the custom metadata
         */
        private fun createCMIfNotExists(log: Logger): String? {
            try {
                return Atlan.getDefaultClient().customMetadataCache.getIdForName(CM_DAAP)
            } catch (e: NotFoundException) {
                try {
                    val customMetadataDef = CustomMetadataDef.creator(CM_DAAP)
                        .attributeDef(
                            AttributeDef.of(
                                CM_ATTR_DAAP_SCORE,
                                AtlanCustomAttributePrimitiveType.DECIMAL,
                                null,
                                false,
                            )
                                .toBuilder()
                                .description("Data as a Product completeness score for this asset")
                                .build(),
                        )
                        .options(CustomMetadataOptions.withIcon(AtlanIcon.GAUGE, AtlanTagColor.GRAY))
                        .build()
                    customMetadataDef.create()
                    log.info("Created DaaP custom metadata structure.")
                    val badge = Badge.creator(CM_ATTR_DAAP_SCORE, CM_DAAP, CM_ATTR_DAAP_SCORE)
                        .userDescription(
                            "Data as a Product completeness score. Indicates how enriched and ready for re-use this asset is, out of a total possible score of 100.",
                        )
                        .badgeCondition(BadgeCondition.of(BadgeComparisonOperator.GTE, "75", BadgeConditionColor.GREEN))
                        .badgeCondition(BadgeCondition.of(BadgeComparisonOperator.LT, "75", BadgeConditionColor.YELLOW))
                        .badgeCondition(BadgeCondition.of(BadgeComparisonOperator.LTE, "25", BadgeConditionColor.RED))
                        .build()
                    try {
                        badge.save()
                        log.info("Created DaaP completeness score badge.")
                    } catch (eBadge: AtlanException) {
                        log.error("Unable to create badge over the DaaP score.", eBadge)
                    }
                    return Atlan.getDefaultClient().customMetadataCache.getIdForName(CM_DAAP)
                } catch (conflict: ConflictException) {
                    // Handle cross-thread race condition that the typedef has since been created
                    try {
                        return Atlan.getDefaultClient().customMetadataCache.getIdForName(CM_DAAP)
                    } catch (eConflict: AtlanException) {
                        log.error(
                            "Unable to look up DaaP custom metadata, even though it should already exist.",
                            eConflict,
                        )
                    }
                } catch (eStruct: AtlanException) {
                    log.error("Unable to create DaaP custom metadata structure.", eStruct)
                }
            } catch (e: AtlanException) {
                log.error("Unable to look up DaaP custom metadata.", e)
            }
            return null
        }
    }
}
