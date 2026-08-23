package com.diskcerveja.manager.web;

import com.diskcerveja.manager.domain.entity.Cliente;
import com.diskcerveja.manager.dto.ClienteDto;
import com.diskcerveja.manager.service.ClienteService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clientes")
@PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public List<ClienteDto> listar(@RequestParam(value = "q", required = false) String q) {
        List<Cliente> lista = (q == null || q.isBlank()) ? clienteService.listarAtivos() : clienteService.buscar(q);
        return lista.stream().map(ClienteController::toDto).toList();
    }

    @GetMapping("/{id}")
    public ClienteDto buscar(@PathVariable Long id) {
        return toDto(clienteService.buscarPorId(id));
    }

    @PostMapping
    public ClienteDto criar(@RequestBody @Valid ClienteDto dto) {
        return toDto(clienteService.salvar(new ClienteDto(
                null, dto.nome(), dto.telefone(), dto.endereco(), dto.observacao(), true)));
    }

    @PutMapping("/{id}")
    public ClienteDto atualizar(@PathVariable Long id, @RequestBody @Valid ClienteDto dto) {
        return toDto(clienteService.salvar(new ClienteDto(
                id, dto.nome(), dto.telefone(), dto.endereco(), dto.observacao(), dto.ativo())));
    }

    private static ClienteDto toDto(Cliente c) {
        return new ClienteDto(c.getId(), c.getNome(), c.getTelefone(), c.getEndereco(), c.getObservacao(), c.isAtivo());
    }
}
