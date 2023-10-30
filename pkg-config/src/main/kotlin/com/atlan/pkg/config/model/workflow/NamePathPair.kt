/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
package com.atlan.pkg.config.model.workflow

import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("name", "path")
data class NamePathPair(
    val name: String,
    val path: String,
) : NamedPair(name)
