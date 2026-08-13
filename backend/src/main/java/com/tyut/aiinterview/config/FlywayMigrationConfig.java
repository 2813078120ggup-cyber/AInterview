package com.tyut.aiinterview.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayMigrationConfig {
    /**
     * Spring Boot 4 split database-migration auto-configuration out of the base JDBC starter.
     * Keep migration ownership explicit so the published classpath migrations always run before traffic is served.
     */
    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("8")
                .validateOnMigrate(true)
                .outOfOrder(false)
                .load();
    }
}
