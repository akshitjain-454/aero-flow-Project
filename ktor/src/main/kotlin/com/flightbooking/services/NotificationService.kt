package com.flightbooking.services
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class NotificationEvent(
    val userId: Int,
    val message: String,
    val type: String = "info",
    val sentAt: Long = System.currentTimeMillis(),
)

object NotificationService {
    private val _events =
        MutableSharedFlow<NotificationEvent>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events = _events.asSharedFlow()

    suspend fun send(event: NotificationEvent) {
        _events.emit(event)
    }
}
