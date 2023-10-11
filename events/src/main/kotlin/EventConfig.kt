/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import config.RuntimeConfig

/**
 * Base class that must be extended for any configuration, to define the expected contents
 * of the configuration.
 */
abstract class EventConfig {
    lateinit var runtime: RuntimeConfig
}
