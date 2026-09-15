package io.github.gijungpark.payswitch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * PaySwitch 결제 스위치 실행 진입점.
 *
 * package root에 두어 payment-application과 payment-infrastructure의 Spring component를 함께 조립한다.
 */
@SpringBootApplication
class PaymentApiApplication

fun main(args: Array<String>) {
    runApplication<PaymentApiApplication>(*args)
}
