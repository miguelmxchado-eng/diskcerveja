package com.diskcerveja.manager.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Webhook InfinitePay (campos relevantes). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InfinitePayWebhookRequest(
        String invoice_slug,
        Integer amount,
        Integer paid_amount,
        Integer installments,
        String capture_method,
        String transaction_nsu,
        String order_nsu,
        String receipt_url) {}
