package com.diskcerveja.manager.dto;

import com.diskcerveja.manager.domain.enums.CategoriaProduto;
import java.math.BigDecimal;
import java.util.List;

public record ComboResponse(
        Long id,
        String nome,
        String codigo,
        String codigoBarras,
        String codigoQr,
        CategoriaProduto categoria,
        String descricao,
        String imagem,
        BigDecimal precoVenda,
        boolean ativo,
        BigDecimal custoTotal,
        BigDecimal lucro,
        BigDecimal margem,
        long quantidadeVendida,
        BigDecimal faturamento,
        int estoqueDisponivel,
        List<ComboItemResponse> itens) {}
