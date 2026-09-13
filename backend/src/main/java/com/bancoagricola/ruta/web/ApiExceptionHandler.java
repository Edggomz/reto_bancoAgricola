package com.bancoagricola.ruta.web;

import com.bancoagricola.ruta.error.NotFoundException;
import com.bancoagricola.ruta.error.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/** Mapea errores a { message }, la forma que lee src/api/client.ts del frontend. */
@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
    return body(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<Map<String, String>> unauthorized(UnauthorizedException ex) {
    return body(HttpStatus.UNAUTHORIZED, ex.getMessage());
  }

  @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
  public ResponseEntity<Map<String, String>> notFound(Exception ex) {
    return body(HttpStatus.NOT_FOUND, ex instanceof NotFoundException ? ex.getMessage() : "No encontramos ese recurso.");
  }

  @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class,
      HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<Map<String, String>> invalid(Exception ex) {
    return body(HttpStatus.BAD_REQUEST, "La solicitud no es válida.");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<Map<String, String>> method(HttpRequestMethodNotSupportedException ex) {
    return body(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException ex) {
    log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
    return body(HttpStatus.CONFLICT, "No se pudo guardar: choca con un dato existente.");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> generic(Exception ex) {
    log.error("Error no controlado", ex);
    return body(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un problema interno. Intenta de nuevo.");
  }

  private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Map.of("message", message == null ? status.getReasonPhrase() : message));
  }
}
