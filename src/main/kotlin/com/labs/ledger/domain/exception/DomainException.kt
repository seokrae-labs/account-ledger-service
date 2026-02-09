package com.labs.ledger.domain.exception

sealed class DomainException(message: String) : RuntimeException(message)

class AccountNotFoundException(message: String) : DomainException(message)
class InsufficientBalanceException(message: String) : DomainException(message)
class InvalidAccountStatusException(message: String) : DomainException(message)
class InvalidAmountException(message: String) : DomainException(message)
class DuplicateTransferException(message: String) : DomainException(message)
class InvalidTransferStatusTransitionException(message: String) : DomainException(message)
