package cz.nicolsburg.boardflow.data

import cz.nicolsburg.boardflow.model.LoggedPlay

suspend fun refreshBggPlayCache(
    prefs: SecurePreferences,
    store: CanonicalCollectionStore,
    repository: BggRepository
): Result<List<LoggedPlay>> {
    val username = prefs.bggUsername.trim()
    if (username.isBlank()) {
        return Result.failure(IllegalStateException("Please set your BGG username in Settings first"))
    }

    prefs.getCredentials()?.let { credentials ->
        repository.login(credentials).getOrThrow()
    }

    val plays = repository.getPlays(username).getOrThrow()
    store.saveBggPlaysCache(plays)
    return Result.success(plays)
}
