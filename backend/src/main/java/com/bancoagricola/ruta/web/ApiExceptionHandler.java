package com.bancoagricola.ruta.web;

import com.bancoagricola.ruta.error.ConflictException;
import com.bancoagricola.ruta.error.NotFoundException;
import com.bancoagricola.ruta.error.UnauthorizedException;
import com.bancoagricola.ruta.repository.Repositorios;
import com.bancoagricola.ruta.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
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

/**
 * Mapea errores a { message }, la forma que lee src/api/client.ts del frontend. Cada
 * error queda en la auditoría con su ruta, salvo los archivos que no existen (favicon).
 */
@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private final AuditoriaService auditoria;
  private final Repositorios.Tokens tokens;

  public ApiExceptionHandler(AuditoriaService auditoria, Repositorios.Tokens tokens) {
    this.auditoria = auditoria;
    this.tokens = tokens;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex, HttpServletRequest req) {
    return body(req, HttpStatus.BAD_REQUEST, ex.getMessage(), null);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<Map<String, String>> conflictoDeEstado(ConflictException ex, HttpServletRequest req) {
    return body(req, HttpStatus.CONFLICT, ex.getMessage(), null);
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<Map<String, String>> unauthorized(UnauthorizedException ex, HttpServletRequest req) {
    return body(req, HttpStatus.UNAUTHORIZED, ex.getMessage(), null);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Map<String, String>> sinArchivo(NoResourceFoundException ex) {
    return respuesta(HttpStatus.NOT_FOUND, "No encontramos ese recurso.");
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Map<String, String>> notFound(NotFoundException ex, HttpServletRequest req) {
    return body(req, HttpStatus.NOT_FOUND, ex.getMessage(), null);
  }

  @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class,
      HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<Map<String, String>> invalid(Exception ex, HttpServletRequest req) {
    return body(req, HttpStatus.BAD_REQUEST, "La solicitud no es válida.", ex.getClass().getSimpleName());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<Map<String, String>> method(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
    return body(req, HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido.", null);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException ex, HttpServletRequest req) {
    log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
    return body(req, HttpStatus.CONFLICT, "No se pudo guardar: choca con un dato existente.", ex.getMostSpecificCause().getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> generic(Exception ex, HttpServletRequest req) {
    log.error("Error no controlado", ex);
    return body(req, HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un problema interno. Intenta de nuevo.",
        ex.getClass().getSimpleName() + ": " + ex.getMessage());
  }

  private ResponseEntity<Map<String, String>> body(HttpServletRequest req, HttpStatus status, String message, String causa) {
    String texto = message == null ? status.getReasonPhrase() : message;
    String ruta = req.getRequestURI().substring(Math.min(req.getContextPath().length(), req.getRequestURI().length()));
    if (!ruta.startsWith("/admin/auditoria")) {
      auditoria.registrar(canal(ruta), "http." + status.value(), status.is5xxServerError() ? AuditoriaService.ERROR : AuditoriaService.AVISO,
          cliente(req), null, status.value() + " · " + req.getMethod() + " " + ruta + " · " + texto,
          causa == null ? null : Map.of("causa", causa), null);
    }
    return respuesta(status, texto);
  }

  /** A quién le pasó, si traía una sesión válida. */
  private String cliente(HttpServletRequest req) {
    String auth = req.getHeader("Authorization");
    if (auth == null || !auth.startsWith("Bearer ")) return null;
    try {
      return tokens.findById(auth.substring(7).trim()).map(t -> t.getClienteId()).orElse(null);
    } catch (Exception e) {
      return null;
    }
  }

  private static String canal(String ruta) {
    if (ruta.startsWith("/voz")) return AuditoriaService.VOZ;
    if (ruta.startsWith("/asesor")) return AuditoriaService.CHAT;
    if (ruta.startsWith("/admin")) return AuditoriaService.ADMIN;
    if (ruta.startsWith("/ai")) return AuditoriaService.SISTEMA;
    return AuditoriaService.APP;
  }

  private static ResponseEntity<Map<String, String>> respuesta(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Map.of("message", message));
  }
}
