package com.diskcerveja.manager.dto;

import java.util.List;

public record MaisVendidosSerieResponse(String chave, List<MaisVendidosDiaDto> porDia) {}
