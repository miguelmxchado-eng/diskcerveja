package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.CategoriaProduto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record ComboDto(
        Long id,
        @NotBlank String nome,
        @Size(max = 80) String codigoBarras,
        @Size(max = 80) String codigoQr,
        @NotNull CategoriaProduto categoria,
        @Size(max = 2000) String descricao,
        String imagem,
        @NotNull BigDecimal precoVenda,
        boolean ativo,
        @NotEmpty @Valid List<ComboItemDto> itens) {}
