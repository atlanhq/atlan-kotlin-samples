/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.model.fields.AtlanField
import com.atlan.model.fields.SearchableField
import com.atlan.samples.loaders.AssetLoader
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Actually run the importer.
 * Note: all parameters should be passed through environment variables.
 */
fun main() {
    Utils.setClient()
    Utils.setWorkflowOpts()

    val attrsToOverwrite = attributesToClear("ATTR_TO_OVERWRITE")
    log.info("Adding attributes to be cleared, if blank: {}", attrsToOverwrite)
    val importer = Importer(attrsToOverwrite)
    importer.handleRequest(Utils.environmentVariables(), null)
}

/**
 * Determine which (if any) attributes should be cleared (removed) if they are empty in the input file.
 *
 * @param varName name of environment variable containing the list of attributes
 * @return parsed list of attribute names to be cleared
 */
fun attributesToClear(varName: String): List<String> {
    val toOverwrite = Utils.getEnvVar(varName, "")
    val attrsToOverwrite: MutableList<String> = if (toOverwrite != "") {
        jacksonObjectMapper().readValue(toOverwrite)
    } else {
        mutableListOf()
    }
    if (attrsToOverwrite.contains("certificateStatus")) {
        attrsToOverwrite.add("certificateStatusMessage")
    }
    if (attrsToOverwrite.contains("announcementType")) {
        attrsToOverwrite.add("announcementTitle")
        attrsToOverwrite.add("announcementMessage")
    }
    return attrsToOverwrite
}

/**
 * Import assets into Atlan from a provided CSV file.
 *
 * Only the assets and attributes in the provided CSV file will attempt to be loaded.
 * By default, any blank values in a cell in the CSV file will be ignored. If you would like any
 * particular column's blank values to actually overwrite (i.e. remove) existing values for that
 * asset in Atlan, then add that column's field to getAttributesToOverwrite.
 *
 * @param attrNamesToOverwrite names of the attributes in Atlan to overwrite
 */
class Importer(attrNamesToOverwrite: List<String>) : AssetLoader() {

    private val attrsToOverwrite: MutableList<AtlanField> = mutableListOf()

    init {
        for (name in attrNamesToOverwrite) {
            attrsToOverwrite.add(SearchableField(name, name))
        }
    }

    /** {@inheritDoc} */
    override fun getAttributesToOverwrite(): MutableList<AtlanField> {
        return attrsToOverwrite
    }

    /** {@inheritDoc} */
    override fun parseParametersFromEvent(event: MutableMap<String, String>) {
        // We intentionally do NOT call the superclass, to avoid forcing an API-token based client setup
        // ... and we just fix a filename here rather than generating one with a timestamp
        filename = event.getOrDefault("FILENAME", "")
    }
}
