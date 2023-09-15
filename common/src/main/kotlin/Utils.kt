/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.Atlan
import mu.KLogger
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.round

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
     * Increments the provided counter by 1 and logs the progress of the job.
     * Note: if batchSize is provided, will only log progress in increments of the batchSize.
     *
     * @param counter an atomic counter indicating how many things have been processed
     * @param total total number of things to be done
     * @param logger through which to report the overall progress
     * @param batchSize number of things that are done per batch of operations
     */
    fun logProgress(counter: AtomicLong, total: Long, logger: KLogger, batchSize: Int = -1) {
        val localCount = counter.incrementAndGet()
        if (batchSize > 0) {
            if (localCount.mod(batchSize) == 0) {
                logger.info(
                    " ... processed {}/{} ({}%)",
                    localCount,
                    total,
                    round((localCount.toDouble() / total) * 100),
                )
            }
        } else {
            logger.info(
                " ... processed {}/{} ({}%)",
                localCount,
                total,
                round((localCount.toDouble() / total) * 100),
            )
        }
    }

    /**
     * Check if the utility is being run through a workflow, and if it is set up the various
     * workflow headers from the relevant environment variables against the default client.
     */
    fun setWorkflowOpts() {
        val atlanAgent = getEnvVar("X_ATLAN_AGENT", "")
        if (atlanAgent == "workflow") {
            val headers = Atlan.getDefaultClient().extraHeaders
            headers.put("x-atlan-agent", listOf("workflow"))
            headers.put("x-atlan-agent-package-name", listOf(getEnvVar("X_ATLAN_AGENT_PACKAGE_NAME", "")))
            headers.put("x-atlan-workflow-id", listOf(getEnvVar("X_ATLAN_WORKFLOW_ID", "")))
            headers.put("x-atlan-agent-id", listOf(getEnvVar("X_ATLAN_AGENT_ID", "")))
            Atlan.getDefaultClient().extraHeaders = headers
        }
    }
}
