/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.Atlan
import com.atlan.exception.AtlanException
import com.atlan.exception.NotFoundException
import com.atlan.model.assets.Connection
import mu.KLogger
import mu.KotlinLogging
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.round
import kotlin.system.exitProcess

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
            if (localCount.mod(batchSize) == 0 || localCount == total) {
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
            headers["x-atlan-agent"] = listOf("workflow")
            headers["x-atlan-agent-package-name"] = listOf(getEnvVar("X_ATLAN_AGENT_PACKAGE_NAME", ""))
            headers["x-atlan-agent-workflow-id"] = listOf(getEnvVar("X_ATLAN_AGENT_WORKFLOW_ID", ""))
            headers["x-atlan-agent-id"] = listOf(getEnvVar("X_ATLAN_AGENT_ID", ""))
            Atlan.getDefaultClient().extraHeaders = headers
        }
    }

    /**
     * Translates environment variables into a map of settings.
     * Sets defaults for:
     * - DELIMITER = |
     * - BATCH_SIZE = 50
     *
     * @return a map of settings drawn from environment variables
     */
    fun environmentVariables(): Map<String, String> {
        val map = System.getenv().toMutableMap()
        if (!map.containsKey("DELIMITER")) {
            map["DELIMITER"] = "|"
        }
        if (!map.containsKey("BATCH_SIZE")) {
            map["BATCH_SIZE"] = "50"
        }
        return map
    }

    /**
     * Either reuse (top priority) or create a new connection, based on the parameters provided.
     * Note that this method will exit if:
     * - it is unable to find a connection with the specified qualifiedName (rc = 1)
     * - it is unable to even parse the specified connection details (rc = 2)
     * - it is unable to create a new connection with the specified details (rc = 3)
     *
     * @param varForAction name of the environment variable containing the action to take: CREATE to create a new connection, or REUSE to reuse an existing connection
     * @param varForReuse name of the environment variable that should contain a connection qualifiedName, to reuse an existing connection
     * @param varForCreate name of the environment variable that should contain a full connection object, to create a new connection
     * @return the qualifiedName of the connection to use, whether reusing or creating, or an empty string if neither variable has any data in it
     */
    fun createOrReuseConnection(varForAction: String, varForReuse: String, varForCreate: String): String {
        val action = getEnvVar(varForAction, "REUSE")
        val connectionQN: String
        if (action == "REUSE") {
            val providedConnectionQN = getEnvVar(varForReuse, "")
            try {
                log.info("Attempting to reuse connection: {}", providedConnectionQN)
                Connection.get(Atlan.getDefaultClient(), providedConnectionQN, false)
            } catch (e: NotFoundException) {
                log.error("Unable to find connection with the provided qualifiedName: {}", providedConnectionQN, e)
                exitProcess(1)
            }
            connectionQN = providedConnectionQN
        } else {
            val connectionString = getEnvVar(varForCreate, "")
            connectionQN = if (connectionString != "") {
                log.info("Attempting to create new connection...")
                try {
                    val toCreate = Atlan.getDefaultClient().readValue(connectionString, Connection::class.java)
                    val response = toCreate.save().block()
                    response.getResult(toCreate).qualifiedName
                } catch (e: IOException) {
                    log.error("Unable to deserialize the connection details: {}", connectionString, e)
                    exitProcess(2)
                } catch (e: IllegalArgumentException) {
                    log.error("Unable to deserialize the connection details: {}", connectionString, e)
                    exitProcess(2)
                } catch (e: AtlanException) {
                    log.error("Unable to create connection: {}", connectionString, e)
                    exitProcess(3)
                }
            } else {
                ""
            }
        }
        return connectionQN
    }
}
