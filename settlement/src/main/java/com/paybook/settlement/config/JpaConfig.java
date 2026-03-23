package com.paybook.settlement.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan("com.paybook")
@EnableJpaRepositories("com.paybook")
public class JpaConfig {
}
