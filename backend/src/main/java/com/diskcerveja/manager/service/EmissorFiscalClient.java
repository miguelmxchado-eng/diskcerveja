package com.diskcerveja.manager.service;

import com.diskcerveja.manager.config.EmissorFiscalProperties;
import com.diskcerveja.manager.dto.fiscal.EmissorEmitirRequest;
import com.diskcerveja.manager.dto.fiscal.EmissorNotaResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class EmissorFiscalClient {

    private static final Logger log = LoggerFactory.getLogger(EmissorFiscalClient.class);

    private final EmissorFiscalProperties properties;
    private final RestClient restClient;

    public EmissorFiscalClient(EmissorFiscalProperties properties) {
        this.properties = properties;
        String base = properties.baseUrl() == null ? "http://localhost:8090" : properties.baseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.restClient = RestClient.builder().baseUrl(base).build();
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public EmissorNotaResponse emitir(EmissorEmitirRequest body) {
        try {
            return restClient
                    .post()
                    .uri("/api/notas")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(EmissorNotaResponse.class);
        } catch (RestClientResponseException ex) {
            String detail = extrairDetail(ex.getResponseBodyAsString());
            throw new IllegalStateException(
                    detail != null ? detail : ("Emissor fiscal HTTP " + ex.getStatusCode().value()),
                    ex);
        }
    }

    public EmissorNotaResponse cancelar(String notaId, String justificativa) {
        try {
            return restClient
                    .post()
                    .uri("/api/notas/{id}/cancelar", notaId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("justificativa", justificativa))
                    .retrieve()
                    .body(EmissorNotaResponse.class);
        } catch (RestClientResponseException ex) {
            String detail = extrairDetail(ex.getResponseBodyAsString());
            throw new IllegalStateException(
                    detail != null ? detail : ("Emissor fiscal HTTP " + ex.getStatusCode().value()),
                    ex);
        }
    }

    @SuppressWarnings("unchecked")
    private String extrairDetail(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            // ProblemDetail JSON: {"detail":"..."}
            int i = body.indexOf("\"detail\"");
            if (i < 0) {
                return body.length() > 300 ? body.substring(0, 300) : body;
            }
            int start = body.indexOf(':', i) + 1;
            String rest = body.substring(start).trim();
            if (rest.startsWith("\"")) {
                int end = rest.indexOf('"', 1);
                if (end > 1) {
                    return rest.substring(1, end);
                }
            }
            return body.length() > 300 ? body.substring(0, 300) : body;
        } catch (Exception e) {
            log.debug("Não foi possível parsear detalhe do emissor: {}", e.toString());
            return body.length() > 300 ? body.substring(0, 300) : body;
        }
    }
}
