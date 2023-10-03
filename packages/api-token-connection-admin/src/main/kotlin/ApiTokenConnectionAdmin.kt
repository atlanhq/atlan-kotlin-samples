/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.Atlan
import com.atlan.exception.AtlanException
import com.atlan.model.assets.Asset
import com.atlan.model.assets.Connection
import com.atlan.model.core.AssetMutationResponse
import mu.KotlinLogging
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

/**
 * Actually run the logic to add the API token as a connection admin.
 */
fun main() {
    Utils.setClient()
    Utils.setWorkflowOpts()

    val connectionQN = Utils.reuseConnection("CONNECTION_QUALIFIED_NAME")
    val apiTokenName = Utils.getEnvVar("API_TOKEN_NAME", "")

    if (connectionQN == "" || apiTokenName == "") {
        log.error("Missing required parameter - you must provide BOTH a connection and the name of an API token.")
        exitProcess(4)
    }

    val apiTokenId = getIdForToken(apiTokenName)
    val connection = getConnectionWithAdmins(connectionQN)
    addTokenAsConnectionAdmin(connection, apiTokenId)
}

/**
 * Retrieve the API token's pseudo-username, that can be used anywhere a username can be used.
 *
 * @param apiTokenName name of the API token for which to fetch the pseudo-username
 * @return the pseudo-username of the API token
 */
fun getIdForToken(apiTokenName: String): String {
    log.info("Looking up API token: {}", apiTokenName)
    val token = Atlan.getDefaultClient().apiTokens.get(apiTokenName)
    if (token == null) {
        log.error("Unable to find any API token with the name: {}", apiTokenName)
        exitProcess(5)
    }
    return "service-account-${token.clientId}"
}

/**
 * Retrieve the connection with its existing admins.
 *
 * @param connectionQN qualifiedName of the connection
 * @return the connection with its existing admins
 */
fun getConnectionWithAdmins(connectionQN: String): Asset {
    log.info("Looking up connection details: {}", connectionQN)
    val found = Connection.select()
        .where(Connection.QUALIFIED_NAME.eq(connectionQN))
        .includeOnResults(Connection.ADMIN_USERS)
        .stream()
        .findFirst()
    if (found.isEmpty) {
        log.error("Unable to find the specified connection: {}", connectionQN)
        exitProcess(6)
    }
    return found.get()
}

/**
 * Actually add the token as a connection admin, appending it to any pre-existing
 * connection admins (rather than replacing).
 *
 * @param connection the connection to add the API token to, with its existing admin users present
 * @param apiToken the API token to append as a connection admin
 */
fun addTokenAsConnectionAdmin(connection: Asset, apiToken: String) {
    log.info("Adding API token {} as connection admin for: {}", apiToken, connection.qualifiedName)
    val existingAdmins = connection.adminUsers
    try {
        val response = connection.trimToRequired()
            .adminUsers(existingAdmins)
            .adminUser(apiToken)
            .build()
            .save()
        when (val result = response?.getMutation(connection)) {
            AssetMutationResponse.MutationType.UPDATED -> log.info(" ... successfully updated the connection with API token as a new admin.")
            AssetMutationResponse.MutationType.NOOP -> log.info(" ... API token is already an admin on the connection - no changes made.")
            AssetMutationResponse.MutationType.CREATED -> log.error(" ... somehow created the connection - that should not have happened.")
            AssetMutationResponse.MutationType.DELETED -> log.error(" ... somehow deleted the connection - that should not have happened.")
            else -> {
                log.warn("Unexpected connection change result: {}", result)
            }
        }
    } catch (e: AtlanException) {
        log.error("Unable to add the API token as a connection admin.", e)
    }
}