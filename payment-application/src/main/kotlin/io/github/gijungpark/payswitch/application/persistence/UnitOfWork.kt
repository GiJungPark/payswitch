package io.github.gijungpark.payswitch.application.persistence

/**
 * 여러 저장소 호출을 하나의 DB transaction으로 실행하는 port.
 */
interface UnitOfWork {

    /**
     * [block]을 하나의 transaction에서 실행하고 정상 종료하면 commit한다.
     *
     * [block]이 예외를 던지면 rollback한 뒤 예외를 전파한다. 멱등성 판정에 쓰는 unique 제약 위반은 rollback 후
     * [UniqueConstraintViolationException]으로 변환하고, 그 밖의 DB 오류는 변환하지 않고 그대로 전파한다.
     */
    fun <T> execute(block: () -> T): T
}
