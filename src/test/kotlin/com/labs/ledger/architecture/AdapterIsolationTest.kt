package com.labs.ledger.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Adapter Isolation 규칙 검증 테스트
 *
 * 검증 규칙:
 * 1. Input Adapter와 Output Adapter 간 직접 의존 금지
 * 2. Persistence Entity는 Domain Model을 상속하지 않아야 함
 */
class AdapterIsolationTest {

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
    fun `Input Adapter는 Output Adapter에 직접 의존하지 않아야 함`() {
        noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..")
            .because("Adapter 간 직접 의존은 금지되며 Application 레이어를 통해서만 통신해야 합니다")
            .check(classes)
    }

    @Test
    fun `Output Adapter는 Input Adapter에 직접 의존하지 않아야 함`() {
        noClasses()
            .that().resideInAPackage("..adapter.out..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.in..")
            .because("Adapter 간 직접 의존은 금지되며 Application 레이어를 통해서만 통신해야 합니다")
            .check(classes)
    }

    @Test
    fun `Persistence Entity는 Domain Model을 상속하지 않아야 함`() {
        noClasses()
            .that().resideInAPackage("..adapter.out.persistence.entity..")
            .should().dependOnClassesThat().resideInAPackage("..domain.model..")
            .because("Entity와 Model은 별도로 관리되어야 하며 Adapter에서 변환 로직을 제공해야 합니다")
            .check(classes)
    }
}
