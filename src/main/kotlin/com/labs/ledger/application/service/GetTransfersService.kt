package com.labs.ledger.application.service

import com.labs.ledger.application.port.`in`.GetTransfersUseCase
import com.labs.ledger.application.port.`in`.TransfersPage
import com.labs.ledger.domain.port.TransferRepository
import org.springframework.stereotype.Service

@Service
class GetTransfersService(
    private val transferRepository: TransferRepository
) : GetTransfersUseCase {

    override suspend fun execute(page: Int, size: Int): TransfersPage {
        val offset = page.toLong() * size
        val transfers = transferRepository.findAll(offset, size)
        val totalElements = transferRepository.count()

        return TransfersPage(
            transfers = transfers,
            totalElements = totalElements,
            page = page,
            size = size
        )
    }
}
