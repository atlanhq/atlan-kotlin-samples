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

private val log = KotlinLogging.logger {}

private const val GLOSSARY_NAME = "Duplicate assets"

data class AssetKey(val typeName: String, val qualifiedName: String, val guid: String)

private val hashToAssetKeys = ConcurrentHashMap<Int, MutableSet<AssetKey>>()
private val hashToColumns = ConcurrentHashMap<Int, Set<String>>()
private val uniqueContainers = ConcurrentHashMap<AssetKey, AssetKey>()

fun main(args: Array<String>) {
    Atlan.setBaseUrl(System.getenv("ATLAN_BASE_URL"))
    Atlan.setApiToken(System.getenv("ATLAN_API_KEY"))

    val qnPrefix = args[0]
    val types = args[1].split(",")

    log.info("Detecting duplicates across {} (for prefix {}) on: {}", types, qnPrefix, Atlan.getDefaultClient().baseUrl)
    findAssets(qnPrefix, types)
    log.info("Processed a total of {} unique assets.", uniqueContainers.size)

    val glossaryQN = glossaryForDuplicates()
    termsForDuplicates(glossaryQN)
}

fun findAssets(qnPrefix: String, types: Collection<String>) {
    val startTime = System.currentTimeMillis()
    Atlan.getDefaultClient().assets.select()
        .where(CompoundQuery.assetTypes(types))
        .where(Table.QUALIFIED_NAME.startsWith(qnPrefix))
        .pageSize(50)
        .includeOnResults(Table.COLUMNS)
        .includeOnRelations(Column.NAME)
        .stream(true)
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
        }
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

fun termsForDuplicates(glossaryQN: String) {
    val startTime = System.currentTimeMillis()
    val totalTerms = AtomicLong(0)
    val totalAssets = AtomicLong(0)
    hashToAssetKeys.keys.forEach { hash ->
        val keys = hashToAssetKeys[hash]
        if (keys?.size!! > 1) {
            val columns = hashToColumns[hash]
            val batch = AssetBatch(Atlan.getDefaultClient(), "asset", 50, false, AssetBatch.CustomMetadataHandling.MERGE, true)
            totalTerms.getAndIncrement()
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
                .pageSize(50)
                .stream(true)
                .forEach { asset ->
                    totalAssets.getAndIncrement()
                    val existingTerms = asset.assignedTerms
                    batch.add(
                        asset.trimToRequired()
                            .assignedTerms(existingTerms)
                            .assignedTerm(term)
                            .build(),
                    )
                }
            batch.flush()
        }
    }
    log.info("Detected a total of $totalTerms potentially duplicated assets.")
    log.info("Detected a total of $totalAssets assets that could be de-duplicated.")
    log.info(" ... time to consolidate: {} ms", System.currentTimeMillis() - startTime)
}

private fun normalize(colName: String): String {
    return colName.replace("_", "").lowercase()
}
