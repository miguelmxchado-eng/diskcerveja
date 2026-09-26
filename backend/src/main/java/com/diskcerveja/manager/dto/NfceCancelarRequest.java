package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NfceCancelarRequest(
        @NotBlank @Size(min = 15, max = 255) String justificativa) {}
