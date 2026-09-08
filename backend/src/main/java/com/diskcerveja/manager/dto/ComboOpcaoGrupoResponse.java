package com.diskcerveja.manager.dto;

import java.util.List;

public record ComboOpcaoGrupoResponse(
        Long id,
        String nome,
        boolean obrigatorio,
        int minimo,
        int maximo,
        int ordem,
        List<ComboOpcaoResponse> opcoes) {}
