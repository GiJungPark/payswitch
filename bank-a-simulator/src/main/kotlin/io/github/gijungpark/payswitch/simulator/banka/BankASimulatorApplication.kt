package io.github.gijungpark.payswitch.simulator.banka

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Bank A 기관 simulator 실행 진입점.
 *
 * ADR 0004에 따라 PaySwitch module과 분리된 별도 프로세스로 실행한다.
 */
@SpringBootApplication
class BankASimulatorApplication

fun main(args: Array<String>) {
    runApplication<BankASimulatorApplication>(*args)
}
