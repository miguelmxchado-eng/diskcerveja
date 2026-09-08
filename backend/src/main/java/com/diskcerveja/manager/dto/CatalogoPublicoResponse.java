package com.diskcerveja.manager.dto;

import java.math.BigDecimal;
import java.util.List;

public record CatalogoPublicoResponse(LojaPublicaDto loja, List<CatalogoCategoriaDto> categorias) {

    public record LojaPublicaDto(
            String nome,
            String whatsapp,
            boolean aberta,
            String horario,
            BigDecimal taxaEntrega,
            BigDecimal pedidoMinimo,
            String info,
            boolean pagamentoOnline) {}

    public record CatalogoCategoriaDto(String codigo, String nome, List<CatalogoItemDto> itens) {}

    public record CatalogoItemDto(
            String tipo,
            Long id,
            String nome,
            String descricao,
            String imagemUrl,
            String categoria,
            BigDecimal preco,
            BigDecimal precoUnidade,
            Integer unidadesPorEmbalagem,
            boolean disponivel,
            boolean promocao,
            boolean configuravel,
            List<CatalogoGrupoOpcaoDto> grupos) {}

    public record CatalogoGrupoOpcaoDto(
            Long id,
            String nome,
            boolean obrigatorio,
            int minimo,
            int maximo,
            List<CatalogoOpcaoDto> opcoes) {}

    public record CatalogoOpcaoDto(Long id, String rotulo) {}
}
