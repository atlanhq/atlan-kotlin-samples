/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
package com.atlan.pkg.config.model.workflow

import com.atlan.model.workflow.NameValuePair
import com.atlan.pkg.config.model.ui.UIConfig
import com.fasterxml.jackson.annotation.JsonIgnore

class WorkflowInputs(
    @JsonIgnore val config: UIConfig,
) {
    val parameters: List<NameValuePair>
    init {
        val builder = mutableListOf<NameValuePair>()
        config.properties.keys.forEach {
            builder.add(NameValuePair.of(it, ""))
        }
        parameters = builder.toList()
    }
}
