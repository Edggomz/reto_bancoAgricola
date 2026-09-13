package com.bancoagricola.ruta.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.bancoagricola.ruta.repository", considerNestedRepositories = true)
public class PersistenceConfig {}
