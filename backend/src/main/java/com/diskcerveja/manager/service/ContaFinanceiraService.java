package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.ContaFinanceira;
import com.diskcerveja.manager.domain.enums.FormaPagamento;
import com.diskcerveja.manager.domain.enums.StatusContaFinanceira;
import com.diskcerveja.manager.domain.enums.TipoContaFinanceira;
import com.diskcerveja.manager.dto.ContaFinanceiraDto;
import com.diskcerveja.manager.repository.ContaFinanceiraRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContaFinanceiraService {

    private final ContaFinanceiraRepository repository;

    public ContaFinanceiraService(ContaFinanceiraRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ContaFinanceiraDto> listar(TipoContaFinanceira tipo, StatusContaFinanceira status, String q) {
        if (tipo == null) {
            throw new IllegalArgumentException("Informe se é conta a pagar ou a receber.");
        }
        String busca = q == null ? "" : q.trim();
        List<ContaFinanceira> lista = repository.buscar(tipo, status, busca);
        return lista.stream()
                .sorted(Comparator
                        .comparing((ContaFinanceira c) -> c.getStatus() != StatusContaFinanceira.ABERTA)
                        .thenComparing(ContaFinanceira::getVencimento)
                        .thenComparing(ContaFinanceira::getId, Comparator.reverseOrder()))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContaFinanceiraDto buscar(Long id) {
        return toDto(buscarEntidade(id));
    }

    @Transactional
    public ContaFinanceiraDto salvar(ContaFinanceiraDto dto) {
        if (dto.tipo() == null) {
            throw new IllegalArgumentException("Informe se é conta a pagar ou a receber.");
        }
        if (dto.descricao() == null || dto.descricao().isBlank()) {
            throw new IllegalArgumentException("Informe a descrição da conta.");
        }
        if (dto.valor() == null || dto.valor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Informe um valor maior que zero.");
        }
        if (dto.vencimento() == null) {
            throw new IllegalArgumentException("Informe a data de vencimento.");
        }

        ContaFinanceira c = dto.id() == null ? new ContaFinanceira() : buscarEntidade(dto.id());
        if (c.getStatus() == StatusContaFinanceira.CANCELADA) {
            throw new IllegalArgumentException("Conta cancelada não pode ser editada.");
        }
        if (c.getStatus() == StatusContaFinanceira.QUITADA && dto.id() != null) {
            throw new IllegalArgumentException("Conta já quitada. Cancele ou lance outra.");
        }

        c.setTipo(dto.tipo());
        c.setDescricao(dto.descricao().trim());
        c.setPessoa(blankToNull(dto.pessoa()));
        c.setValor(dto.valor());
        c.setVencimento(dto.vencimento());
        c.setObservacao(blankToNull(dto.observacao()));
        if (c.getStatus() == null) {
            c.setStatus(StatusContaFinanceira.ABERTA);
        }
        return toDto(repository.save(c));
    }

    @Transactional
    public ContaFinanceiraDto quitar(Long id, FormaPagamento forma, LocalDate dataPagamento) {
        ContaFinanceira c = buscarEntidade(id);
        if (c.getStatus() != StatusContaFinanceira.ABERTA) {
            throw new IllegalArgumentException("Só dá para quitar conta em aberto.");
        }
        c.setStatus(StatusContaFinanceira.QUITADA);
        c.setDataPagamento(dataPagamento != null ? dataPagamento : LocalDate.now());
        if (forma != null && forma != FormaPagamento.MISTO) {
            c.setFormaPagamento(forma);
        } else if (forma == FormaPagamento.MISTO) {
            throw new IllegalArgumentException("Escolha Pix, dinheiro ou cartão.");
        }
        return toDto(repository.save(c));
    }

    @Transactional
    public ContaFinanceiraDto cancelar(Long id) {
        ContaFinanceira c = buscarEntidade(id);
        if (c.getStatus() == StatusContaFinanceira.CANCELADA) {
            return toDto(c);
        }
        if (c.getStatus() == StatusContaFinanceira.QUITADA) {
            throw new IllegalArgumentException("Conta quitada não pode ser cancelada.");
        }
        c.setStatus(StatusContaFinanceira.CANCELADA);
        return toDto(repository.save(c));
    }

    private ContaFinanceira buscarEntidade(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada."));
    }

    private ContaFinanceiraDto toDto(ContaFinanceira c) {
        return new ContaFinanceiraDto(
                c.getId(),
                c.getTipo(),
                c.getDescricao(),
                c.getPessoa(),
                c.getValor(),
                c.getVencimento(),
                c.getStatus(),
                c.getDataPagamento(),
                c.getFormaPagamento(),
                c.getObservacao());
    }

    private static String blankToNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim();
    }
}
