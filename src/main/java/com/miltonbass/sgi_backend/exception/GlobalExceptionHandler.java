package com.miltonbass.sgi_backend.exception;

import com.miltonbass.sgi_backend.auth.dto.AuthDtos.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        log.debug("AuthException: {} - {}", ex.getCodigo(), ex.getMessage());
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
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        ErrorResponse body = ErrorResponse.of(400, "VALIDACION_FALLIDA", mensajes);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * IllegalArgumentException — tipicamente UUID malformado u otros errores de parsing.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("IllegalArgumentException: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(400, "PARAMETRO_INVALIDO", ex.getMessage());
        return ResponseEntity.badRequest().body(body);
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