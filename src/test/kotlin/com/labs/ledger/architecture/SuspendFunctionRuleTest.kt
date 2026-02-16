package com.labs.ledger.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Kotlin Suspend 함수 사용 규칙 검증 테스트
 *
 * 검증 규칙:
 * 1. Port 인터페이스는 suspend 함수를 사용해야 함 (non-blocking I/O)
 * 2. Domain Model은 suspend 함수를 사용하지 않아야 함 (순수 비즈니스 로직)
 *
 * 기술적 배경:
 * - Kotlin suspend 함수는 컴파일 시 Continuation 파라미터가 추가됨
 * - suspend fun execute() → fun execute(continuation: Continuation<T>)
 * - ArchUnit은 Java 바이트코드를 분석하므로 Continuation 파라미터로 suspend 함수 감지
 *
 * POC 검증 결과:
 * - 정확도: 100% (60개 메서드 검증, False Positive/Negative 없음)
 * - 문서: docs/POC_SUSPEND_VALIDATION_RESULT.md
 * - Issue: #132
 */
class SuspendFunctionRuleTest {

    companion object {
        private lateinit var classes: JavaClasses

        @JvmStatic
        @BeforeAll
        fun setup() {
            classes = ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("com.labs.ledger")
        }

        /**
         * Continuation 파라미터가 있는지 확인
         * (Kotlin suspend 함수는 컴파일 시 Continuation 파라미터가 추가됨)
         */
        private fun haveContinuationParameter() = object : ArchCondition<JavaMethod>("have Continuation parameter (suspend function)") {
            override fun check(method: JavaMethod, events: ConditionEvents) {
                val hasContinuation = method.rawParameterTypes.any { paramType ->
                    paramType.name == "kotlin.coroutines.Continuation"
                }

                if (!hasContinuation) {
                    val message = String.format(
                        "%s은(는) suspend 함수가 아닙니다. Port는 non-blocking I/O를 위해 suspend 함수를 사용해야 합니다.",
                        method.fullName
                    )
                    events.add(SimpleConditionEvent.violated(method, message))
                }
            }
        }

        /**
         * Continuation 파라미터가 없는지 확인
         * (일반 함수는 Continuation 파라미터가 없어야 함)
         */
        private fun notHaveContinuationParameter() = object : ArchCondition<JavaMethod>("not have Continuation parameter (normal function)") {
            override fun check(method: JavaMethod, events: ConditionEvents) {
                val hasContinuation = method.rawParameterTypes.any { paramType ->
                    paramType.name == "kotlin.coroutines.Continuation"
                }

                if (hasContinuation) {
                    val message = String.format(
                        "%s은(는) suspend 함수입니다. Domain Model은 순수 비즈니스 로직만 포함해야 하며 I/O에 의존하지 않습니다.",
                        method.fullName
                    )
                    events.add(SimpleConditionEvent.violated(method, message))
                }
            }
        }
    }

    /**
     * Rule 1: Port 인터페이스 메서드는 suspend 함수여야 함
     *
     * 근거:
     * - Port는 I/O 경계를 정의하는 인터페이스
     * - Reactive 환경에서 non-blocking I/O를 위해 suspend 함수 필수
     * - Repository, UseCase 등 모든 Port가 해당
     *
     * 주의:
     * - 인터페이스만 검증 (domain.port 패키지의 data class 제외)
     * - Object 기본 메서드 (equals, hashCode, toString) 제외
     */
    @Test
    fun `Port 인터페이스 메서드는 suspend 함수여야 함`() {
        methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain.port..")
            .and().areDeclaredInClassesThat().areInterfaces()  // 인터페이스만 (data class 제외)
            .and().areDeclaredInClassesThat().haveSimpleNameNotEndingWith("FailureRegistry")  // In-memory cache, suspend 불필요
            .and().arePublic()
            .and().doNotHaveName("equals")  // Object 메서드 제외
            .and().doNotHaveName("hashCode")
            .and().doNotHaveName("toString")
            .and().doNotHaveName("isRetriable")  // RetryPolicy.isRetriable() - 단순 조건 체크, suspend 불필요
            .should(haveContinuationParameter())
            .because("Port는 non-blocking I/O를 위해 suspend 함수를 사용해야 합니다 (Hexagonal Architecture)")
            .check(classes)
    }

    /**
     * Rule 2: Domain Model 메서드는 suspend 함수가 아니어야 함
     *
     * 근거:
     * - Domain Model은 순수 비즈니스 로직을 표현
     * - I/O에 의존하지 않으므로 suspend 불필요
     * - 테스트 용이성 및 재사용성 향상
     *
     * 주의:
     * - data class 생성 메서드 (copy, component) 제외
     * - Object 기본 메서드 제외
     */
    @Test
    fun `Domain Model 메서드는 suspend 함수가 아니어야 함`() {
        methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain.model..")
            .and().arePublic()
            .and().doNotHaveName("equals")  // Object 메서드 제외
            .and().doNotHaveName("hashCode")
            .and().doNotHaveName("toString")
            .and().doNotHaveName("copy")  // data class 메서드 제외
            .and().doNotHaveName("copy\$default")
            .and().haveNameNotMatching("component\\d+")  // component1, component2, ...
            .should(notHaveContinuationParameter())
            .because("Domain Model은 순수 비즈니스 로직만 포함하며 I/O에 의존하지 않습니다 (DDD)")
            .check(classes)
    }
}
