package com.diskcerveja.manager.dto;

import java.util.List;

public record PdvInsightsResponse(
        int diasBase,
        List<ProdutoVendaRankDto> maisVendidos,
        List<ProdutoVendaRankDto> alertaEstoqueQuente) {}
