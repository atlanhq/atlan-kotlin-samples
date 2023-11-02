/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.Atlan
import com.atlan.exception.NotFoundException
import com.atlan.model.assets.Asset
import com.atlan.model.assets.Column
import com.atlan.model.assets.Glossary
import com.atlan.model.assets.GlossaryTerm
import com.atlan.model.assets.IColumn
import com.atlan.model.assets.MaterializedView
import com.atlan.model.assets.Table
import com.atlan.model.assets.View
import com.atlan.model.enums.CertificateStatus
import com.atlan.model.search.CompoundQuery
import com.atlan.util.AssetBatch
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

private const val GLOSSARY_NAME = "Duplicate assets"

data class AssetKey(val typeName: String, val qualifiedName: String, val guid: String)

private val hashToAssetKeys = ConcurrentHashMap<Int, MutableSet<AssetKey>>()
private val hashToColumns = ConcurrentHashMap<Int, Set<String>>()
private val uniqueContainers = ConcurrentHashMap<AssetKey, AssetKey>()

fun main() {
    Utils.setClient()
    Utils.setWorkflowOpts()

    val qnPrefix = Utils.getEnvVar("QN_PREFIX", "default")
    val types = MultiSelectDeserializer.deserialize(Utils.getEnvVar("ASSET_TYPES", "[\"Table\",\"View\",\"MaterialisedView\"]"))
    val batchSize = Utils.getEnvVar("BATCH_SIZE", "50").toInt()

    logger.info("Detecting duplicates across {} (for prefix {}) on: {}", types, qnPrefix, Atlan.getDefaultClient().baseUrl)
    findAssets(qnPrefix, types, batchSize)

    val glossaryQN = glossaryForDuplicates()
    termsForDuplicates(glossaryQN, batchSize)
}

/**
 * Find the assets to compare with each other for deduplication.
 *
 * @param qnPrefix a partial qualifiedName that every matching asset must start with
 * @param types collection of asset types to include
 * @param batchSize: maximum number of assets to look for per API request (page of results)
 */
fun findAssets(qnPrefix: String, types: Collection<String>, batchSize: Int) {
    val request = Atlan.getDefaultClient().assets.select()
        .where(CompoundQuery.assetTypes(types))
        .where(Asset.QUALIFIED_NAME.startsWith(qnPrefix))
        .pageSize(batchSize)
        .includeOnResults(Table.COLUMNS)
        .includeOnRelations(Column.NAME)
    val totalAssetCount = request.count()
    val count = AtomicLong(0)
    logger.info("Comparing a total of {} assets...", totalAssetCount)
    request.stream(true)
        .forEach { asset ->
            val columns = when (asset) {
                is Table -> asset.columns
                is View -> asset.columns
                is MaterializedView -> asset.columns
                else -> setOf()
            }
            val columnNames = columns.stream()
                .map(IColumn::getName)
                .map { normalize(it) }
                .toList()
                .toSet()
            val containerKey = AssetKey(asset.typeName, asset.qualifiedName, asset.guid)
            if (uniqueContainers.put(containerKey, containerKey) == null) {
                val hash = columnNames.hashCode()
                if (!hashToAssetKeys.containsKey(hash)) {
                    hashToColumns[hash] = columnNames
                    hashToAssetKeys[hash] = mutableSetOf()
                }
                hashToAssetKeys[hash]?.add(containerKey)
            }
            Utils.logProgress(count, totalAssetCount, logger, batchSize)
        }
}

/**
 * Idempotently create (or fetch) a glossary to capture the duplicate assets.
 *
 * @return the qualifiedName of the glossary
 */
fun glossaryForDuplicates(): String {
    return try {
        Glossary.findByName(GLOSSARY_NAME).qualifiedName
    } catch (e: NotFoundException) {
        val glossary = Glossary.creator(GLOSSARY_NAME)
            .description("Glossary whose terms represent potential duplicate assets.")
            .build()
        logger.info("Creating glossary to hold duplicates.")
        glossary.save().getResult(glossary).qualifiedName
    }
}

/**
 * Idempotently create (or update) a term for each set of 2 or more potential duplicate assets,
 * and link those potential duplicate assets to the term.
 *
 * @param glossaryQN qualifiedName of the glossary in which to manage the terms
 * @param batchSize maximum number of assets to update at a time
 */
fun termsForDuplicates(glossaryQN: String, batchSize: Int) {
    val termCount = AtomicLong(0)
    val assetCount = AtomicLong(0)
    val totalSets = hashToAssetKeys.keys
        .stream()
        .filter { hashToAssetKeys[it]?.size!! > 1 }
        .count()
    logger.info("Processing {} total sets of duplicates...", totalSets)
    hashToAssetKeys.keys.forEach { hash ->
        val keys = hashToAssetKeys[hash]
        if (keys?.size!! > 1) {
            val columns = hashToColumns[hash]
            val batch = AssetBatch(Atlan.getDefaultClient(), batchSize, false, AssetBatch.CustomMetadataHandling.MERGE, true)
            val termName = "Dup. ($hash)"
            val term = try {
                GlossaryTerm.findByNameFast(termName, glossaryQN)
            } catch (e: NotFoundException) {
                val toCreate = GlossaryTerm.creator(termName, glossaryQN)
                    .description("Assets with the same set of  ${columns?.size} columns:\n" + columns?.joinToString(separator = "\n") { "- $it" })
                    .certificateStatus(CertificateStatus.DRAFT)
                    .build()
                toCreate.save().getResult(toCreate)
            }
            val guids = keys.stream()
                .map(AssetKey::guid)
                .toList()
            Atlan.getDefaultClient().assets.select()
                .where(Asset.GUID.`in`(guids))
                .includeOnResults(Asset.ASSIGNED_TERMS)
                .includeOnRelations(Asset.QUALIFIED_NAME)
                .pageSize(batchSize)
                .stream(true)
                .forEach { asset ->
                    assetCount.getAndIncrement()
                    val existingTerms = asset.assignedTerms
                    batch.add(
                        asset.trimToRequired()
                            .assignedTerms(existingTerms)
                            .assignedTerm(term)
                            .build(),
                    )
                }
            batch.flush()
            Utils.logProgress(termCount, totalSets, logger)
        }
    }
    logger.info("Detected a total of $assetCount assets that could be de-duplicated across $totalSets unique sets of duplicates.")
}

/**
 * Normalize the provided column name for comparison purposes.
 * At the moment this:
 * 1. Removes any underscores (_)
 * 2. Makes the entire column name case-insensitive
 *
 * @param colName original name of the column
 * @return the normalized name of the column
 */
private fun normalize(colName: String): String {
    return colName.replace("_", "").lowercase()
}
