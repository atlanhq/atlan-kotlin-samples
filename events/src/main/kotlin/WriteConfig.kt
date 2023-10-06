/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import java.io.File
import java.io.FileOutputStream

/**
 * Utility to synchronize one or more configuration files from the in-tenant S3 bucket
 * to the container that will run pipeline processing (to feed configuration into that container).
 * Note: all configuration is received through environment variables.
 */
fun main() {
    val nestedConfig = Utils.getEnvVar("NESTED_CONFIG", "")
    val configFile = File("/tmp", "config.json")
    FileOutputStream(configFile).use {
        it.writer().write(nestedConfig)
    }
    val runtimeFile = File("/tmp", "runtime.json")
    FileOutputStream(runtimeFile).use {
        it.writer().write(buildRuntimeConfig())
    }
}

/**
 * Construct a JSON representation of the runtime configuration of the workflow, drawn from
 * a standard set of environment variables about the workflow.
 */
fun buildRuntimeConfig(): String {
    val userId = Utils.getEnvVar("ATLAN_USER_ID", "")
    val agent = Utils.getEnvVar("X_ATLAN_AGENT", "")
    val agentId = Utils.getEnvVar("X_ATLAN_AGENT_ID", "")
    val agentPkg = Utils.getEnvVar("X_ATLAN_AGENT_PACKAGE_NAME", "")
    val agentWfl = Utils.getEnvVar("X_ATLAN_AGENT_WORKFLOW_ID", "")
    return """
    {
        "user-id": "$userId",
        "x-atlan-agent": "$agent",
        "x-atlan-agent-id": "$agentId",
        "x-atlan-agent-package-name": "$agentPkg",
        "x-atlan-agent-workflow-id": "$agentWfl"
    }
    """.trimIndent()
}
