package com.diskcerveja.manager.dto.fiscal;

import java.math.BigDecimal;
import java.util.List;

/** Payload compatível com o emissor-fiscal (POST /api/notas). */
public record EmissorEmitirRequest(
        String pedidoId,
        Destinatario destinatario,
        List<Item> itens,
        List<Pagamento> pagamentos,
        BigDecimal descontoTotal,
        BigDecimal freteTotal,
        String observacao) {

    public record Destinatario(String cpfCnpj, String nome, String email) {}

    public record Item(
            String codigo,
            String ean,
            String descricao,
            String ncm,
            String cfop,
            String unidade,
            BigDecimal quantidade,
            BigDecimal valorUnitario,
            BigDecimal desconto,
            String cstOuCsosn,
            String origem) {}

    public record Pagamento(String forma, BigDecimal valor, String descricao) {
        public Pagamento(String forma, BigDecimal valor) {
            this(forma, valor, null);
        }
    }
}
