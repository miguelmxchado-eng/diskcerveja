package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.entity.PedidoItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InfinitePayService {

    private static final Logger log = LoggerFactory.getLogger(InfinitePayService.class);
    private static final String LINKS_URL = "https://api.checkout.infinitepay.io/links";

    private final ConfigSistemaService configSistemaService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public InfinitePayService(ConfigSistemaService configSistemaService, ObjectMapper objectMapper) {
        this.configSistemaService = configSistemaService;
        this.objectMapper = objectMapper;
    }

    public String criarLinkCheckout(Pedido pedido, String clienteNome, String telefone, String baseUrl) {
        String handle = configSistemaService.getInfinitepayHandle();
        if (handle.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("handle", handle);
            body.put("order_nsu", String.valueOf(pedido.getId()));
            body.put("redirect_url", baseUrl + "/p?pago=1&pedido=" + pedido.getId());
            body.put("webhook_url", baseUrl + "/api/publico/infinitepay/webhook");

            List<Map<String, Object>> items = new ArrayList<>();
            BigDecimal subtotalItens = BigDecimal.ZERO;
            for (PedidoItem item : pedido.getItens()) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("quantity", item.getQuantidade());
                line.put("price", toCents(item.getPrecoUnitario()));
                line.put(
                        "description",
                        item.getDescricao() != null
                                ? item.getDescricao()
                                : (item.isCombo() ? "Combo" : "Produto"));
                items.add(line);
                subtotalItens = subtotalItens.add(
                        item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())));
            }
            BigDecimal desconto = pedido.getDesconto() != null ? pedido.getDesconto() : BigDecimal.ZERO;
            BigDecimal taxa = pedido.getTotal().subtract(subtotalItens.subtract(desconto));
            if (taxa.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> taxaLine = new LinkedHashMap<>();
                taxaLine.put("quantity", 1);
                taxaLine.put("price", toCents(taxa));
                taxaLine.put("description", "Taxa de entrega");
                items.add(taxaLine);
            }
            body.put("items", items);

            Map<String, Object> customer = new LinkedHashMap<>();
            customer.put("name", clienteNome);
            String digits = telefone == null ? "" : telefone.replaceAll("\\D", "");
            if (!digits.isBlank()) {
                if (!digits.startsWith("55")) {
                    digits = "55" + digits;
                }
                customer.put("phone_number", "+" + digits);
            }
            body.put("customer", customer);

            String payload = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LINKS_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("InfinitePay link falhou ({}): {}", response.statusCode(), response.body());
                throw new IllegalStateException("Não foi possível gerar o link de pagamento. Tente de novo.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
            Object url = parsed.get("url");
            if (url == null || url.toString().isBlank()) {
                throw new IllegalStateException("InfinitePay não retornou URL de checkout.");
            }
            return url.toString();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao criar link InfinitePay", e);
            throw new IllegalStateException("Falha ao conectar com InfinitePay. Tente de novo.");
        }
    }

    private static int toCents(BigDecimal value) {
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}
