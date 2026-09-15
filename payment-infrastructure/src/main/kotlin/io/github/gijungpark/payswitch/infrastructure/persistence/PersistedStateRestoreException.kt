package io.github.gijungpark.payswitch.infrastructure.persistence

/**
 * 저장된 row가 domain 규칙상 도달할 수 없는 상태라서 domain 객체로 복원할 수 없음을 나타낸다.
 */
class PersistedStateRestoreException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * [restore] 중 발생한 domain 검증 실패를 [PersistedStateRestoreException]으로 감싼다.
 */
internal inline fun <T> restorePersistedState(table: String, id: String, restore: () -> T): T =
    try {
        restore()
    } catch (e: PersistedStateRestoreException) {
        throw e
    } catch (e: IllegalArgumentException) {
        throw PersistedStateRestoreException("$table row '$id' is not a restorable state: ${e.message}", e)
    } catch (e: IllegalStateException) {
        throw PersistedStateRestoreException("$table row '$id' is not a restorable state: ${e.message}", e)
    }
