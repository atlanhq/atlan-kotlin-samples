/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.pkg.CustomPackage
import com.atlan.pkg.config.model.ui.UIConfig
import com.atlan.pkg.config.model.ui.UIRule
import com.atlan.pkg.config.model.ui.UIStep
import com.atlan.pkg.config.model.workflow.WorkflowOutputs
import com.atlan.pkg.config.widgets.NumericInput
import com.atlan.pkg.config.widgets.Radio
import com.atlan.pkg.config.widgets.TextInput

/**
 * Definition for the MigrationAssistantExport custom package.
 */
object MigrationAssistantExportPkg : CustomPackage(
    "@csa/migration-assistant-export",
    "Migration Assistant Export",
    "Export manually-enriched assets and the manual enrichments made against them.",
    "http://assets.atlan.com/assets/ph-cloud-arrow-down-light.svg",
    "https://atlanhq.github.io/marketplace-csa-scripts/migration-assistant/export/",
    uiConfig = UIConfig(
        steps = listOf(
            UIStep(
                title = "Configuration",
                description = "Export configuration",
                inputs = mapOf(
                    "export_scope" to Radio(
                        label = "Export scope",
                        required = true,
                        possibleValues = mapOf(
                            "ENRICHED_ONLY" to "Enriched only",
                            "ALL" to "All",
                        ),
                        default = "ENRICHED_ONLY",
                        help = "Whether to export only those assets that were enriched by users, or all assets with the qualified name prefix.",
                    ),
                    "qn_prefix" to TextInput(
                        label = "Qualified name prefix",
                        required = false,
                        help = "Starting value for a qualifiedName that will determine which assets to export.",
                        placeholder = "default",
                        grid = 6,
                    ),
                    "control_config_strategy" to Radio(
                        label = "Options",
                        required = true,
                        possibleValues = mapOf(
                            "default" to "Default",
                            "advanced" to "Advanced",
                        ),
                        default = "default",
                        help = "Options to optimize how the utility runs.",
                    ),
                    "batch_size" to NumericInput(
                        label = "Batch size",
                        required = false,
                        help = "Maximum number of results to process at a time (per API request).",
                        placeholder = "50",
                        grid = 4,
                    ),
                ),
            ),
        ),
        rules = listOf(
            UIRule(
                whenInputs = mapOf("control_config_strategy" to "advanced"),
                required = listOf("batch_size"),
            ),
        ),
    ),
    containerImage = "ghcr.io/atlanhq/atlan-kotlin-samples:0.4.0",
    containerCommand = listOf("/dumb-init", "--", "java", "ExporterKt"),
    outputs = WorkflowOutputs(
        mapOf(
            "debug-logs" to "/tmp/debug.log",
            "assets-csv" to "/tmp/asset-export.csv",
        ),
    ),
    keywords = listOf("kotlin", "utility"),
    preview = true,
) {
    @JvmStatic
    fun main(args: Array<String>) {
        createPackageFiles("packages/migration-assistant-export/src/main/resources")
    }
}
