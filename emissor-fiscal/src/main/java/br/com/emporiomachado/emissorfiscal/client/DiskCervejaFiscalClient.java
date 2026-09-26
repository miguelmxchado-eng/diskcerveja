package br.com.emporiomachado.emissorfiscal.client;

import br.com.emporiomachado.emissorfiscal.dto.CancelarNotaRequest;
import br.com.emporiomachado.emissorfiscal.dto.EmitirNotaRequest;
import br.com.emporiomachado.emissorfiscal.dto.NotaFiscalResponse;

import java.util.UUID;

/**
 * Stub de cliente HTTP para o Disk Cerveja (Angular/Spring principal) plugar depois.
 * <p>
 * Uso futuro sugerido no backend do PDV:
 * <pre>
 *   // RestClient / WebClient apontando para http://localhost:8090
 *   NotaFiscalResponse resp = fiscalClient.emitir(payloadDaVenda);
 *   // persistir resp.id() / chaveAcesso no pedido
 * </pre>
 * Não há implementação Feign/WebClient nesta v1 — apenas o contrato.
 */
public interface DiskCervejaFiscalClient {

    /** POST /api/notas — emite NFC-e a partir da venda do PDV. */
    NotaFiscalResponse emitir(EmitirNotaRequest venda);

    /** GET /api/notas/{id} — consulta status / protocolo. */
    NotaFiscalResponse consultar(UUID id);

    /** POST /api/notas/{id}/cancelar — cancela NFC-e autorizada (prazo SEFAZ). */
    NotaFiscalResponse cancelar(UUID id, CancelarNotaRequest request);
}
