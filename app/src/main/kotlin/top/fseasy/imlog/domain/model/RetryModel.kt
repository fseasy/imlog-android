package top.fseasy.imlog.domain.model

/**
 * Used to define the retry logic in data level.
 * */
enum class RetryModel {
    None, // no retry
    OnDbConflict, OnAnyException
}