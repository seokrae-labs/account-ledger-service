package com.labs.ledger.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Cyclic Dependency 규칙 검증 테스트
 *
 * 검증 규칙:
 * 1. 패키지 간 순환 의존성이 없어야 함
 *    - 최상위 레이어(adapter/application/domain/infrastructure) 간 순환 참조 차단
 *
 * 주의:
 * - Domain Model 간 순환 의존성 검증은 제외됨 (flat package 구조로 인해 적용 불가)
 * - ArchUnit의 slice 규칙은 서브패키지 기반으로 동작하므로, 단일 패키지 내 클래스 간 순환은 검증하지 않음
 */
class CyclicDependencyTest {

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
    fun `패키지 간 순환 의존성이 없어야 함`() {
        slices()
            .matching("com.labs.ledger.(*)..")
            .should().beFreeOfCycles()
            .because("순환 의존성은 코드 유지보수와 테스트를 어렵게 만듭니다")
            .check(classes)
    }
}
