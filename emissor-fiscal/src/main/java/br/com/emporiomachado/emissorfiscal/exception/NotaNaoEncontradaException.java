package br.com.emporiomachado.emissorfiscal.exception;

import java.util.UUID;

public class NotaNaoEncontradaException extends RuntimeException {
    public NotaNaoEncontradaException(UUID id) {
        super("Nota não encontrada: " + id);
    }
}
