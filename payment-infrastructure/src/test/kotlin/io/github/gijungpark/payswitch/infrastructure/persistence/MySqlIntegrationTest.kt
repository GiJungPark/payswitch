package io.github.gijungpark.payswitch.infrastructure.persistence

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.mysql.MySQLContainer

/**
 * 실제 MySQL Testcontainer에 Flyway migration을 적용한 context로 실행하는 통합 테스트 기반.
 *
 * Spring context cache가 테스트 class 간에 재사용되므로 container는 JVM당 한 번만 시작한다.
 */
@SpringBootTest
abstract class MySqlIntegrationTest {

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun deleteRows() {
        jdbcTemplate.update("DELETE FROM financial_transaction")
        jdbcTemplate.update("DELETE FROM payment")
    }

    companion object {
        private val mysql: MySQLContainer = MySQLContainer("mysql:8.4").apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun dataSourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }
}
