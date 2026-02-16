package com.labs.ledger.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.RestController

/**
 * Naming Convention 규칙 검증 테스트
 *
 * 검증 규칙:
 * 1. UseCase 인터페이스는 *UseCase로 끝나야 함
 * 2. Service 구현체는 *Service로 끝나야 함
 * 3. Repository 인터페이스는 *Repository로 끝나야 함
 * 4. RestController는 *Controller로 끝나야 함
 * 5. Persistence Entity는 *Entity로 끝나야 함
 * 6. Domain Model은 Entity 접미사를 사용하지 않아야 함
 */
class NamingConventionTest {

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
    fun `UseCase 인터페이스는 UseCase로 끝나야 함`() {
        classes()
            .that().resideInAPackage("..domain.port..")
            .and().areInterfaces()
            .and().haveSimpleNameEndingWith("UseCase")  // UseCase로 끝나는 것만
            .should().haveSimpleNameEndingWith("UseCase")
            .because("UseCase 인터페이스는 일관된 네이밍을 위해 *UseCase로 끝나야 합니다")
            .check(classes)
    }

    @Test
    fun `Service 구현체는 Service로 끝나야 함`() {
        classes()
            .that().resideInAPackage("..application.service..")
            .and().haveSimpleNameEndingWith("Service")  // Service로 끝나는 것만 (내부 클래스 제외)
            .should().haveSimpleNameEndingWith("Service")
            .because("Service 구현체는 일관된 네이밍을 위해 *Service로 끝나야 합니다")
            .check(classes)
    }

    @Test
    fun `Repository 인터페이스는 Repository로 끝나야 함`() {
        classes()
            .that().resideInAPackage("..domain.port..")
            .and().areInterfaces()
            .and().haveSimpleNameEndingWith("Repository")  // Repository로 끝나는 것만
            .should().haveSimpleNameEndingWith("Repository")
            .because("Repository 인터페이스는 일관된 네이밍을 위해 *Repository로 끝나야 합니다")
            .check(classes)
    }

    @Test
    fun `RestController는 Controller로 끝나야 함`() {
        classes()
            .that().areAnnotatedWith(RestController::class.java)
            .should().haveSimpleNameEndingWith("Controller")
            .andShould().resideInAPackage("..adapter.in.web")
            .because("REST Controller는 일관된 네이밍과 위치를 위해 adapter.in.web 패키지에서 *Controller로 끝나야 합니다")
            .check(classes)
    }

    @Test
    fun `Persistence Entity는 Entity로 끝나야 함`() {
        classes()
            .that().resideInAPackage("..adapter.out.persistence.entity..")
            .should().haveSimpleNameEndingWith("Entity")
            .because("Persistence Entity는 Domain Model과 구분하기 위해 *Entity로 끝나야 합니다")
            .check(classes)
    }

    @Test
    fun `Domain Model은 Entity 접미사를 사용하지 않아야 함`() {
        classes()
            .that().resideInAPackage("..domain.model..")
            .should().haveSimpleNameNotEndingWith("Entity")
            .because("Domain Model은 Persistence Entity와 구분하기 위해 Entity 접미사를 사용하지 않아야 합니다")
            .check(classes)
    }
}
