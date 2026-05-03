package com.flightbooking.services
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow

data class NotificationEvent(
    val message: String,
    val type: String = "info"
)

object NotificationService {
    private val _events = MutableSharedFlow<NotificationEvent>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow()

    suspend fun send(event: NotificationEvent) {
        _events.emit(event)
    }
}