package com.diskcerveja.manager.web;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("erro", "Credenciais inválidas."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Dados inválidos.");
        return ResponseEntity.badRequest().body(Map.of("erro", msg));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> dataIntegrity(DataIntegrityViolationException ex) {
        String root = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "";
        String msg = "Não foi possível salvar (dados em conflito).";
        if (root != null) {
            if (root.contains("uq_combo_codigo_barras")) {
                msg = "Já existe um combo com este código de barras.";
            } else if (root.contains("uq_combo_codigo_qr")) {
                msg = "Já existe um combo com este QR Code.";
            } else if (root.contains("uq_combo_codigo")) {
                msg = "Já existe um combo com este código.";
            } else if (root.contains("codigo_barras") || root.contains("uq_produto_codigo_barras")) {
                msg = "Já existe um produto com este código de barras.";
            } else if (root.contains("codigo_qr") || root.contains("uq_produto_codigo_qr")) {
                msg = "Já existe um produto com este QR Code.";
            } else if (root.contains("codigo_interno") || root.contains("uq_produto_codigo_interno")) {
                msg = "Já existe um produto com este código interno.";
            }
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", msg));
    }
}
