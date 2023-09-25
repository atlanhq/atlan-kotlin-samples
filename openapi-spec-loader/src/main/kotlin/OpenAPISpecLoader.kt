/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.Atlan
import com.atlan.exception.AtlanException
import com.atlan.exception.NotFoundException
import com.atlan.model.assets.APIPath
import com.atlan.model.assets.APISpec
import com.atlan.model.assets.Connection
import com.atlan.model.core.AssetMutationResponse
import com.atlan.model.enums.AtlanConnectorType
import com.atlan.util.AssetBatch
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.parser.OpenAPIV3Parser
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

/**
 * Actually run the loader, taking all settings from environment variables.
 * Note: all parameters should be passed through environment variables.
 */
fun main() {
    Utils.setClient()
    Utils.setWorkflowOpts()

    val connectionName = Utils.getEnvVar("CONNECTION_NAME", "")
    val specUrl = Utils.getEnvVar("SPEC_URL", "")
    val batchSize = Utils.getEnvVar("BATCH_SIZE", "50").toInt()

    if (connectionName == "" || specUrl == "") {
        log.error("Missing required parameter — you must provide BOTH a connection name and specification URL.")
        exitProcess(1)
    }

    log.info("Loading OpenAPI specification {} from: {}", connectionName, specUrl)

    val connectionQN = findOrCreateConnection(connectionName)
    val parser = OpenAPISpecReader(specUrl)
    loadOpenAPISpec(connectionQN, parser, batchSize)
}

/**
 * Find an existing connection, or create a new one if an existing connection does not already exist.
 *
 * @param name of the connection (type is fixed as API)
 * @return the qualifiedName of the connection
 */
fun findOrCreateConnection(name: String): String {
    log.info("Searching for existing API connection named: {}", name)
    var connectionQN = ""
    try {
        val found = Connection.findByName(name, AtlanConnectorType.API)
        if (found.size > 1) {
            log.warn(" ... found multiple API connections with the name {} — using only the first.", name)
        }
        connectionQN = found[0].qualifiedName
        log.info(" ... re-using: {} ({})", name, connectionQN)
    } catch (e: NotFoundException) {
        log.info(" ... none found, creating a new API connection")
        val toCreate = Connection.creator(
            name,
            AtlanConnectorType.API,
            listOf(Atlan.getDefaultClient().roleCache.getIdForName("\$admin")),
            null,
            null,
        ).build()
        try {
            val response = toCreate.save().block()
            if (response != null && response.createdAssets.size == 1) {
                connectionQN = response.createdAssets[0].qualifiedName
                log.info(" ... created connection: {}", connectionQN)
            }
        } catch (create: AtlanException) {
            log.error("Unable to create aa connection for the API.", create)
            exitProcess(2)
        }
    } catch (find: AtlanException) {
        log.error("Unable to even attempt to find an existing connection for the API.", find)
        exitProcess(3)
    }
    if (connectionQN == "") {
        log.error("Unable to find an existing or create a new connection for the API.")
        exitProcess(4)
    }
    return connectionQN
}

/**
 * Process the OpenAPI spec and create relevant assets in Atlan.
 *
 * @param connectionQN qualifiedName of the connection in which to create the assets
 * @param spec object for reading from the OpenAPI spec itself
 * @param batchSize maximum number of assets to save per API request
 */
fun loadOpenAPISpec(connectionQN: String, spec: OpenAPISpecReader, batchSize: Int) {
    val toCreate = APISpec.creator(spec.title, connectionQN)
        .sourceURL(spec.sourceURL)
        .apiSpecType(spec.openAPIVersion)
        .description(spec.description)
        .apiSpecTermsOfServiceURL(spec.termsOfServiceURL)
        .apiSpecContactEmail(spec.contactEmail)
        .apiSpecContactName(spec.contactName)
        .apiSpecContactURL(spec.contactURL)
        .apiSpecLicenseName(spec.licenseName)
        .apiSpecLicenseURL(spec.licenseURL)
        .apiSpecVersion(spec.version)
        .apiExternalDoc("url", spec.externalDocsURL)
        .apiExternalDoc("description", spec.externalDocsDescription)
        .build()
    val specQN = toCreate.qualifiedName
    log.info("Saving APISpec: {}", specQN)
    try {
        val response = toCreate.save()
        val mutation = response.getMutation(toCreate)
        if (mutation in listOf(AssetMutationResponse.MutationType.NOOP, AssetMutationResponse.MutationType.UNKNOWN)) {
            log.info(" ... reusing existing APISpec: {}", toCreate.qualifiedName)
        } else {
            log.info(" ... {} APISpec: {}", mutation.name, toCreate.qualifiedName)
        }
    } catch (e: AtlanException) {
        log.error("Unable to save the APISpec.", e)
        exitProcess(5)
    }
    val batch = AssetBatch(Atlan.getDefaultClient(), APIPath.TYPE_NAME, batchSize, false, AssetBatch.CustomMetadataHandling.MERGE, true)
    val totalCount = spec.paths?.size!!.toLong()
    if (totalCount > 0) {
        log.info("Creating an APIPath for each path defined within the spec (total: {})", totalCount)
        try {
            val assetCount = AtomicLong(0)
            for (apiPath in spec.paths.entries) {
                val pathUrl = apiPath.key
                val pathDetails = apiPath.value
                val operations = mutableListOf<String>()
                val desc = StringBuilder()
                desc.append("| Method | Summary|\n|---|---|\n")
                addOperationDetails(pathDetails.get, "GET", operations, desc)
                addOperationDetails(pathDetails.post, "POST", operations, desc)
                addOperationDetails(pathDetails.put, "PUT", operations, desc)
                addOperationDetails(pathDetails.patch, "PATCH", operations, desc)
                addOperationDetails(pathDetails.delete, "DELETE", operations, desc)
                val path = APIPath.creator(pathUrl, specQN)
                    .description(desc.toString())
                    .apiPathRawURI(pathUrl)
                    .apiPathSummary(pathDetails.summary)
                    .apiPathAvailableOperations(operations)
                    .apiPathIsTemplated(pathUrl.contains("{") && pathUrl.contains("}"))
                    .build()
                batch.add(path)
                Utils.logProgress(assetCount, totalCount, log, batchSize)
            }
            batch.flush()
            Utils.logProgress(assetCount, totalCount, log, batchSize)
        } catch (e: AtlanException) {
            log.error("Unable to bulk-save API paths.", e)
        }
    }
}

/**
 * Add the details of the provided operation to the details captured for the APIPath.
 *
 * @param operation the operation to include (if non-null) as one that exists for the path
 * @param name the name of the operation
 * @param operations the overall list of operations to which to append
 * @param description the overall description of the APIPath to which to append
 */
fun addOperationDetails(operation: Operation?, name: String, operations: MutableList<String>, description: StringBuilder) {
    if (operation != null) {
        operations.add(name)
        description.append("| `").append(name).append("` |").append(operation.summary).append(" |\n")
    }
}

/**
 * Utility class for parsing and reading the contents of an OpenAPI spec file,
 * using the Swagger parser.
 */
class OpenAPISpecReader(url: String) {

    private val spec: OpenAPI

    val sourceURL: String
    val openAPIVersion: String
    val paths: Paths?
    val title: String
    val description: String
    val termsOfServiceURL: String
    val version: String
    val contactEmail: String
    val contactName: String
    val contactURL: String
    val licenseName: String
    val licenseURL: String
    val externalDocsURL: String
    val externalDocsDescription: String

    init {
        spec = OpenAPIV3Parser().read(url)
        sourceURL = url
        openAPIVersion = spec.openapi
        paths = spec.paths
        title = spec.info?.title ?: ""
        description = spec.info?.description ?: ""
        termsOfServiceURL = spec.info?.termsOfService ?: ""
        version = spec.info?.version ?: ""
        contactEmail = spec.info?.contact?.email ?: ""
        contactName = spec.info?.contact?.name ?: ""
        contactURL = spec.info?.contact?.url ?: ""
        licenseName = spec.info?.license?.name ?: ""
        licenseURL = spec.info?.license?.url ?: ""
        externalDocsURL = spec.externalDocs?.url ?: ""
        externalDocsDescription = spec.externalDocs?.description ?: ""
    }
}
