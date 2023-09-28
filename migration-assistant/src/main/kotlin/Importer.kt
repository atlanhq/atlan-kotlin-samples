/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.model.fields.AtlanField
import com.atlan.samples.loaders.AssetLoader

/**
 * Actually run the importer.
 * Note: all parameters should be passed through environment variables.
 */
fun main() {
    Utils.setClient()
    Utils.setWorkflowOpts()

    val importer = Importer()
    importer.handleRequest(Utils.environmentVariables(), null)
}

/**
 * Import assets into Atlan from a provided CSV file.
 *
 * Only the assets and attributes in the provided CSV file will attempt to be loaded.
 * By default, any blank values in a cell in the CSV file will be ignored. If you would like any
 * particular column's blank values to actually overwrite (i.e. remove) existing values for that
 * asset in Atlan, then add that column's field to getAttributesToOverwrite.
 */
class Importer : AssetLoader() {

    /** {@inheritDoc} */
    override fun getAttributesToOverwrite(): MutableList<AtlanField> {
        return mutableListOf() // TODO: take these in from workflow config
    }

    /** {@inheritDoc} */
    override fun parseParametersFromEvent(event: MutableMap<String, String>) {
        // We intentionally do NOT call the superclass, to avoid forcing an API-token based client setup
        // ... and we just fix a filename here rather than generating one with a timestamp
        filename = event.getOrDefault("FILENAME", "")
    }
}
