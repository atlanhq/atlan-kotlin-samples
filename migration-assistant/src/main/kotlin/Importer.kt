/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.model.fields.AtlanField
import com.atlan.samples.loaders.AssetLoader

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
        TODO("Not yet implemented")
    }
}

/**
 * Actually run the importer.
 * Note: all parameters should be passed through environment variables.
 */
fun main() {
    val importer = Importer()
    importer.handleRequest(Utils.environmentVariables(), null)
}
