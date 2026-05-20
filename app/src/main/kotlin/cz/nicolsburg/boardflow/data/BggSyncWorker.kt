package cz.nicolsburg.boardflow.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BggSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = SecurePreferences(applicationContext)
        val store = CanonicalCollectionStore.getInstance(applicationContext)
        val repository = BggRepository()
        return refreshBggPlayCache(prefs, store, repository)
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
