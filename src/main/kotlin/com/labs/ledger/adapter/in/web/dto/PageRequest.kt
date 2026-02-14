package com.labs.ledger.adapter.`in`.web.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class PageRequest(
    @field:Min(value = 0, message = "Page number must be 0 or greater")
    val page: Int = 0,

    @field:Min(value = 1, message = "Page size must be at least 1")
    @field:Max(value = 100, message = "Page size must not exceed 100")
    val size: Int = 20,

    val sort: String? = null
) {
    val offset: Long
        get() = page.toLong() * size
}
