package com.extrairapk.app.data

import com.extrairapk.app.util.ExtractResult

enum class AppFilter {
    ALL,
    USER,
    SYSTEM,
}

enum class SortOrder {
    NAME,
    SIZE,
    RECENT,
}

sealed interface ExtractionState {
    data object Idle : ExtractionState
    data object InProgress : ExtractionState
    data class Done(val result: ExtractResult) : ExtractionState
    data class Error(val message: String) : ExtractionState
}
