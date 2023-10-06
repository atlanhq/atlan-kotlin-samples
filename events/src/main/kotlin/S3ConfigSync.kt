/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import java.io.File

/**
 * Utility class to synchronize configuration for an event-driven pipeline from JSON
 * files, themselves configured via a workflow template in the UI.
 * Note: this relies on two files being produced by the workflow configuration:
 * 1. config.json -- containing at least an "api_token" key, giving the GUID of the API token for the pipeline to execute through
 * 2. runtime.json -- containing the details of the workflow (X_ATLAN_AGENT_* headers), for injection into audit logs
 */
class S3ConfigSync(private val localPath: String, private val configPrefix: String) {

    private val logger = KotlinLogging.logger {}

    private val bucketName = Utils.getEnvVar("AWS_S3_BUCKET_NAME", "")
    private val region = Utils.getEnvVar("AWS_S3_REGION", "ap-south-1")

    companion object {
        const val CONFIG_FILE = "/tmp/config.json"
        const val RUNTIME_FILE = "/tmp/runtime.json"
        val MAPPER = jacksonObjectMapper()
    }

    /**
     * Download any configuration files from S3 into the running container.
     *
     * @return true if any files were synced, otherwise false
     */
    fun sync(): Boolean {
        logger.info("Syncing configuration from s3://$bucketName/$configPrefix to $localPath")

        val s3Client = S3Client.builder().region(Region.of(region)).build()
        val request = ListObjectsV2Request.builder()
            .bucket(bucketName)
            .prefix(configPrefix)
            .build()

        val localFilesLastModified = File(localPath).walkTopDown().filter { it.isFile }.map {
            it.relativeTo(File(localPath)).path to it.lastModified()
        }.toMap()

        val s3FilesToDownload = mutableListOf<String>()
        s3Client.listObjectsV2(request).contents().forEach { file ->
            val key = File(file.key()).relativeTo(File(configPrefix)).path
            if (key !in localFilesLastModified || file.lastModified().toEpochMilli() > localFilesLastModified[key]!!) {
                s3FilesToDownload.add(key)
            }
        }

        var anySynced = false

        s3FilesToDownload.forEach {
            val localFile = File(localPath, it)
            if (localFile.exists()) {
                localFile.delete()
            }
            if (!localFile.parentFile.exists()) {
                localFile.parentFile.mkdirs()
            }
            val s3Prefix = File(configPrefix, it).path
            logger.info("Downloading s3://$bucketName/$s3Prefix to ${localFile.path}")
            s3Client.getObject(
                GetObjectRequest.builder().bucket(bucketName).key(s3Prefix).build(),
                localFile.toPath(),
            )
            anySynced = true
        }

        if (anySynced) {
            Config.parse()
            Runtime.parse()
        }

        // TODO: return the requested configuration instance (or null if no config), rather than a simple boolean
        return anySynced
    }

    // TODO: have this receive an object type, and parse the config into an instance of that object
    //  and return it, so the configuration itself is strongly typed
    private object Config {
        fun parse(): Map<String, Any> {
            return MAPPER.readValue<Map<String, Any>>(File(CONFIG_FILE).readText())
        }
    }

    // All runtime configuration comes through these settings:
    // - will impersonate all pipeline activity as the user who configured the workflow
    // - will set update details (in audit log) based on details of the workflow
    private object Runtime {
        fun parse() {
            val config = MAPPER.readValue<MutableMap<String, String>>(File(RUNTIME_FILE).readText())
            Utils.setClient(config["user-id"] ?: "")
            Utils.setWorkflowOpts(config)
        }
    }
}
