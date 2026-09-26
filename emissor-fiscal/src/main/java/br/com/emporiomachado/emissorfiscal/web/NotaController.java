package br.com.emporiomachado.emissorfiscal.web;

import br.com.emporiomachado.emissorfiscal.domain.NotaFiscal;
import br.com.emporiomachado.emissorfiscal.domain.NotaFiscalRepository;
import br.com.emporiomachado.emissorfiscal.dto.CancelarNotaRequest;
import br.com.emporiomachado.emissorfiscal.dto.EmitirNotaRequest;
import br.com.emporiomachado.emissorfiscal.dto.NotaFiscalResponse;
import br.com.emporiomachado.emissorfiscal.exception.NotaNaoEncontradaException;
import br.com.emporiomachado.emissorfiscal.service.CancelamentoService;
import br.com.emporiomachado.emissorfiscal.service.NfceEmissaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notas")
public class NotaController {

    private final NfceEmissaoService emissaoService;
    private final CancelamentoService cancelamentoService;
    private final NotaFiscalRepository repository;

    public NotaController(
            NfceEmissaoService emissaoService,
            CancelamentoService cancelamentoService,
            NotaFiscalRepository repository
    ) {
        this.emissaoService = emissaoService;
        this.cancelamentoService = cancelamentoService;
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<NotaFiscalResponse> emitir(@Valid @RequestBody EmitirNotaRequest request) {
        NotaFiscal nota = emissaoService.emitir(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(NotaFiscalResponse.from(nota));
    }

    @GetMapping("/{id}")
    public NotaFiscalResponse consultar(@PathVariable UUID id) {
        return repository.findById(id)
                .map(NotaFiscalResponse::from)
                .orElseThrow(() -> new NotaNaoEncontradaException(id));
    }

    @PostMapping("/{id}/cancelar")
    public NotaFiscalResponse cancelar(
            @PathVariable UUID id,
            @Valid @RequestBody CancelarNotaRequest request
    ) {
        return NotaFiscalResponse.from(cancelamentoService.cancelar(id, request.justificativa()));
    }
}
