/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.pkg.CustomPackage
import com.atlan.pkg.config.model.ui.UIConfig
import com.atlan.pkg.config.model.ui.UIRule
import com.atlan.pkg.config.model.ui.UIStep
import com.atlan.pkg.config.model.workflow.WorkflowOutputs
import com.atlan.pkg.config.widgets.DropDown
import com.atlan.pkg.config.widgets.NumericInput
import com.atlan.pkg.config.widgets.Radio

/**
 * Definition for the MigrationAssistantImport custom package.
 */
object MigrationAssistantImportPkg : CustomPackage(
    "@csa/migration-assistant-import",
    "Migration Assistant Import",
    "Import manually-enriched assets from a CSV file.",
    "http://assets.atlan.com/assets/ph-cloud-arrow-up-light.svg",
    "https://developer.atlan.com/samples/packages/migration-assistant-import/",
    uiConfig = UIConfig(
        steps = listOf(
            UIStep(
                title = "Configuration",
                description = "Export configuration",
                inputs = mapOf(
                    "s3_details" to TODO("S3 connectivity details widget -- reuse? atlan/connectors/configmaps/csa-lineage-asset-loader.yaml"),
                    "attr_remove" to DropDown(
                        label = "Remove attributes, if empty",
                        required = false,
                        possibleValues = mapOf(
                            "certificateStatus" to "Certificate",
                            "announcementType" to "Announcement",
                            "description" to "Description (system)",
                            "userDescription" to "Description (user)",
                            "ownerUsers" to "Owners (users)",
                            "ownerGroups" to "Owners (groups)",
                            "assignedTerms" to "Assigned terms",
                            "readme" to "README",
                        ),
                        help = "Select attributes you want to clear (remove) from assets if their value is blank in the provided file.",
                        multiSelect = true,
                        grid = 8,
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
                        placeholder = "20",
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
    containerImage = "ghcr.io/atlanhq/atlan-kotlin-samples:0.3.0",
    containerCommand = listOf("/dumb-init", "--", "java", "ImporterKt"),
    outputs = WorkflowOutputs(mapOf("debug-logs" to "/tmp/debug.log")),
    keywords = listOf("kotlin", "utility"),
    preview = true,
) {
    @JvmStatic
    fun main(args: Array<String>) {
        createPackageFiles("packages/migration-assistant-import/src/main/resources")
    }
}
