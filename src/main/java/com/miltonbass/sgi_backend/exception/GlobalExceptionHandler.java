package com.miltonbass.sgi_backend.exception;

import com.miltonbass.sgi_backend.auth.dto.AuthDtos.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

/**
 * Manejador global de excepciones.
 * Convierte excepciones de dominio en respuestas HTTP consistentes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Excepciones de autenticación y autorización propias del dominio SGI.
     */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(AuthException ex) {
        ErrorResponse body = ErrorResponse.of(ex.getHttpStatus(), ex.getCodigo(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    /**
     * Errores de validación de @Valid en los request bodies.
     * Ejemplo: email inválido, password muy corto, campo requerido vacío.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String mensajes = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error != null ? error.getDefaultMessage() : null)
                .filter(msg -> msg != null)
                .collect(Collectors.joining("; "));

        ErrorResponse body = ErrorResponse.of(400, "VALIDACION_FALLIDA", mensajes);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Acceso denegado por regla de negocio (ej: CONSOLIDACION_SEDE intentando ver
     * un miembro no asignado).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.debug("AccessDeniedException: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(403, "ACCESO_DENEGADO", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * IllegalArgumentException — tipicamente UUID malformado u otros errores de
     * parsing.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("IllegalArgumentException: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(400, "PARAMETRO_INVALIDO", ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * MethodArgumentTypeMismatchException — path variable o query param con tipo
     * incompatible
     * (ej: UUID invalido en /{id}).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.debug("MethodArgumentTypeMismatchException: param={} value={}", ex.getName(), ex.getValue());
        String msg = "Valor invalido para el parametro '" + ex.getName() + "': " + ex.getValue();
        ErrorResponse body = ErrorResponse.of(400, "PARAMETRO_INVALIDO", msg);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * ResponseStatusException — para errores de negocio con código HTTP explícito
     * (ej. 404 recurso no encontrado).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        String codigo = status == 404 ? "RECURSO_NO_ENCONTRADO" : "ERROR_SOLICITUD";
        ErrorResponse body = ErrorResponse.of(status, codigo,
                ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    /**
     * Catch-all para excepciones no controladas.
     * NO revelar detalles internos al cliente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Error no controlado: {}", ex.getMessage(), ex);
        ErrorResponse body = ErrorResponse.of(500, "ERROR_INTERNO",
                "Ocurrió un error inesperado. Por favor intenta de nuevo.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}