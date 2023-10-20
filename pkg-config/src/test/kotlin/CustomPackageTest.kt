/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.pkg.CustomPackage
import com.atlan.pkg.config.model.ui.UIConfig
import com.atlan.pkg.config.model.ui.UIRule
import com.atlan.pkg.config.model.ui.UIStep
import com.atlan.pkg.config.widgets.ConnectionCreator
import com.atlan.pkg.config.widgets.ConnectionSelector
import com.atlan.pkg.config.widgets.Radio
import com.atlan.pkg.config.widgets.TextInput
import kotlin.test.Test
import kotlin.test.assertNotNull

class CustomPackageTest {

    companion object {
        private val uiConfig = UIConfig(
            steps = listOf(
                UIStep(
                    title = "Configuration",
                    description = "OpenAPI spec configuration",
                    inputs = mapOf(
                        "spec_url" to TextInput(
                            label = "Specification URL",
                            required = true,
                            help = "Full URL to the JSON form of the OpenAPI specification.",
                            placeholder = "https://petstore3.swagger.io/api/v3/openapi.json",
                        ),
                    ),
                ),
                UIStep(
                    title = "Connection",
                    description = "Connection details",
                    inputs = mapOf(
                        "connection_usage" to Radio(
                            label = "Connection",
                            required = true,
                            possibleValues = mapOf(
                                "CREATE" to "Create",
                                "REUSE" to "Reuse",
                            ),
                            default = "REUSE",
                            help = "Whether to create a new connection to hold these API assets, or reuse an existing connection.",
                        ),
                        "connection" to ConnectionCreator(
                            label = "Connection",
                            required = true,
                            help = "Enter details for a new connection to be created.",
                            placeholder = "Swagger",
                        ),
                        "connection_qualified_name" to ConnectionSelector(
                            label = "Connection",
                            required = true,
                            help = "Select an existing connection to load assets into.",
                        ),
                    ),
                ),
            ),
            rules = listOf(
                UIRule(
                    whenInputs = mapOf("connection_usage" to "REUSE"),
                    required = listOf("connection_qualified_name"),
                ),
                UIRule(
                    whenInputs = mapOf("connection_usage" to "CREATE"),
                    required = listOf("connection"),
                ),
            ),
        )

        private val pkg = CustomPackage(
            "csa-openapi-spec-loader",
            uiConfig,
            "ghcr.io/atlanhq/atlan-kotlin-samples:0.2.1",
            listOf("/dumb-init", "--", "java", "OpenAPISpecLoaderKt"),
        )
    }

    @Test
    fun serializeConfig() {
        val configMap = pkg.configMapYAML()
        assertNotNull(configMap)
        print(configMap)
    }

    @Test
    fun serializeWorkflow() {
        val workflowTemplate = pkg.workflowTemplateYAML()
        assertNotNull(workflowTemplate)
        print(workflowTemplate)
    }
}
