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
import io.numaproj.numaflow.function.FunctionServer
import mu.KotlinLogging
import org.slf4j.Logger

object VerificationEnforcer : AbstractNumaflowHandler(Handler) {

    @JvmStatic
    fun main(args: Array<String>) {
        val logger = KotlinLogging.logger {}
        logger.info("Looking for configuration in S3...")
        val config = S3ConfigSync("/tmp", Utils.getEnvVar("CONFIG_PREFIX", ""))
        if (!config.sync()) {
            logger.info("... no configuration found, will timeout pod and retry ...")
        } else {
            logger.info("Configuration found - synced to: /tmp/config.json")
            FunctionServer().registerMapHandler(VerificationEnforcer).start()
        }
    }

    object Handler : AtlanEventHandler {

        private val REQUIRED_ATTRS = setOf(
            Asset.CERTIFICATE_STATUS.atlanFieldName,
            Asset.DESCRIPTION.atlanFieldName,
            Asset.USER_DESCRIPTION.atlanFieldName,
            Asset.OWNER_USERS.atlanFieldName,
            Asset.OWNER_GROUPS.atlanFieldName,
            Asset.HAS_LINEAGE.atlanFieldName,
            ICatalog.INPUT_TO_PROCESSES.atlanFieldName,
            ICatalog.OUTPUT_FROM_PROCESSES.atlanFieldName,
        )
        private const val ENFORCEMENT_MESSAGE = "To be verified, an asset must have a description, at least one owner, and lineage."

        // Note: we can just re-use the default validatePrerequisites

        /** {@inheritDoc}  */
        @Throws(AtlanException::class)
        override fun getCurrentState(client: AtlanClient, fromEvent: Asset, log: Logger): Asset {
            return AtlanEventHandler.getCurrentViewOfAsset(client, fromEvent, REQUIRED_ATTRS, false, false)
                ?: throw NotFoundException(
                    ErrorCode.ASSET_NOT_FOUND_BY_QN, fromEvent.qualifiedName, fromEvent.typeName,
                )
        }

        /** {@inheritDoc}  */
        @Throws(AtlanException::class)
        override fun calculateChanges(asset: Asset, log: Logger): Collection<Asset> {
            // We only need to consider enforcement if the asset is currently verified
            if (asset.certificateStatus == CertificateStatus.VERIFIED) {
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
    }
}
