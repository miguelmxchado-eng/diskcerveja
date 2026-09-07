package com.diskcerveja.manager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContaClienteLoginRequest(
        @NotBlank @Size(max = 32) String telefone, @NotBlank @Size(max = 40) String senha) {}
