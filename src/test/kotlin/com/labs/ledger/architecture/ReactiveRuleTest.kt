package com.labs.ledger.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Reactive 규칙 검증 테스트
 *
 * 검증 규칙:
 * 1. Adapter는 Blocking I/O 라이브러리를 사용하지 않아야 함
 */
class ReactiveRuleTest {

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
    fun `Adapter는 Blocking IO 라이브러리를 사용하지 않아야 함`() {
        noClasses()
            .that().resideInAPackage("..adapter.out.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework.jdbc..",
                "java.sql.."
            )
            .because("Reactive 환경에서는 R2DBC를 사용해야 하며 Blocking I/O(JDBC)를 사용하면 안 됩니다")
            .check(classes)
    }
}
