package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Pedido;
import com.diskcerveja.manager.domain.entity.PedidoItem;
import com.fasterxml.jackson.databind.JsonNode;
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
    private static final String PAYMENT_CHECK_URL = "https://api.checkout.infinitepay.io/payment_check";

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
            int itensCents = 0;
            for (PedidoItem item : pedido.getItens()) {
                Map<String, Object> line = new LinkedHashMap<>();
                int unitCents = toCents(item.getPrecoUnitario());
                line.put("quantity", item.getQuantidade());
                line.put("price", unitCents);
                line.put(
                        "description",
                        item.getDescricao() != null
                                ? item.getDescricao()
                                : (item.isCombo() ? "Combo" : "Produto"));
                items.add(line);
                itensCents += unitCents * item.getQuantidade();
            }
            int descontoCents = toCents(
                    pedido.getDesconto() != null ? pedido.getDesconto() : BigDecimal.ZERO);
            int totalCents = toCents(pedido.getTotal());
            int taxaCents = totalCents - (itensCents - descontoCents);
            if (taxaCents > 0) {
                Map<String, Object> taxaLine = new LinkedHashMap<>();
                taxaLine.put("quantity", 1);
                taxaLine.put("price", taxaCents);
                taxaLine.put("description", "Taxa de entrega");
                items.add(taxaLine);
            } else if (taxaCents < 0) {
                log.error(
                        "Total do pedido #{} menor que itens (itens={} desconto={} total={})",
                        pedido.getId(),
                        itensCents,
                        descontoCents,
                        totalCents);
                throw new IllegalStateException("Inconsistência no valor do checkout. Tente de novo.");
            }
            body.put("items", items);

            int somaLink = items.stream()
                    .mapToInt(it -> ((Integer) it.get("price")) * ((Integer) it.get("quantity")))
                    .sum();
            if (somaLink != totalCents) {
                log.error(
                        "Soma InfinitePay ({}) != total pedido #{} ({})",
                        somaLink,
                        pedido.getId(),
                        totalCents);
                throw new IllegalStateException("Inconsistência no valor do checkout. Tente de novo.");
            }

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
                throw new IllegalStateException(mensagemErroLink(response.body()));
            }
            JsonNode parsed = objectMapper.readTree(response.body());
            String url = firstText(parsed, "checkout_url", "url", "link");
            if (url == null || url.isBlank()) {
                log.warn("InfinitePay resposta sem URL: {}", response.body());
                throw new IllegalStateException("InfinitePay não retornou URL de checkout.");
            }
            return url;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao criar link InfinitePay", e);
            throw new IllegalStateException("Falha ao conectar com InfinitePay. Tente de novo.");
        }
    }

    /**
     * Confirma pagamento no retorno do redirect (quando o webhook ainda não chegou).
     * Retorna true se a InfinitePay confirmar paid=true.
     */
    public boolean verificarPagamento(String orderNsu, String transactionNsu, String slug) {
        String handle = configSistemaService.getInfinitepayHandle();
        if (handle.isBlank() || orderNsu == null || transactionNsu == null || slug == null) {
            return false;
        }
        if (orderNsu.isBlank() || transactionNsu.isBlank() || slug.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("handle", handle);
            body.put("order_nsu", orderNsu);
            body.put("transaction_nsu", transactionNsu);
            body.put("slug", slug);
            String payload = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PAYMENT_CHECK_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("InfinitePay payment_check falhou ({}): {}", response.statusCode(), response.body());
                return false;
            }
            JsonNode parsed = objectMapper.readTree(response.body());
            return parsed.path("paid").asBoolean(false);
        } catch (Exception e) {
            log.warn("Falha ao consultar payment_check", e);
            return false;
        }
    }

    private String mensagemErroLink(String body) {
        try {
            JsonNode parsed = objectMapper.readTree(body);
            String code = firstText(parsed, "error");
            if ("external_checkout_not_enabled".equals(code)) {
                return "Ative o Checkout Externo no app InfinitePay (Configurações → Checkout Externo) e tente de novo.";
            }
            String msg = firstText(parsed, "message");
            if (msg != null && !msg.isBlank()) {
                return "InfinitePay: " + msg;
            }
        } catch (Exception ignored) {
            // corpo inválido — mensagem genérica abaixo
        }
        return "Não foi possível gerar o link de pagamento. Tente de novo.";
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && v.isTextual() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }

    private static int toCents(BigDecimal value) {
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}
