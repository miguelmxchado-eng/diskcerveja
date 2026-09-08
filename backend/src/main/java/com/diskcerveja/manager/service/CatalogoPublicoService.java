package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Combo;
import com.diskcerveja.manager.domain.entity.ComboOpcao;
import com.diskcerveja.manager.domain.entity.Produto;
import com.diskcerveja.manager.domain.enums.CategoriaProduto;
import com.diskcerveja.manager.dto.CatalogoPublicoResponse;
import com.diskcerveja.manager.dto.CatalogoPublicoResponse.CatalogoCategoriaDto;
import com.diskcerveja.manager.dto.CatalogoPublicoResponse.CatalogoGrupoOpcaoDto;
import com.diskcerveja.manager.dto.CatalogoPublicoResponse.CatalogoItemDto;
import com.diskcerveja.manager.dto.CatalogoPublicoResponse.CatalogoOpcaoDto;
import com.diskcerveja.manager.dto.CatalogoPublicoResponse.LojaPublicaDto;
import com.diskcerveja.manager.dto.LojaConfigResponse;
import com.diskcerveja.manager.repository.ComboRepository;
import com.diskcerveja.manager.repository.ProdutoRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogoPublicoService {

    public static final String CAT_PROMOCOES = "PROMOCOES";

    private final ConfigSistemaService configSistemaService;
    private final ProdutoRepository produtoRepository;
    private final ComboRepository comboRepository;
    private final ZonaEntregaService zonaEntregaService;

    public CatalogoPublicoService(
            ConfigSistemaService configSistemaService,
            ProdutoRepository produtoRepository,
            ComboRepository comboRepository,
            ZonaEntregaService zonaEntregaService) {
        this.configSistemaService = configSistemaService;
        this.produtoRepository = produtoRepository;
        this.comboRepository = comboRepository;
        this.zonaEntregaService = zonaEntregaService;
    }

    @Transactional(readOnly = true)
    public LojaPublicaDto loja() {
        LojaConfigResponse c = configSistemaService.getLoja();
        return new LojaPublicaDto(
                c.nome(),
                c.whatsapp(),
                c.aberta(),
                c.horario(),
                zonaEntregaService.taxaExibidaCardapio(),
                c.pedidoMinimo(),
                c.info(),
                configSistemaService.isPagamentoOnlineAtivo());
    }

    @Transactional(readOnly = true)
    public CatalogoPublicoResponse catalogo(String q) {
        LojaPublicaDto loja = loja();
        String busca = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<CatalogoItemDto> itens = new ArrayList<>();
        List<CatalogoItemDto> promos = new ArrayList<>();

        for (Produto p : produtoRepository.findCardapioProdutos()) {
            if (!busca.isEmpty()
                    && !p.getNome().toLowerCase(Locale.ROOT).contains(busca)
                    && (p.getDescricaoCardapio() == null
                            || !p.getDescricaoCardapio().toLowerCase(Locale.ROOT).contains(busca))) {
                continue;
            }
            CatalogoItemDto item = new CatalogoItemDto(
                    "PRODUTO",
                    p.getId(),
                    p.getNome(),
                    p.getDescricaoCardapio(),
                    p.getImagemUrl(),
                    p.getCategoria().name(),
                    p.getPreco(),
                    p.getPrecoUnidade(),
                    p.getUnidadesPorEmbalagem(),
                    p.getEstoqueAtual() > 0,
                    p.isPromocaoCardapio(),
                    false,
                    null);
            itens.add(item);
            if (p.isPromocaoCardapio()) {
                promos.add(item);
            }
        }
        for (Combo c : comboRepository.findCardapioCombos()) {
            if (!busca.isEmpty()
                    && !c.getNome().toLowerCase(Locale.ROOT).contains(busca)
                    && (c.getDescricao() == null || !c.getDescricao().toLowerCase(Locale.ROOT).contains(busca))) {
                continue;
            }
            int estoque = estoqueCombo(c);
            List<CatalogoGrupoOpcaoDto> grupos = null;
            if (c.isConfiguravel()) {
                grupos = c.getGruposOpcao().stream()
                        .map(g -> new CatalogoGrupoOpcaoDto(
                                g.getId(),
                                g.getNome(),
                                g.isObrigatorio(),
                                g.getMinimo(),
                                g.getMaximo(),
                                g.getOpcoes().stream()
                                        .filter(ComboOpcao::isAtivo)
                                        .map(o -> new CatalogoOpcaoDto(o.getId(), o.getRotulo()))
                                        .toList()))
                        .filter(g -> g.opcoes() != null && !g.opcoes().isEmpty())
                        .toList();
            }
            CatalogoItemDto item = new CatalogoItemDto(
                    "COMBO",
                    c.getId(),
                    c.getNome(),
                    c.getDescricao(),
                    c.getImagem(),
                    c.getCategoria().name(),
                    c.getPrecoVenda(),
                    null,
                    null,
                    estoque > 0,
                    c.isPromocaoCardapio(),
                    c.isConfiguravel() && grupos != null && !grupos.isEmpty(),
                    grupos);
            itens.add(item);
            if (c.isPromocaoCardapio()) {
                promos.add(item);
            }
        }

        Map<String, List<CatalogoItemDto>> porCat = new LinkedHashMap<>();
        for (CategoriaProduto cat : CategoriaProduto.values()) {
            porCat.put(cat.name(), new ArrayList<>());
        }
        for (CatalogoItemDto item : itens) {
            porCat.computeIfAbsent(item.categoria(), k -> new ArrayList<>()).add(item);
        }

        List<CatalogoCategoriaDto> categorias = new ArrayList<>();
        if (!promos.isEmpty()) {
            promos.sort(Comparator.comparing(CatalogoItemDto::nome, String.CASE_INSENSITIVE_ORDER));
            categorias.add(new CatalogoCategoriaDto(CAT_PROMOCOES, "Promoções", promos));
        }
        for (Map.Entry<String, List<CatalogoItemDto>> e : porCat.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            e.getValue().sort(Comparator.comparing(CatalogoItemDto::nome, String.CASE_INSENSITIVE_ORDER));
            categorias.add(new CatalogoCategoriaDto(e.getKey(), labelCategoria(e.getKey()), e.getValue()));
        }
        return new CatalogoPublicoResponse(loja, categorias);
    }

    private static int estoqueCombo(Combo c) {
        if (c.getItens() == null || c.getItens().isEmpty()) {
            return 0;
        }
        int min = Integer.MAX_VALUE;
        for (var ci : c.getItens()) {
            Produto p = ci.getProduto();
            if (p == null || !p.isAtivo()) {
                return 0;
            }
            int possivel = ci.getQuantidade() > 0 ? p.getEstoqueAtual() / ci.getQuantidade() : 0;
            min = Math.min(min, possivel);
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    private static String labelCategoria(String codigo) {
        return switch (codigo) {
            case "CERVEJAS" -> "Cervejas";
            case "DESTILADOS" -> "Destilados";
            case "REFRIGERANTES" -> "Refrigerantes";
            case "ENERGETICOS" -> "Energéticos";
            case "PETISCOS" -> "Petiscos";
            case "COMBOS" -> "Combos";
            case "CIGARROS" -> "Cigarros";
            case CAT_PROMOCOES -> "Promoções";
            default -> "Outros";
        };
    }
}
