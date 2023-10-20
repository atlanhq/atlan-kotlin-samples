/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
package com.atlan.pkg.config.model.workflow

import com.atlan.pkg.config.model.ui.UIConfig
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("image", "imagePullPolicy", "command", "args", "env")
class WorkflowContainer(
    @JsonIgnore val config: UIConfig,
    val image: String,
    val command: List<String>,
    val args: List<String> = listOf(),
    val imagePullPolicy: String = "IfNotPresent",
) {
    val env: List<NamedPair>
    init {
        val builder = mutableListOf<NamedPair>()
        builder.add(NameValuePair("ATLAN_BASE_URL", "INTERNAL"))
        builder.add(NameValuePair("ATLAN_USER_ID", "{{=sprig.dig('labels', 'workflows', 'argoproj', 'io/creator', '', workflow)}}"))
        builder.add(NameValuePair("X_ATLAN_AGENT", "workflow"))
        builder.add(NameValuePair("X_ATLAN_AGENT_ID", "{{workflow.name}}"))
        builder.add(NameValuePair("X_ATLAN_AGENT_PACKAGE_NAME", "{{=sprig.dig('annotations', 'package', 'argoproj', 'io/name', '', workflow)}}"))
        builder.add(NameValuePair("X_ATLAN_AGENT_WORKFLOW_ID", "{{=sprig.dig('labels', 'workflows', 'argoproj', 'io/workflow-template', '', workflow)}}"))
        builder.add(NamedSecret("CLIENT_ID", "argo-client-creds", "login"))
        builder.add(NamedSecret("CLIENT_SECRET", "argo-client-creds", "password"))
        config.properties.forEach {
            builder.add(NameValuePair(it.key.uppercase(), "{{inputs.parameters.${it.key}}}"))
        }
        env = builder.toList()
    }
}
