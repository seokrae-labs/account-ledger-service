package com.labs.ledger.adapter.`in`.web.dto

import java.time.LocalDateTime

data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)
