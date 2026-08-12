package app.chompass.services.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.response.ReadRecordResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import kotlin.reflect.KClass

/**
 * Test double for [HealthConnectClient] that records what the writer pushed and
 * serves scripted pages to the reader. Unused directions throw, so a test that
 * accidentally touches the wrong surface fails loudly instead of silently
 * passing against a permissive stub.
 */
internal class HealthConnectFakeClient : HealthConnectClient {
    /** Records passed to [insertRecords], in call order. */
    val inserted: MutableList<Record> = mutableListOf()

    /** (recordType, clientRecordIdsList) captured from [deleteRecords] calls. */
    val deleted: MutableList<Pair<KClass<out Record>, List<String>>> = mutableListOf()

    /** Requests passed to [readRecords], in call order (pageToken progression). */
    val readRequests: MutableList<ReadRecordsRequest<*>> = mutableListOf()

    /** Scripted readRecords pages; each entry is consumed by one call. */
    val readPages: MutableList<Pair<List<Record>, String?>> = mutableListOf()

    /** When set, [insertRecords] throws it (binder failure simulation). */
    var insertError: Exception? = null

    /** When set, [deleteRecords] throws it (binder failure simulation). */
    var deleteError: Exception? = null

    /** When set, [readRecords] throws it before consuming a page. */
    var readError: Exception? = null

    override val permissionController: PermissionController
        get() = error("not used in these tests")

    override suspend fun insertRecords(records: List<Record>): InsertRecordsResponse {
        insertError?.let { throw it }
        inserted.addAll(records)
        return InsertRecordsResponse(emptyList())
    }

    override suspend fun updateRecords(records: List<Record>) {
        error("not used in these tests")
    }

    override suspend fun deleteRecords(
        recordType: KClass<out Record>,
        recordIdsList: List<String>,
        clientRecordIdsList: List<String>,
    ) {
        deleteError?.let { throw it }
        deleted.add(recordType to clientRecordIdsList)
    }

    override suspend fun deleteRecords(
        recordType: KClass<out Record>,
        timeRangeFilter: TimeRangeFilter,
    ) {
        error("not used in these tests")
    }

    override suspend fun <T : Record> readRecord(recordType: KClass<T>, recordId: String): ReadRecordResponse<T> {
        error("not used in these tests")
    }

    override suspend fun <T : Record> readRecords(request: ReadRecordsRequest<T>): ReadRecordsResponse<T> {
        readRequests.add(request)
        readError?.let { throw it }
        // An unscripted call models a mid-pagination failure: the reader must
        // return null (leaving its one-shot restore flag unset) instead of
        // trusting a partial history.
        val (records, pageToken) = readPages.removeFirstOrNull()
            ?: throw IllegalStateException("unexpected readRecords call")
        @Suppress("UNCHECKED_CAST")
        return ReadRecordsResponse(records as List<T>, pageToken)
    }

    override suspend fun aggregate(request: AggregateRequest): AggregationResult {
        error("not used in these tests")
    }

    override suspend fun aggregateGroupByDuration(
        request: AggregateGroupByDurationRequest,
    ): List<AggregationResultGroupedByDuration> {
        error("not used in these tests")
    }

    override suspend fun aggregateGroupByPeriod(
        request: AggregateGroupByPeriodRequest,
    ): List<AggregationResultGroupedByPeriod> {
        error("not used in these tests")
    }

    override suspend fun getChangesToken(request: ChangesTokenRequest): String {
        error("not used in these tests")
    }

    override suspend fun getChanges(changesToken: String): ChangesResponse {
        error("not used in these tests")
    }
}
