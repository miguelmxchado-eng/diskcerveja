package com.diskcerveja.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.emissor-fiscal")
public record EmissorFiscalProperties(
        boolean enabled,
        String baseUrl,
        String defaultNcm,
        String defaultCfop,
        String defaultCsosn) {}
