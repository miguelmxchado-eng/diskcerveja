package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.CategoriaProduto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProdutoDto(
        Long id,
        @NotBlank String nome,
        @Size(max = 80) String codigoBarras,
        @Size(max = 80) String codigoQr,
        @Size(max = 80) String codigoInterno,
        @NotNull CategoriaProduto categoria,
        @NotNull BigDecimal preco,
        BigDecimal custo,
        @NotNull Integer estoqueAtual,
        @NotNull Integer estoqueMinimo,
        boolean ativo) {}
