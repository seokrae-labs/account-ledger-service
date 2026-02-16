package com.labs.ledger.architecture

import com.tngtech.archunit.core.domain.JavaClass
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
 * Kotlin Suspend 함수 검증 POC (Proof of Concept)
 *
 * 목적:
 * - Kotlin suspend 함수를 ArchUnit으로 검증할 수 있는지 기술 검증
 * - Continuation 파라미터 기반 감지의 정확도 측정
 * - False Positive/Negative 비율 확인
 *
 * 배경:
 * - Kotlin의 suspend 함수는 컴파일 시 마지막 파라미터로 Continuation이 추가됨
 * - suspend fun execute() → fun execute(continuation: Continuation<T>)
 * - ArchUnit은 Java 바이트코드를 분석하므로 이를 활용
 *
 * Issue: #132
 */
class SuspendFunctionValidationPOC {

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
         * Continuation 파라미터가 있는지 확인하는 조건
         * (Kotlin suspend 함수는 컴파일 시 Continuation 파라미터가 추가됨)
         */
        private fun haveContinuationParameter() = object : ArchCondition<JavaMethod>("have Continuation parameter") {
            override fun check(method: JavaMethod, events: ConditionEvents) {
                val hasContinuation = method.rawParameterTypes.any { paramType ->
                    paramType.name == "kotlin.coroutines.Continuation"
                }

                if (!hasContinuation) {
                    val message = "${method.fullName} does not have Continuation parameter (not a suspend function)"
                    events.add(SimpleConditionEvent.violated(method, message))
                }
            }
        }

        /**
         * Continuation 파라미터가 없는지 확인하는 조건
         * (일반 함수는 Continuation 파라미터가 없어야 함)
         */
        private fun notHaveContinuationParameter() = object : ArchCondition<JavaMethod>("not have Continuation parameter") {
            override fun check(method: JavaMethod, events: ConditionEvents) {
                val hasContinuation = method.rawParameterTypes.any { paramType ->
                    paramType.name == "kotlin.coroutines.Continuation"
                }

                if (hasContinuation) {
                    val message = "${method.fullName} has Continuation parameter (is a suspend function)"
                    events.add(SimpleConditionEvent.violated(method, message))
                }
            }
        }
    }

    /**
     * POC 1: Port 인터페이스 메서드는 suspend 함수여야 함
     *
     * 예상 결과: ✅ PASS
     * - AccountRepository, LedgerEntryRepository, TransferRepository 등
     * - 모든 메서드가 suspend 함수 (Continuation 파라미터 있음)
     *
     * 주의: Port는 인터페이스만 해당 (data class 제외)
     */
    @Test
    fun `POC - Port 인터페이스 메서드는 suspend 함수여야 함`() {
        methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain.port..")
            .and().areDeclaredInClassesThat().areInterfaces()  // 인터페이스만 (data class 제외)
            .and().arePublic()
            .and().doNotHaveName("equals")  // Object 메서드 제외
            .and().doNotHaveName("hashCode")
            .and().doNotHaveName("toString")
            .and().doNotHaveName("isRetriable")  // RetryPolicy.isRetriable() - 단순 조건 체크
            .should(haveContinuationParameter())
            .because("Port는 non-blocking I/O를 위해 suspend 함수를 사용해야 합니다")
            .check(classes)
    }

    /**
     * POC 2: Domain Model 메서드는 suspend 함수가 아니어야 함
     *
     * 예상 결과: ✅ PASS
     * - Account, Transfer, LedgerEntry 등
     * - 모든 비즈니스 로직 메서드가 순수 함수 (Continuation 없음)
     */
    @Test
    fun `POC - Domain Model 메서드는 suspend 함수가 아니어야 함`() {
        methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain.model..")
            .and().arePublic()
            .and().doNotHaveName("equals")  // Object/Data class 메서드 제외
            .and().doNotHaveName("hashCode")
            .and().doNotHaveName("toString")
            .and().doNotHaveName("copy")
            .and().doNotHaveName("copy\$default")
            .and().haveNameNotMatching("component\\d+")  // component1, component2, ...
            .should(notHaveContinuationParameter())
            .because("Domain Model은 순수 비즈니스 로직만 포함하며 I/O에 의존하지 않습니다")
            .check(classes)
    }

    /**
     * POC 3: 상세 분석 - 각 클래스별 suspend 함수 검출 현황
     *
     * 목적: False Positive/Negative 측정
     * - Port 패키지: Continuation 있는 메서드 개수
     * - Domain Model 패키지: Continuation 없는 메서드 개수
     */
    @Test
    fun `POC - 상세 분석 - suspend 함수 검출 현황`() {
        println("\n=== Kotlin Suspend Function Detection POC ===\n")

        // Port 분석
        val portClasses = classes
            .filter { it.packageName.contains("domain.port") }
            .filter { it.isInterface }

        println("### Port Interfaces (should be suspend functions)")
        portClasses.forEach { portClass: JavaClass ->
            analyzeMethods(portClass, expectSuspend = true)
        }

        // Domain Model 분석
        val modelClasses = classes
            .filter { it.packageName.contains("domain.model") }
            .filter { !it.isInterface }

        println("\n### Domain Models (should NOT be suspend functions)")
        modelClasses.forEach { modelClass: JavaClass ->
            analyzeMethods(modelClass, expectSuspend = false)
        }

        println("\n=== Analysis Complete ===\n")
    }

    /**
     * 클래스의 메서드들을 분석하여 suspend 함수 여부 출력
     */
    private fun analyzeMethods(javaClass: JavaClass, expectSuspend: Boolean) {
        val methods = javaClass.methods
            .filter { it.modifiers.contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC) }
            .filter { !it.name.matches(Regex("equals|hashCode|toString|copy|copy\\\$default|component\\d+")) }

        if (methods.isEmpty()) return

        println("\n📦 ${javaClass.simpleName}")

        var correctCount = 0
        var incorrectCount = 0

        methods.forEach { method ->
            val hasContinuation = method.rawParameterTypes.any {
                it.name == "kotlin.coroutines.Continuation"
            }

            val isCorrect = (expectSuspend && hasContinuation) || (!expectSuspend && !hasContinuation)

            if (isCorrect) {
                correctCount++
                println("   ✅ ${method.name} - ${if (hasContinuation) "suspend" else "normal"}")
            } else {
                incorrectCount++
                println("   ❌ ${method.name} - ${if (hasContinuation) "suspend" else "normal"} (UNEXPECTED)")
            }
        }

        val total = correctCount + incorrectCount
        val accuracy = if (total > 0) (correctCount * 100.0 / total) else 0.0
        println("   📊 Accuracy: $correctCount/$total (${String.format("%.1f", accuracy)}%)")
    }
}
