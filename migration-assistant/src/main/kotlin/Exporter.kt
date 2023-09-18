/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.Atlan
import com.atlan.model.assets.Asset
import com.atlan.model.assets.AtlanQuery
import com.atlan.model.assets.AuthPolicy
import com.atlan.model.assets.IAccessControl
import com.atlan.model.assets.INamespace
import com.atlan.model.assets.Link
import com.atlan.model.assets.Procedure
import com.atlan.model.fields.AtlanField
import com.atlan.model.fields.CustomMetadataField
import com.atlan.model.search.FluentSearch
import com.atlan.samples.reporters.AssetReporter

/**
 * Export assets from Atlan, in one of two modes (defined by EXPORT_SCOPE environment variable):
 * - ENRICHED_ONLY — will only export assets that have had some UI-controlled field enriched (default)
 * - ALL — will export all assets
 *
 * In both cases the overall scope of assets to include is restricted by the qualifiedName prefix
 * specified by the QN_PREFIX environment variable.
 */
class Exporter : AssetReporter() {

    /** {@inheritDoc} */
    override fun getAssetsToExtract(event: Map<String, String>): FluentSearch.FluentSearchBuilder<*, *> {
        val scope = event.getOrDefault("EXPORT_SCOPE", "ENRICHED_ONLY")
        val builder = Atlan.getDefaultClient().assets
            .select()
            .where(Asset.QUALIFIED_NAME.startsWith(event.getOrDefault("QN_PREFIX", "default")))
            .whereNot(FluentSearch.superTypes(listOf(IAccessControl.TYPE_NAME, INamespace.TYPE_NAME)))
            .whereNot(FluentSearch.assetTypes(listOf(AuthPolicy.TYPE_NAME, Procedure.TYPE_NAME, AtlanQuery.TYPE_NAME)))
        if (scope == "ENRICHED_ONLY") {
            builder
                .whereSome(Asset.CERTIFICATE_STATUS.hasAnyValue())
                .whereSome(Asset.DESCRIPTION.hasAnyValue())
                .whereSome(Asset.USER_DESCRIPTION.hasAnyValue())
                .whereSome(Asset.ANNOUNCEMENT_TYPE.hasAnyValue())
                .whereSome(Asset.ASSIGNED_TERMS.hasAnyValue())
                .whereSome(Asset.ATLAN_TAGS.hasAnyValue())
                .whereSome(Asset.README.hasAny())
                .whereSome(Asset.LINKS.hasAny())
                .whereSome(Asset.STARRED_BY.hasAnyValue())
                .minSomes(1)
            for (cmField in CustomMetadataFields.all) {
                builder.whereSome(cmField.hasAnyValue())
            }
        }
        return builder
    }

    /** {@inheritDoc} */
    override fun getAttributesToExtract(event: Map<String, String>): MutableList<AtlanField> {
        val attributeList: MutableList<AtlanField> = mutableListOf(
            Asset.NAME,
            Asset.DESCRIPTION,
            Asset.USER_DESCRIPTION,
            Asset.OWNER_USERS,
            Asset.OWNER_GROUPS,
            Asset.CERTIFICATE_STATUS,
            Asset.CERTIFICATE_STATUS_MESSAGE,
            Asset.ANNOUNCEMENT_TYPE,
            Asset.ANNOUNCEMENT_TITLE,
            Asset.ANNOUNCEMENT_MESSAGE,
            Asset.ASSIGNED_TERMS,
            Asset.ATLAN_TAGS,
            Asset.LINKS,
            Asset.README,
            Asset.STARRED_DETAILS,
        )
        for (cmField in CustomMetadataFields.all) {
            attributeList.add(cmField)
        }
        return attributeList
    }

    /** {@inheritDoc} */
    override fun getRelatedAttributesToExtract(event: Map<String, String>): MutableList<AtlanField> {
        return mutableListOf(
            Asset.NAME, // for Link embedding
            Asset.DESCRIPTION, // for README embedding
            Link.LINK, // for Link embedding
        )
    }
}

object CustomMetadataFields {

    val all: List<CustomMetadataField>

    init {
        all = loadCustomMetadataFields()
    }

    /**
     * Retrieve all custom metadata fields for attributes that exist in the tenant.
     *
     * @return a list of all custom metadata fields defined in the tenant
     */
    private fun loadCustomMetadataFields(): List<CustomMetadataField> {
        val customMetadataDefs = Atlan.getDefaultClient().customMetadataCache
            .getAllCustomAttributes(false, true)
        val fields = mutableListOf<CustomMetadataField>()
        for ((setName, attributes) in customMetadataDefs) {
            for (attribute in attributes) {
                fields.add(CustomMetadataField(Atlan.getDefaultClient(), setName, attribute.displayName))
            }
        }
        return fields
    }
}

/**
 * Actually run the export, taking all settings from environment variables.
 * Note: all parameters should be passed through environment variables.
 */
fun main() {
    val exporter = Exporter()
    exporter.handleRequest(Utils.environmentVariables(), null)
}
