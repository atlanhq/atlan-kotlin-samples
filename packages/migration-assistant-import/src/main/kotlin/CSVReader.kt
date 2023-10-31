/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright 2023 Atlan Pte. Ltd. */
import com.atlan.Atlan
import com.atlan.cache.ReflectionCache
import com.atlan.exception.AtlanException
import com.atlan.model.assets.Asset
import com.atlan.model.enums.AtlanDeleteType
import com.atlan.model.fields.AtlanField
import com.atlan.util.AssetBatch
import de.siegmar.fastcsv.reader.CsvReader
import de.siegmar.fastcsv.reader.CsvRow
import mu.KLogger
import java.io.Closeable
import java.io.IOException
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Utility class for reading from CSV files, using FastCSV.
 *
 * @param path location and filename of the CSV file to read
 * @param fieldSeparator character to use to separate fields (for example ',' or ';')
 */
class CSVReader @JvmOverloads constructor(path: String, fieldSeparator: Char = ',') : Closeable {

    private val reader: CsvReader
    private val header: List<String>
    private val totalRowCount: Long
    private val typeIdx: Int
    private val qualifiedNameIdx: Int

    init {
        val inputFile = Paths.get(path)
        val builder = CsvReader.builder()
            .fieldSeparator(fieldSeparator)
            .quoteCharacter('"')
            .skipEmptyRows(true)
            .errorOnDifferentFieldCount(true)
        builder.build(inputFile).use { tmp -> totalRowCount = tmp.stream().parallel().count() - 1 }
        builder.build(inputFile).use { tmp ->
            val one = tmp.stream().findFirst()
            header =
                one.map { obj: CsvRow -> obj.fields }
                    .orElse(emptyList())
        }
        typeIdx = header.indexOf(Asset.TYPE_NAME.atlanFieldName)
        qualifiedNameIdx = header.indexOf(Asset.QUALIFIED_NAME.atlanFieldName)
        if (typeIdx < 0 || qualifiedNameIdx < 0) {
            throw IOException(
                "Unable to find either (or both) the columns 'typeName' and / or 'qualifiedName'. These are both mandatory columns in the input CSV.",
            )
        }
        reader = builder.build(inputFile)
    }

    /**
     * Parallel-read the CSV file into batched asset updates against Atlan.
     * Note: this requires the input CSV file to be fully parallel-loadable without any
     * conflicts. That means: every row is a unique asset, no two rows update any relationship
     * attribute that points at the same related asset (such as an assigned term).
     *
     * @param rowToAsset translator from a row of CSV values to an asset object
     * @param batchSize maximum number of Assets to bulk-save in Atlan per API request
     * @param logger through which to report the overall progress
     */
    fun streamRows(rowToAsset: AssetGenerator, batchSize: Int, logger: KLogger) {
        // Note that for proper parallelism we need to manage a separate AssetBatch per thread
        val batchMap: MutableMap<Long, AssetBatch> = ConcurrentHashMap()
        val relatedMap: MutableMap<Long, AssetBatch> = ConcurrentHashMap()
        val relatedHolds: MutableMap<Long, MutableMap<String, RelatedAssetHold>> = ConcurrentHashMap()
        val deferDeletes: MutableMap<Long, MutableMap<String, Set<AtlanField>>> = ConcurrentHashMap()

        // Step 1: load the main assets
        logger.info("Loading a total of {} assets...", totalRowCount)
        val count = AtomicLong(0)
        reader.stream().skip(1).parallel().forEach { r: CsvRow ->
            val id = Thread.currentThread().id
            if (!batchMap.containsKey(id)) {
                // Initialize a new AssetBatch for each parallel thread
                batchMap[id] = AssetBatch(
                    Atlan.getDefaultClient(),
                    batchSize,
                    true,
                    AssetBatch.CustomMetadataHandling.MERGE,
                    true,
                )
                relatedHolds[id] = ConcurrentHashMap()
                deferDeletes[id] = ConcurrentHashMap()
                relatedMap[id] = AssetBatch(
                    Atlan.getDefaultClient(),
                    batchSize,
                    true,
                    AssetBatch.CustomMetadataHandling.MERGE,
                    true,
                )
            }
            val localBatch = batchMap[id]
            val assets = rowToAsset.buildFromRow(r.fields, header, typeIdx, qualifiedNameIdx)
            if (assets != null) {
                try {
                    val asset = assets.primary.build()
                    localBatch!!.add(asset)
                    Utils.logProgress(count, totalRowCount, logger, batchSize)
                    if (assets.related.isNotEmpty()) {
                        relatedHolds[id]!![asset.guid] = RelatedAssetHold(asset, assets.related)
                    }
                    if (assets.delete.isNotEmpty()) {
                        deferDeletes[id]!![asset.guid] = assets.delete
                    }
                    // TODO: Handle non-idempotent Atlan tags
                } catch (e: AtlanException) {
                    logger.error("Unable to load batch.", e)
                }
            }
        }

        // Step 2: load the deferred related assets (and final-flush the main asset batches, too)
        val totalCreates = AtomicLong(0)
        val totalUpdates = AtomicLong(0)
        val totalFailures = AtomicLong(0)
        val totalRelated = AtomicLong(0)
        val searchAndDelete = mutableMapOf<String, Set<AtlanField>>()
        relatedHolds.values.forEach { a -> a.values.forEach { b -> totalRelated.getAndAdd(b.relatedMap.size.toLong()) } }
        logger.info("Processing {} total related assets in a second pass.", totalRelated)
        batchMap.entries.parallelStream().forEach { entry: MutableMap.MutableEntry<Long, AssetBatch> ->
            val threadId = entry.key
            val batch = entry.value
            val relatedBatch = relatedMap[threadId]
            batch.flush()
            totalCreates.getAndAdd(batch.created.size.toLong())
            totalUpdates.getAndAdd(batch.updated.size.toLong())
            logFailures(batch, logger, totalFailures)
            for (hold in relatedHolds[threadId]!!) {
                val placeholderGuid = hold.key
                val relatedAssetHold = hold.value
                val resolvedGuid = batch.resolvedGuids[placeholderGuid]
                val resolvedAsset = relatedAssetHold.fromAsset.toBuilder().guid(resolvedGuid).build() as Asset
                for (related in relatedAssetHold.relatedMap.values) {
                    val resolvedRelated = rowToAsset.buildRelated(resolvedAsset, related)
                    logger.info("Loading related for asset {}: {}", resolvedAsset.guid, resolvedRelated.toJson(Atlan.getDefaultClient()))
                    relatedBatch!!.add(resolvedRelated)
                    Utils.logProgress(count, totalRelated.get(), logger, batchSize)
                }
            }
            for (delete in deferDeletes[threadId]!!) {
                val placeholderGuid = delete.key
                val resolvedGuid = batch.resolvedGuids[placeholderGuid]!!
                searchAndDelete[resolvedGuid] = delete.value
            }
        }
        logger.info("Total assets created: {}", totalCreates)
        logger.info("Total assets updated: {}", totalUpdates)
        logger.info("Total assets failed : {}", totalFailures)

        // Step 3: final-flush the deferred related assets
        val totalCreatesR = AtomicLong(0)
        val totalUpdatesR = AtomicLong(0)
        val totalFailuresR = AtomicLong(0)
        relatedMap.values.parallelStream().forEach { b ->
            b.flush()
            totalCreatesR.getAndAdd(b.created.size.toLong())
            totalUpdatesR.getAndAdd(b.updated.size.toLong())
            logFailures(b, logger, totalFailuresR)
        }
        logger.info("Total related assets created: {}", totalCreatesR)
        logger.info("Total related assets updated: {}", totalUpdatesR)
        logger.info("Total related assets failed : {}", totalFailuresR)

        // Step 4: bulk-delete any related assets marked for removal
        val totalToScan = searchAndDelete.size.toLong()
        val totalScanned = AtomicLong(0)
        val totalDeleted = AtomicLong(0)
        logger.info("Scanning {} total assets in a final pass for possible README removal.", totalToScan)
        searchAndDelete.entries.parallelStream().forEach { entry ->
            val guid = entry.key
            val fields = entry.value
            Atlan.getDefaultClient().assets.select()
                .where(Asset.GUID.eq(guid))
                .includesOnResults(fields)
                .stream()
                .forEach { result ->
                    val guids = mutableListOf<String>()
                    for (field in fields) {
                        val getter = ReflectionCache.getGetter(result.javaClass, field.atlanFieldName)
                        val reference = getter.invoke(result)
                        if (reference is Asset) {
                            guids.add(reference.guid)
                        } else if (reference != null && Collection::class.java.isAssignableFrom(reference.javaClass)) {
                            for (element in reference as Collection<*>) {
                                if (element is Asset) {
                                    guids.add(element.guid)
                                }
                            }
                        }
                    }
                    if (guids.isNotEmpty()) {
                        val response = Atlan.getDefaultClient().assets.delete(guids, AtlanDeleteType.SOFT)
                        totalDeleted.getAndAdd(response.deletedAssets.size.toLong())
                    }
                }
            Utils.logProgress(totalScanned, totalToScan, logger, batchSize)
        }
        logger.info("Total READMEs deleted: {}", totalDeleted)
    }

    private fun logFailures(b: AssetBatch, logger: KLogger, totalFailures: AtomicLong) {
        if (b.failures.isNotEmpty()) {
            for (f in b.failures) {
                logger.info("Failed batch reason:", f.failureReason)
                totalFailures.getAndAdd(f.failedAssets.size.toLong())
                for (failed in f.failedAssets) {
                    logger.info(
                        " ... included asset: {}::{}",
                        failed.typeName,
                        failed.qualifiedName,
                    )
                }
            }
        }
    }

    /** {@inheritDoc}  */
    @Throws(IOException::class)
    override fun close() {
        reader.close()
    }

    data class RelatedAssetHold(
        val fromAsset: Asset,
        val relatedMap: Map<String, Asset>,
    )
}
