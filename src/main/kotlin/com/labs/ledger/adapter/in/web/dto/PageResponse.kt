package com.labs.ledger.adapter.`in`.web.dto

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
) {
    companion object {
        fun <T> of(
            content: List<T>,
            page: Int,
            size: Int,
            totalElements: Long
        ): PageResponse<T> {
            val totalPages = if (totalElements == 0L) 0 else ((totalElements - 1) / size + 1).toInt()
            val hasNext = page < totalPages - 1
            val hasPrevious = page > 0

            return PageResponse(
                content = content,
                page = page,
                size = size,
                totalElements = totalElements,
                totalPages = totalPages,
                hasNext = hasNext,
                hasPrevious = hasPrevious
            )
        }
    }
}
