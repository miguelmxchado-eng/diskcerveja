package com.diskcerveja.manager.dto;

/** Webhook InfinitePay (campos relevantes). */
public record InfinitePayWebhookRequest(
        String invoice_slug,
        Integer amount,
        Integer paid_amount,
        Integer installments,
        String capture_method,
        String transaction_nsu,
        String order_nsu,
        String receipt_url) {}
