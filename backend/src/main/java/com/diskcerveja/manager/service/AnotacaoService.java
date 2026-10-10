package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Anotacao;
import com.diskcerveja.manager.dto.AnotacaoDto;
import com.diskcerveja.manager.repository.AnotacaoRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnotacaoService {

    private static final Set<String> CORES = Set.of("AMARELO", "VERDE", "AZUL", "ROSA", "BRANCO");

    private final AnotacaoRepository repository;

    public AnotacaoService(AnotacaoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AnotacaoDto> listar(boolean concluidas, String q) {
        String busca = q == null ? "" : q.trim();
        return repository.buscar(concluidas, busca).stream().map(AnotacaoService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> contagem() {
        return Map.of("abertas", repository.countByConcluida(false), "concluidas", repository.countByConcluida(true));
    }

    @Transactional
    public AnotacaoDto criar(AnotacaoDto dto, String autorNome) {
        Anotacao a = new Anotacao();
        a.setAutorNome(autorNome);
        aplicar(a, dto);
        return toDto(repository.save(a));
    }

    @Transactional
    public AnotacaoDto atualizar(Long id, AnotacaoDto dto) {
        Anotacao a = buscarEntidade(id);
        aplicar(a, dto);
        return toDto(repository.save(a));
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscarEntidade(id));
    }

    private Anotacao buscarEntidade(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Anotação não encontrada."));
    }

    private static void aplicar(Anotacao a, AnotacaoDto dto) {
        String texto = dto.texto() == null ? "" : dto.texto().strip();
        if (texto.isEmpty()) {
            throw new IllegalArgumentException("Escreva alguma coisa na anotação.");
        }
        String titulo = dto.titulo() == null ? null : dto.titulo().strip();
        a.setTitulo(titulo == null || titulo.isEmpty() ? null : titulo);
        a.setTexto(texto);
        if (dto.cor() != null && !dto.cor().isBlank()) {
            String cor = dto.cor().trim().toUpperCase(Locale.ROOT);
            if (!CORES.contains(cor)) {
                throw new IllegalArgumentException("Cor inválida.");
            }
            a.setCor(cor);
        }
        if (dto.fixada() != null) {
            a.setFixada(dto.fixada());
        }
        if (dto.concluida() != null) {
            a.setConcluida(dto.concluida());
        }
    }

    private static AnotacaoDto toDto(Anotacao a) {
        return new AnotacaoDto(
                a.getId(),
                a.getTitulo(),
                a.getTexto(),
                a.getCor(),
                a.isFixada(),
                a.isConcluida(),
                a.getAutorNome(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
