/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.Atlan
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

object Utils {

    fun setClient() {
        val baseUrl = System.getenv("ATLAN_BASE_URL")
        val apiToken = System.getenv("ATLAN_API_KEY")
        Atlan.setBaseUrl(baseUrl)
        if (apiToken == null) {
            val userId = System.getenv("ATLAN_USER_ID")
            log.info("No API token found, attempting to impersonate user: {}", userId)
            val defClient = Atlan.getDefaultClient()
            val userToken = defClient.impersonate.user(userId)
            Atlan.setApiToken(userToken)
        } else {
            Atlan.setApiToken(System.getenv("ATLAN_API_KEY"))
        }
    }
}
