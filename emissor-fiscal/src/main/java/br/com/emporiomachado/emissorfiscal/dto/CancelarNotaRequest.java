package br.com.emporiomachado.emissorfiscal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarNotaRequest(
        @NotBlank @Size(min = 15, max = 255) String justificativa
) {
}
