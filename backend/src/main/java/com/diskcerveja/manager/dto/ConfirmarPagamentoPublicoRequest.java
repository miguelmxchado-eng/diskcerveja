package com.diskcerveja.manager.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Confirmação de pagamento no retorno do redirect InfinitePay. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfirmarPagamentoPublicoRequest(String transactionNsu, String slug, String captureMethod) {}
