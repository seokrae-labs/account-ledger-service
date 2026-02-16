package com.labs.ledger.architecture

import com.labs.ledger.domain.exception.DomainException
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Domain Purity 규칙 검증 테스트
 *
 * 검증 규칙:
 * 1. Domain Model은 Persistence 어노테이션을 사용하지 않아야 함
 * 2. Domain은 Reactor 타입을 사용하지 않아야 함
 * 3. Domain Model은 Spring Framework에 의존하지 않아야 함
 * 4. Domain Exception은 DomainException을 상속해야 함
 */
class DomainPurityTest {

    companion object {
        private lateinit var classes: JavaClasses

        @JvmStatic
        @BeforeAll
        fun setup() {
            classes = ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("com.labs.ledger")
        }
    }

    @Test
    fun `Domain Model은 Persistence 어노테이션을 사용하지 않아야 함`() {
        noClasses()
            .that().resideInAPackage("..domain.model..")
            .should().beAnnotatedWith("org.springframework.data.relational.core.mapping.Table")
            .orShould().beAnnotatedWith("jakarta.persistence.Entity")
            .orShould().beAnnotatedWith("jakarta.persistence.Table")
            .orShould().beAnnotatedWith("org.springframework.data.annotation.Id")
            .because("Domain Model은 순수 비즈니스 로직만 포함하며 Persistence 기술에 의존하지 않아야 합니다")
            .check(classes)
    }

    @Test
    fun `Domain은 Reactor 타입을 사용하지 않아야 함`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "reactor.core.publisher..",
                "org.reactivestreams.."
            )
            .because("Domain은 Coroutine을 사용하며 Reactor 타입(Mono/Flux)에 의존하지 않습니다")
            .check(classes)
    }

    @Test
    fun `Domain Model은 Spring Framework에 의존하지 않아야 함`() {
        noClasses()
            .that().resideInAPackage("..domain.model..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.."
            )
            .because("Domain Model은 프레임워크 독립적이어야 하며 Spring에 의존하지 않아야 합니다")
            .check(classes)
    }

    @Test
    fun `Domain Exception은 DomainException을 상속해야 함`() {
        classes()
            .that().resideInAPackage("..domain.exception..")
            .and().areAssignableTo(RuntimeException::class.java)
            .and().doNotHaveSimpleName("DomainException")
            .should().beAssignableTo(DomainException::class.java)
            .because("Domain Exception은 sealed class인 DomainException을 상속하여 컴파일 타임 exhaustive check를 활용해야 합니다")
            .check(classes)
    }
}
