/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.Atlan
import config.EventConfig
import config.S3ConfigSync
import io.numaproj.numaflow.function.FunctionServer
import mu.KotlinLogging

/**
 * Utilities for working with event-processing pipelines.
 */
object EventUtils {

    val logger = KotlinLogging.logger {}

    /**
     * Set up the event-processing options, and start up the event processor.
     *
     * @return the configuration used to set up the event-processing handler, or null if no configuration was found
     */
    inline fun <reified T : EventConfig> setEventOps(): T? {
        logger.info("Looking for configuration in S3...")
        val config = S3ConfigSync().sync<T>()
        if (config == null) {
            logger.info("... no configuration found, will timeout pod and retry ...")
        } else {
            logger.info("Configuration found - synced to: /tmp/config.json")
            Utils.setClient(config.runtime.userId ?: "")
            Utils.setWorkflowOpts(config.runtime)
        }
        return config
    }

    /**
     * Update the configuration for the event-processing handler to run using the provided API token.
     *
     * @param apiTokenId unique identifier (GUID) of the API token
     */
    fun useApiToken(apiTokenId: String?) {
        if (apiTokenId != null) {
            val token =
                Atlan.getDefaultClient().apiTokens.list("{\"id\":\"$apiTokenId\"}", "-createdAt", 0, 2)?.records?.get(0)
            if (token != null) {
                logger.info("Setting pipeline to run with token: {}", token.displayName)
                Utils.setClient("service-account-" + token.clientId)
            }
        }
    }

    /**
     * Start the event-processing handler.
     *
     * @param handler the event-processing handler to start running
     */
    fun startHandler(handler: AbstractNumaflowHandler) {
        FunctionServer().registerMapHandler(handler).start()
    }
}
