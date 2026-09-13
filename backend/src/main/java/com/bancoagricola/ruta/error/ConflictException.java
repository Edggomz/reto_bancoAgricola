package com.bancoagricola.ruta.error;

/** La operación es válida pero choca con el estado actual (p. ej. la fecha ya se cambió hace poco). */
public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
