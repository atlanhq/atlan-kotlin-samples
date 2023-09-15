/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.Atlan
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

object Utils {

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

    fun getEnvVar(name: String, default: String): String {
        val candidate = System.getenv(name)
        return if (candidate != null && candidate.isNotEmpty()) candidate else default
    }
}
