package com.diskcerveja.manager.service;

import com.diskcerveja.manager.config.EmissorFiscalProperties;
import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.entity.PedidoItem;
import com.diskcerveja.manager.domain.entity.PedidoPagamento;
import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.StatusPedido;
import com.diskcerveja.manager.dto.fiscal.EmissorEmitirRequest;
import com.diskcerveja.manager.dto.fiscal.EmissorNotaResponse;
import com.diskcerveja.manager.repository.PedidoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NfcePedidoService {

    private static final Logger log = LoggerFactory.getLogger(NfcePedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final EmissorFiscalClient client;
    private final EmissorFiscalProperties properties;
    /** Proxy próprio — afterCommit e REQUIRES_NEW precisam do proxy Spring. */
    private final NfcePedidoService self;

    public NfcePedidoService(
            PedidoRepository pedidoRepository,
            EmissorFiscalClient client,
            EmissorFiscalProperties properties,
            @Lazy NfcePedidoService self) {
        this.pedidoRepository = pedidoRepository;
        this.client = client;
        this.properties = properties;
        this.self = self;
    }

    /** Agenda emissão após commit da venda (não atrasa / não desfaz o pedido). */
    public void agendarAposEntrega(Long pedidoId) {
        if (!client.isEnabled() || pedidoId == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            self.emitirParaPedido(pedidoId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    self.emitirParaPedido(pedidoId);
                } catch (Exception e) {
                    log.warn("Falha ao emitir NFC-e do pedido {}: {}", pedidoId, e.getMessage());
                }
            }
        });
    }

    /** Reemissão sob demanda (PDV / histórico). HTTP SEFAZ fora da transaction. */
    public Pedido reemitir(Long pedidoId) {
        EmissorEmitirRequest req = self.prepararEmissao(pedidoId, true);
        return executarEmissao(pedidoId, req);
    }

    /** Chamado após ENTREGUE (afterCommit). */
    public void emitirParaPedido(Long pedidoId) {
        try {
            EmissorEmitirRequest req = self.prepararEmissao(pedidoId, false);
            if (req == null) {
                return;
            }
            executarEmissao(pedidoId, req);
        } catch (IllegalStateException | IllegalArgumentException e) {
            log.warn("NFC-e pedido #{}: {}", pedidoId, e.getMessage());
        }
    }

    /**
     * TX curta: valida e marca ENVIANDO; monta payload.
     * @param exigirReemissao se true, falha se já autorizada / emissor off / não entregue
     * @return null se emissão silenciosa deve ser ignorada (já autorizada / pedido inválido)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmissorEmitirRequest prepararEmissao(Long pedidoId, boolean exigirReemissao) {
        Pedido p = pedidoRepository.findByIdWithItens(pedidoId).orElse(null);
        if (p == null) {
            if (exigirReemissao) {
                throw new IllegalArgumentException("Pedido não encontrado.");
            }
            return null;
        }
        if (p.getStatus() != StatusPedido.ENTREGUE) {
            if (exigirReemissao) {
                throw new IllegalStateException("Só é possível emitir NFC-e de pedido entregue.");
            }
            return null;
        }
        if ("AUTORIZADA".equalsIgnoreCase(p.getNfceStatus())) {
            if (exigirReemissao) {
                throw new IllegalStateException("Pedido já possui NFC-e autorizada.");
            }
            return null;
        }
        if (!client.isEnabled()) {
            if (exigirReemissao) {
                throw new IllegalStateException(
                        "Emissor fiscal desligado. Defina EMISSOR_FISCAL_ENABLED=true e a URL.");
            }
            return null;
        }
        p.setNfceStatus("ENVIANDO");
        p.setNfceMotivo(null);
        pedidoRepository.save(p);
        return montarRequest(p);
    }

    private Pedido executarEmissao(Long pedidoId, EmissorEmitirRequest req) {
        try {
            EmissorNotaResponse resp = client.emitir(req);
            return self.salvarResultadoEmissao(pedidoId, resp, null);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "Falha na emissão" : e.getMessage();
            log.warn("NFC-e pedido #{}: {}", pedidoId, msg);
            return self.salvarResultadoEmissao(pedidoId, null, msg);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Pedido salvarResultadoEmissao(Long pedidoId, EmissorNotaResponse resp, String erro) {
        Pedido p = pedidoRepository
                .findByIdWithItens(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
        if (erro != null) {
            p.setNfceStatus("ERRO");
            p.setNfceMotivo(erro.length() > 500 ? erro.substring(0, 500) : erro);
        } else {
            aplicarResposta(p, resp);
        }
        return pedidoRepository.save(p);
    }

    /** Cancelamento: HTTP fora da TX; motivo de falha é persistido mesmo assim. */
    public Pedido cancelar(Long pedidoId, String justificativa) {
        String notaId = self.validarCancelamento(pedidoId, justificativa);
        String j = justificativa.trim();
        try {
            EmissorNotaResponse resp = client.cancelar(notaId, j);
            return self.salvarResultadoCancelamento(pedidoId, resp, null);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "Falha no cancelamento" : e.getMessage();
            log.warn("Cancelamento NFC-e pedido #{}: {}", pedidoId, msg);
            self.salvarResultadoCancelamento(pedidoId, null, msg);
            throw new IllegalStateException(msg, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String validarCancelamento(Long pedidoId, String justificativa) {
        Pedido p = pedidoRepository
                .findByIdWithItens(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
        if (!"AUTORIZADA".equalsIgnoreCase(p.getNfceStatus())) {
            throw new IllegalStateException("Só é possível cancelar NFC-e autorizada.");
        }
        if (p.getNfceIdExterno() == null || p.getNfceIdExterno().isBlank()) {
            throw new IllegalStateException("Pedido sem vínculo da nota no emissor.");
        }
        if (!client.isEnabled()) {
            throw new IllegalStateException(
                    "Emissor fiscal desligado. Defina EMISSOR_FISCAL_ENABLED=true e a URL.");
        }
        String j = justificativa == null ? "" : justificativa.trim();
        if (j.length() < 15) {
            throw new IllegalArgumentException("Informe o motivo com pelo menos 15 caracteres.");
        }
        return p.getNfceIdExterno();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Pedido salvarResultadoCancelamento(Long pedidoId, EmissorNotaResponse resp, String erro) {
        Pedido p = pedidoRepository
                .findByIdWithItens(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
        if (erro != null) {
            p.setNfceMotivo(erro.length() > 500 ? erro.substring(0, 500) : erro);
            return pedidoRepository.save(p);
        }
        aplicarResposta(p, resp);
        if (resp != null && resp.status() != null && !"CANCELADA".equalsIgnoreCase(p.getNfceStatus())) {
            p.setNfceStatus(resp.status());
        }
        return pedidoRepository.save(p);
    }

    private void aplicarResposta(Pedido p, EmissorNotaResponse resp) {
        if (resp == null) {
            p.setNfceStatus("ERRO");
            p.setNfceMotivo("Resposta vazia do emissor");
            return;
        }
        p.setNfceIdExterno(resp.id());
        p.setNfceChave(resp.chaveAcesso());
        p.setNfceProtocolo(resp.protocolo());
        p.setNfceQrCode(resp.qrCode());
        p.setNfceStatus(resp.status() != null ? resp.status() : "ERRO");
        String motivo = resp.xMotivo();
        if (motivo == null || motivo.isBlank()) {
            motivo = resp.cStat();
        }
        p.setNfceMotivo(motivo);
        if ("AUTORIZADA".equalsIgnoreCase(p.getNfceStatus())) {
            p.setNfceEmitidaEm(Instant.now());
        }
    }

    private EmissorEmitirRequest montarRequest(Pedido p) {
        String ncmDefault = blankTo(properties.defaultNcm(), "22030000");
        String cfopDefault = blankTo(properties.defaultCfop(), "5102");
        String csosnDefault = blankTo(properties.defaultCsosn(), "102");

        List<EmissorEmitirRequest.Item> itens = new ArrayList<>();
        int i = 1;
        for (PedidoItem item : p.getItens()) {
            String desc = item.getDescricao();
            if (desc == null || desc.isBlank()) {
                if (item.getProduto() != null) {
                    desc = item.getProduto().getNome();
                } else if (item.getCombo() != null) {
                    desc = item.getCombo().getNome();
                } else {
                    desc = "Item";
                }
            }
            if (item.getObservacao() != null && !item.getObservacao().isBlank()) {
                desc = desc + " (" + item.getObservacao() + ")";
            }
            String ean = null;
            String ncm = ncmDefault;
            String cfop = cfopDefault;
            String csosn = csosnDefault;
            if (item.getProduto() != null) {
                if (item.getProduto().getCodigoBarras() != null) {
                    ean = item.getProduto().getCodigoBarras();
                }
                ncm = blankTo(item.getProduto().getNcm(), ncmDefault);
                cfop = blankTo(item.getProduto().getCfop(), cfopDefault);
                csosn = blankTo(item.getProduto().getCsosn(), csosnDefault);
            }
            String codigo = item.getProduto() != null
                    ? String.valueOf(item.getProduto().getId())
                    : (item.getCombo() != null ? "C" + item.getCombo().getId() : "I" + i);
            itens.add(new EmissorEmitirRequest.Item(
                    codigo,
                    ean,
                    desc,
                    ncm,
                    cfop,
                    "UN",
                    BigDecimal.valueOf(item.getQuantidade()),
                    item.getPrecoUnitario().setScale(2, RoundingMode.HALF_UP),
                    null,
                    csosn,
                    "0"));
            i++;
        }

        List<EmissorEmitirRequest.Pagamento> pags = new ArrayList<>();
        if (p.getPagamentos() != null && !p.getPagamentos().isEmpty()) {
            for (PedidoPagamento pg : p.getPagamentos()) {
                String[] forma = mapForma(pg.getFormaPagamento());
                pags.add(new EmissorEmitirRequest.Pagamento(
                        forma[0],
                        pg.getValor().setScale(2, RoundingMode.HALF_UP),
                        forma[1]));
            }
        } else {
            String[] forma = mapForma(p.getFormaPagamento());
            pags.add(new EmissorEmitirRequest.Pagamento(
                    forma[0],
                    p.getTotal().setScale(2, RoundingMode.HALF_UP),
                    forma[1]));
        }

        EmissorEmitirRequest.Destinatario dest = null;
        String cpf = digits(p.getCpfNaNota());
        if (cpf != null && (cpf.length() == 11 || cpf.length() == 14)) {
            dest = new EmissorEmitirRequest.Destinatario(cpf, p.getClienteNome(), null);
        }

        BigDecimal desconto = p.getDesconto() != null ? p.getDesconto() : BigDecimal.ZERO;
        BigDecimal frete = BigDecimal.ZERO;
        if (p.getEntrega() != null && p.getEntrega().getTaxaEntrega() != null) {
            frete = p.getEntrega().getTaxaEntrega();
        }
        return new EmissorEmitirRequest(
                String.valueOf(p.getId()),
                dest,
                itens,
                pags,
                desconto.compareTo(BigDecimal.ZERO) > 0 ? desconto : null,
                frete.compareTo(BigDecimal.ZERO) > 0 ? frete : null,
                null);
    }

    /** @return [tPag, xPag opcional] */
    private static String[] mapForma(FormaPagamento f) {
        if (f == null) {
            return new String[] {"01", null};
        }
        return switch (f) {
            case DINHEIRO -> new String[] {"01", null};
            case PIX -> new String[] {"17", null};
            case CARTAO -> new String[] {"99", "Cartao"};
            case MISTO -> new String[] {"99", "Pagamento misto"};
        };
    }

    private static String digits(String s) {
        if (s == null) {
            return null;
        }
        String d = s.replaceAll("\\D", "");
        return d.isBlank() ? null : d;
    }

    private static String blankTo(String v, String def) {
        return v == null || v.isBlank() ? def : v.trim();
    }
}
