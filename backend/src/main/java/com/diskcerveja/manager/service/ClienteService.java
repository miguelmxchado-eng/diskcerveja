package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Cliente;
import com.diskcerveja.manager.dto.ClienteDto;
import com.diskcerveja.manager.repository.ClienteRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Transactional(readOnly = true)
    public List<Cliente> listarAtivos() {
        return clienteRepository.findTop200ByAtivoTrueOrderByNomeAsc();
    }

    @Transactional(readOnly = true)
    public List<Cliente> buscar(String termo) {
        if (termo == null || termo.isBlank()) {
            return listarAtivos();
        }
        String q = termo.trim();
        String digits = q.replaceAll("\\D+", "");
        return clienteRepository.buscarAtivos(q, digits.isBlank() ? q : digits).stream().limit(50).toList();
    }

    @Transactional(readOnly = true)
    public Cliente buscarPorId(Long id) {
        return clienteRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado."));
    }

    @Transactional
    public Cliente salvar(ClienteDto dto) {
        Cliente c;
        if (dto.id() != null) {
            c = buscarPorId(dto.id());
        } else {
            c = new Cliente();
        }
        String nome = dto.nome() == null ? "" : dto.nome().trim();
        if (nome.isBlank()) {
            throw new IllegalArgumentException("Informe o nome do cliente.");
        }
        c.setNome(nome);
        c.setTelefone(blankToNull(dto.telefone()));
        c.setEndereco(blankToNull(dto.endereco()));
        c.setObservacao(blankToNull(dto.observacao()));
        if (dto.ativo() != null) {
            c.setAtivo(dto.ativo());
        } else if (dto.id() == null) {
            c.setAtivo(true);
        }
        return clienteRepository.save(c);
    }

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
