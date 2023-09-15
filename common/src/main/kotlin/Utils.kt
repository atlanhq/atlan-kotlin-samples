/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.Atlan
import com.atlan.net.RequestOptions
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Common utilities for using the Atlan SDK within Kotlin.
 */
object Utils {

    /**
     * Set up the default Atlan client, based on environment variables.
     * This will use an API token if found in ATLAN_API_KEY, and will fallback to attempting
     * to impersonate a user if ATLAN_API_KEY is empty.
     */
    fun setClient() {
        val baseUrl = getEnvVar("ATLAN_BASE_URL", "INTERNAL")
        val apiToken = getEnvVar("ATLAN_API_KEY", "")
        Atlan.setBaseUrl(baseUrl)
        if (apiToken.isEmpty()) {
            val userId = getEnvVar("ATLAN_USER_ID", "")
            log.info("No API token found, attempting to impersonate user: {}", userId)
            val defClient = Atlan.getDefaultClient()
            val userToken = defClient.impersonate.user(userId)
            Atlan.setApiToken(userToken)
        } else {
            Atlan.setApiToken(apiToken)
        }
    }

    /**
     * Retrieves the value of an environment variable (if found), or if not found or empty
     * gives the specified default value instead.
     *
     * @param name of the environment variable
     * @param default value to give the variable, if it is not found or empty
     * @return the value (or default) of the environment variable
     */
    fun getEnvVar(name: String, default: String): String {
        val candidate = System.getenv(name)
        return if (candidate != null && candidate.isNotEmpty()) candidate else default
    }

    /**
     * Check if the utility is being run through a workflow, and if it is set up the various
     * workflow headers from the relevant environment variables.
     *
     * @return request options with the additional headers for the workflow details
     */
    fun getWorkflowOpts(): RequestOptions {
        val atlanAgent = getEnvVar("X_ATLAN_AGENT", "")
        return if (atlanAgent == "workflow") {
            RequestOptions.from(Atlan.getDefaultClient())
                .extraHeader("x-atlan-agent", listOf("workflow"))
                .extraHeader("x-atlan-agent-package-name", listOf(getEnvVar("X_ATLAN_AGENT_PACKAGE_NAME", "")))
                .extraHeader("x-atlan-workflow-id", listOf(getEnvVar("X_ATLAN_WORKFLOW_ID", "")))
                .extraHeader("x-atlan-agent-id", listOf(getEnvVar("X_ATLAN_AGENT_ID", "")))
                .build()
        } else {
            RequestOptions.from(Atlan.getDefaultClient()).build()
        }
    }
}
