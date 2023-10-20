/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
package com.atlan.pkg

import com.atlan.pkg.config.model.ConfigMap
import com.atlan.pkg.config.model.WorkflowTemplate
import com.atlan.pkg.config.model.ui.UIConfig
import com.atlan.pkg.config.model.workflow.WorkflowContainer
import com.atlan.pkg.config.model.workflow.WorkflowOutputs
import com.atlan.pkg.config.model.workflow.WorkflowTemplateDefinition
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

/**
 * Single class through which you can define a custom package.
 *
 * @param name of the custom package
 * @param uiConfig configuration for the UI of the custom package
 * @param image container image to run the logic of the custom package
 * @param command the full command to run in the container image, as a list rather than spaced
 * @param imagePullPolicy (optional) override the default IfNotPresent policy
 * @param outputs (optional) any outputs that the custom package logic is expected to produce
 */
class CustomPackage(
    private val name: String,
    private val uiConfig: UIConfig,
    private val image: String,
    private val command: List<String>,
    private val imagePullPolicy: String = "IfNotPresent",
    private val outputs: WorkflowOutputs? = null,
) {
    private val configMap = ConfigMap(name, uiConfig)
    private val workflowTemplate = WorkflowTemplate(
        name,
        WorkflowTemplateDefinition(
            uiConfig,
            WorkflowContainer(
                uiConfig,
                image,
                command = listOf(command[0]),
                args = command.subList(1, command.size),
                imagePullPolicy,
            ),
            outputs,
        ),
    )

    /**
     * Retrieve the YAML for the ConfigMap of the custom package.
     *
     * @return configmap YAML
     */
    fun configMapYAML(): String {
        return yaml.writeValueAsString(configMap)
    }

    /**
     * Retrieve the YAML for the WorkflowTemplate of the custom package.
     *
     * @return workflowtemplate YAML
     */
    fun workflowTemplateYAML(): String {
        return yaml.writeValueAsString(workflowTemplate)
    }

    companion object {
        val yaml = YAMLMapper.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .disable(YAMLGenerator.Feature.SPLIT_LINES)
            .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
            .build()
            .registerKotlinModule()
    }
}
