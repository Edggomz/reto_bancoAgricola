package com.bancoagricola.ruta.config;

import com.bancoagricola.ruta.web.CurrentCustomerResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS abierto para la demo (la app Expo llega desde orígenes variables: web,
 * LAN, túnel). En producción restringir a los orígenes reales.
 */
@Configuration
@EnableScheduling
public class WebConfig implements WebMvcConfigurer {
  private final CurrentCustomerResolver currentCustomerResolver;

  public WebConfig(CurrentCustomerResolver currentCustomerResolver) {
    this.currentCustomerResolver = currentCustomerResolver;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
        .allowedOriginPatterns("*")
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*");
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(currentCustomerResolver);
  }
}
