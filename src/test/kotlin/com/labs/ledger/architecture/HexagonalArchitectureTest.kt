package com.labs.ledger.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Hexagonal Architecture 경계 검증 테스트
 *
 * 검증 규칙:
 * 1. application → infrastructure 의존 금지
 * 2. domain → application/infrastructure/adapter 의존 금지
 * 3. application.service에 Spring 어노테이션(@Service, @Component) 사용 금지
 * 4. application → Spring infrastructure (org.springframework.dao) 의존 금지
 * 5. 전체 레이어 의존성 규칙 검증
 */
class HexagonalArchitectureTest {

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
    fun `application 레이어는 infrastructure 레이어에 의존하지 않아야 함`() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .because("Application 레이어는 infrastructure 레이어에 직접 의존하면 안 됩니다 (Port를 통해 의존해야 함)")
            .check(classes)
    }

    @Test
    fun `domain 레이어는 외부 레이어에 의존하지 않아야 함`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..application..",
                "..infrastructure..",
                "..adapter.."
            )
            .because("Domain 레이어는 순수 비즈니스 로직만 포함하며 외부 레이어에 의존하면 안 됩니다")
            .check(classes)
    }

    @Test
    fun `application service 클래스는 Spring 어노테이션을 사용하지 않아야 함`() {
        classes()
            .that().resideInAPackage("..application.service..")
            .should().notBeAnnotatedWith(org.springframework.stereotype.Service::class.java)
            .andShould().notBeAnnotatedWith(org.springframework.stereotype.Component::class.java)
            .because("Application 서비스는 @Bean으로 등록되어야 하며 @Service/@Component를 사용하면 안 됩니다")
            .check(classes)
    }

    @Test
    fun `application 레이어는 Spring infrastructure 예외에 의존하지 않아야 함`() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.dao..")
            .because("Application 레이어는 Spring infrastructure 예외가 아닌 domain 예외를 사용해야 합니다")
            .check(classes)
    }

    @Test
    fun `Hexagonal Architecture 레이어 의존성 규칙 검증`() {
        val isKotlinOrJavaStandardLib = DescribedPredicate.describe<JavaClass>("Kotlin or Java standard library") { javaClass ->
            javaClass.packageName.startsWith("kotlin.") ||
                javaClass.packageName.startsWith("java.") ||
                javaClass.packageName.startsWith("javax.") ||
                javaClass.packageName.startsWith("kotlinx.") ||
                javaClass.packageName.startsWith("org.jetbrains.annotations.")
        }

        layeredArchitecture()
            .consideringOnlyDependenciesInLayers()  // 레이어 간 의존성만 검증
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Adapter").definedBy("..adapter..")
            .layer("Infrastructure").definedBy("..infrastructure..")

            .whereLayer("Domain").mayNotAccessAnyLayer()
            .whereLayer("Application").mayOnlyAccessLayers("Domain")
            .whereLayer("Adapter").mayOnlyAccessLayers("Application", "Domain", "Infrastructure")
            .whereLayer("Infrastructure").mayOnlyAccessLayers("Application", "Domain")

            .ignoreDependency(
                DescribedPredicate.alwaysTrue(),
                isKotlinOrJavaStandardLib
            )

            .because("Hexagonal Architecture의 레이어 의존성 규칙을 준수해야 합니다")
            .check(classes)
    }
}
