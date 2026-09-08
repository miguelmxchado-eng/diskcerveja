package com.diskcerveja.manager.dto;

public record ComboOpcaoResponse(
        Long id, String rotulo, Long produtoId, int ordem, boolean ativo) {}
