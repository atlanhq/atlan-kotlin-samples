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
import kotlin.math.round

private val log = KotlinLogging.logger {}

private const val GLOSSARY_NAME = "Duplicate assets"

data class AssetKey(val typeName: String, val qualifiedName: String, val guid: String)

private val hashToAssetKeys = ConcurrentHashMap<Int, MutableSet<AssetKey>>()
private val hashToColumns = ConcurrentHashMap<Int, Set<String>>()
private val uniqueContainers = ConcurrentHashMap<AssetKey, AssetKey>()

fun main(args: Array<String>) {
    Utils.setClient()

    val qnPrefix: String
    val types: List<String>
    val batchSize: Int
    if (args.isNotEmpty()) {
        qnPrefix = args[0]
        types = args[1].split(",")
        batchSize = 50
    } else {
        val envVars = System.getenv()
        qnPrefix = envVars.getOrDefault("QN_PREFIX", "default")
        types = envVars.getOrDefault("ASSET_TYPES", "Table,View,MaterialisedView").split(",")
        batchSize = envVars.getOrDefault("BATCH_SIZE", "50").toInt()
    }

    log.info("Detecting duplicates across {} (for prefix {}) on: {}", types, qnPrefix, Atlan.getDefaultClient().baseUrl)
    findAssets(qnPrefix, types, batchSize)
    log.info("Processed a total of {} unique assets.", uniqueContainers.size)

    val glossaryQN = glossaryForDuplicates()
    termsForDuplicates(glossaryQN, batchSize)
}

fun findAssets(qnPrefix: String, types: Collection<String>, batchSize: Int) {
    val startTime = System.currentTimeMillis()
    val request = Atlan.getDefaultClient().assets.select()
        .where(CompoundQuery.assetTypes(types))
        .where(Asset.QUALIFIED_NAME.startsWith(qnPrefix))
        .pageSize(batchSize)
        .includeOnResults(Table.COLUMNS)
        .includeOnRelations(Column.NAME)
    val totalAssetCount = request.count()
    val count = AtomicLong(0)
    log.info("Comparing a total of {} assets...", totalAssetCount)
    request.stream(true)
        .forEach { asset ->
            val columns = when (asset) {
                is Table -> asset.columns
                is View -> asset.columns
                is MaterializedView -> asset.columns
                else -> setOf()
            }
            val localCount = count.getAndIncrement()
            if (localCount.mod(batchSize) == 0) {
                log.info(" ... processed {}/{} ({}%)", localCount, totalAssetCount, round((localCount.toDouble() / totalAssetCount) * 100))
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
        }
    log.info(" ... processed {}/{} ({}%)", totalAssetCount, totalAssetCount, 100)
    log.info(" ... time to calculate: {} ms", System.currentTimeMillis() - startTime)
}

fun glossaryForDuplicates(): String {
    return try {
        Glossary.findByName(GLOSSARY_NAME).qualifiedName
    } catch (e: NotFoundException) {
        val glossary = Glossary.creator(GLOSSARY_NAME)
            .description("Glossary whose terms represent potential duplicate assets.")
            .build()
        log.info("Creating glossary to hold duplicates.")
        glossary.save().getResult(glossary).qualifiedName
    }
}

fun termsForDuplicates(glossaryQN: String, batchSize: Int) {
    val startTime = System.currentTimeMillis()
    val termCount = AtomicLong(0)
    val assetCount = AtomicLong(0)
    val totalSets = hashToAssetKeys.keys
        .stream()
        .filter { hashToAssetKeys[it]?.size!! > 1 }
        .count()
    log.info("Processing {} total sets of duplicates...", totalSets)
    hashToAssetKeys.keys.forEach { hash ->
        val keys = hashToAssetKeys[hash]
        if (keys?.size!! > 1) {
            val columns = hashToColumns[hash]
            val batch = AssetBatch(Atlan.getDefaultClient(), "asset", batchSize, false, AssetBatch.CustomMetadataHandling.MERGE, true)
            termCount.getAndIncrement()
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
                    if (batch.add(
                            asset.trimToRequired()
                                .assignedTerms(existingTerms)
                                .assignedTerm(term)
                                .build(),
                        ) != null
                    ) {
                        log.info(" ... processed {}/{} ({}%)", termCount, totalSets, round((termCount.get().toDouble() / totalSets) * 100))
                    }
                }
            batch.flush()
            log.info(" ... processed {}/{} ({}%)", termCount, totalSets, round((termCount.get().toDouble() / totalSets) * 100))
        }
    }
    log.info("Detected a total of $assetCount assets that could be de-duplicated across $totalSets unique sets of duplicates.")
    log.info(" ... time to consolidate: {} ms", System.currentTimeMillis() - startTime)
}

private fun normalize(colName: String): String {
    return colName.replace("_", "").lowercase()
}
