package dev.fusion.files

/** FM-03/11. A UI Boolean or experimental consent cannot substitute for S6 evidence. */
enum class FileMutation { CREATE_DIRECTORY, RENAME, DELETE, COPY, TRASH, RESTORE, PURGE, PURGE_ALL }
class FileMutationUnavailable(operation: FileMutation) : UnsupportedOperationException(
    "$operation unavailable: provider/crash journal and safe-publication acceptance S6 has not passed. No mutation attempted."
)
object FileMutationPolicy {
    // No preference, intent extra or caller-provided token can enable legacy destructive code.
    fun <T> denied(operation:FileMutation):Result<T> = Result.failure(FileMutationUnavailable(operation))
}
