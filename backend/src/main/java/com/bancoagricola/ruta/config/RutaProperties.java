package com.bancoagricola.ruta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ruta")
public record RutaProperties(String adminKey, Push push, Firebase firebase) {

  public record Push(int diasAviso, String cron, String zona, String expoUrl) {}

  public record Firebase(String credentialsB64, String credentialsPath) {}
}
