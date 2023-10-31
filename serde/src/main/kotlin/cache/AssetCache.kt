/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
package cache

import com.atlan.model.assets.Asset

/**
 * Utility class for lazy-loading a cache of assets based on some human-constructable identity.
 */
abstract class AssetCache {
    private val cache: MutableMap<String, Asset?> = mutableMapOf()

    /**
     * Retrieve an asset from the cache, lazily-loading it on any cache misses.
     *
     * @param identity of the asset to retrieve
     * @return the asset with the specified identity
     */
    operator fun get(identity: String): Asset? {
        if (!this.containsKey(identity)) {
            this[identity] = lookupAsset(identity)
        }
        return cache[identity]
    }

    /**
     * Add an asset to the cache.
     *
     * @param identity of the asset to add to the cache
     * @param asset the asset to add to the cache
     */
    operator fun set(identity: String, asset: Asset?) {
        cache[identity] = asset
    }

    /**
     * Indicates whether the cache already contains an asset with a given identity.
     *
     * @param identity of the asset to check for presence in the cache
     * @return true if this identity is already in the cache, false otherwise
     */
    fun containsKey(identity: String): Boolean {
        return cache.containsKey(identity)
    }

    /**
     * Actually go to Atlan and find the asset with the provided identity.
     *
     * @param identity of the asset to lookup
     * @return the asset, from Atlan itself
     */
    protected abstract fun lookupAsset(identity: String?): Asset?
}
