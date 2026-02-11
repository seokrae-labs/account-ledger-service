package com.labs.ledger.adapter.`in`.web.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val error: String,
    val message: String,
    val errors: List<FieldError>? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class FieldError(
    val field: String,
    val message: String,
    val rejectedValue: Any? = null
)
