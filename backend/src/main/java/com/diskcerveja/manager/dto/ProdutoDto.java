package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.CategoriaProduto;
import jakarta.validation.constraints.DecimalMin;
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
        @NotNull(message = "Preço de venda é obrigatório")
        @DecimalMin(value = "0.01", message = "Preço de venda deve ser maior que zero")
        BigDecimal preco,
        /** Preço avulso (opcional). */
        @DecimalMin(value = "0.01", message = "Preço da unidade deve ser maior que zero")
        BigDecimal precoUnidade,
        /** Ex.: 6 para pack c/6. Obrigatório se informar preço da unidade. */
        Integer unidadesPorEmbalagem,
        @NotNull(message = "Preço de compra é obrigatório")
        @DecimalMin(value = "0.00", message = "Preço de compra não pode ser negativo")
        BigDecimal custo,
        @NotNull Integer estoqueAtual,
        @NotNull Integer estoqueMinimo,
        boolean ativo) {}
