package io.github.gijungpark.payswitch.infrastructure.persistence

import org.flywaydb.core.Flyway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * application context 시작 시 `classpath:db/migration`의 versioned migration을 적용한다.
 *
 * Spring Boot 4의 Flyway auto-configuration은 별도 `spring-boot-flyway` module에 있으므로 승인된 `flyway-core`만으로 직접 구성한다.
 */
@Configuration(proxyBeanMethods = false)
internal class FlywayMigrationConfiguration {

    @Bean(initMethod = "migrate")
    fun flyway(dataSource: DataSource): Flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations(MIGRATION_LOCATION)
            .load()

    private companion object {
        const val MIGRATION_LOCATION = "classpath:db/migration"
    }
}
