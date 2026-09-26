package br.com.emporiomachado.emissorfiscal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload de venda vindo do PDV / Disk Cerveja (ou Postman na homologação).
 */
public record EmitirNotaRequest(
        String pedidoId,
        @Valid DestinatarioRequest destinatario,
        @NotEmpty @Valid List<ItemVendaRequest> itens,
        @NotEmpty @Valid List<PagamentoRequest> pagamentos,
        BigDecimal descontoTotal,
        /** Frete / taxa de entrega (entra em vFrete e no total da NFC-e). */
        BigDecimal freteTotal,
        String observacao
) {
    public record DestinatarioRequest(
            String cpfCnpj,
            String nome,
            String email
    ) {
    }

    public record ItemVendaRequest(
            @NotBlank String codigo,
            String ean,
            @NotBlank String descricao,
            @NotBlank String ncm,
            @NotBlank String cfop,
            @NotBlank String unidade,
            @NotNull @DecimalMin("0.0001") BigDecimal quantidade,
            @NotNull @DecimalMin("0.00") BigDecimal valorUnitario,
            BigDecimal desconto,
            /** CST ICMS (regime normal) ou CSOSN (Simples). Default CSOSN 102. */
            String cstOuCsosn,
            String origem
    ) {
    }

    public record PagamentoRequest(
            /** tPag NFC-e: 01 dinheiro, 03 crédito, 04 débito, 17 PIX, 99 outros */
            @NotBlank String forma,
            @NotNull @DecimalMin("0.01") BigDecimal valor,
            /** Obrigatório quando forma=99 (xPag). */
            String descricao
    ) {
        public PagamentoRequest(String forma, BigDecimal valor) {
            this(forma, valor, null);
        }
    }
}
