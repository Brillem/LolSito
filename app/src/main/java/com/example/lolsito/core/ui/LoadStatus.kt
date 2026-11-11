package com.example.lolsito.core.ui

/** Status of one screen section that loads remotely. */
sealed interface LoadStatus {
    data object Loading : LoadStatus
    data object Content : LoadStatus
    data object Empty : LoadStatus
    data class Error(val message: String) : LoadStatus
}
