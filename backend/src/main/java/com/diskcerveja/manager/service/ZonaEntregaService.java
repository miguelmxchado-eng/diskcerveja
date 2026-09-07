package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.ZonaEntrega;
import com.diskcerveja.manager.dto.FretePublicoResponse;
import com.diskcerveja.manager.dto.ZonaEntregaDto;
import com.diskcerveja.manager.repository.ZonaEntregaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ZonaEntregaService {

    private final ZonaEntregaRepository repository;
    private final ConfigSistemaService configSistemaService;

    public ZonaEntregaService(ZonaEntregaRepository repository, ConfigSistemaService configSistemaService) {
        this.repository = repository;
        this.configSistemaService = configSistemaService;
    }

    @Transactional(readOnly = true)
    public List<ZonaEntregaDto> listar() {
        return repository.findAllByOrderByOrdemAscIdAsc().stream().map(ZonaEntregaService::toDto).toList();
    }

    @Transactional
    public List<ZonaEntregaDto> substituirTodas(List<ZonaEntregaDto> zonas) {
        List<ZonaEntregaDto> limpas = new ArrayList<>();
        if (zonas != null) {
            int i = 0;
            for (ZonaEntregaDto dto : zonas) {
                if (dto == null || dto.nome() == null || dto.nome().isBlank()) {
                    continue;
                }
                if (dto.cepPrefixos() == null || dto.cepPrefixos().isBlank()) {
                    throw new IllegalArgumentException("Informe os CEPs da zona \"" + dto.nome().trim() + "\".");
                }
                String prefixos = normalizarListaPrefixos(dto.cepPrefixos());
                BigDecimal taxa = (dto.taxa() != null ? dto.taxa() : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                if (taxa.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Taxa inválida na zona \"" + dto.nome().trim() + "\".");
                }
                limpas.add(new ZonaEntregaDto(
                        null,
                        dto.nome().trim(),
                        taxa,
                        prefixos,
                        dto.ativo(),
                        dto.ordem() >= 0 ? dto.ordem() : i));
                i++;
            }
        }

        // Valida tudo antes de apagar — evita perder zonas se algo falhar no meio.
        repository.deleteAllInBatch();
        if (limpas.isEmpty()) {
            return List.of();
        }

        List<ZonaEntrega> salvas = new ArrayList<>();
        int ordem = 0;
        for (ZonaEntregaDto dto : limpas) {
            ZonaEntrega z = new ZonaEntrega();
            z.setNome(dto.nome());
            z.setTaxa(dto.taxa());
            z.setCepPrefixos(dto.cepPrefixos());
            z.setAtivo(dto.ativo());
            z.setOrdem(ordem++);
            salvas.add(repository.save(z));
        }
        return salvas.stream().map(ZonaEntregaService::toDto).toList();
    }

    /**
     * Taxa exibida no cardápio ("a partir de"): menor zona ativa, ou taxa fixa da loja.
     */
    @Transactional(readOnly = true)
    public BigDecimal taxaExibidaCardapio() {
        List<ZonaEntrega> ativas = repository.findByAtivoTrueOrderByOrdemAscIdAsc();
        if (ativas.isEmpty()) {
            return nz(configSistemaService.getLoja().taxaEntrega());
        }
        return ativas.stream()
                .map(ZonaEntrega::getTaxa)
                .map(ZonaEntregaService::nz)
                .min(BigDecimal::compareTo)
                .orElse(nz(configSistemaService.getLoja().taxaEntrega()));
    }

    @Transactional(readOnly = true)
    public FretePublicoResponse cotar(String cepRaw) {
        String cep = soDigitos(cepRaw);
        BigDecimal minimo = nz(configSistemaService.getLoja().pedidoMinimo());
        if (cep.length() != 8) {
            return new FretePublicoResponse(
                    false, BigDecimal.ZERO, null, minimo, "Informe um CEP válido com 8 dígitos.");
        }
        List<ZonaEntrega> ativas = repository.findByAtivoTrueOrderByOrdemAscIdAsc();
        if (ativas.isEmpty()) {
            BigDecimal taxa = nz(configSistemaService.getLoja().taxaEntrega());
            return new FretePublicoResponse(
                    true, taxa, "Taxa padrão", minimo, "Taxa de entrega para este CEP.");
        }
        Optional<ZonaEntrega> match =
                ativas.stream().filter(z -> cepCasaComZona(cep, z.getCepPrefixos())).findFirst();
        if (match.isEmpty()) {
            return new FretePublicoResponse(
                    false, BigDecimal.ZERO, null, minimo, "Este CEP está fora da área de entrega.");
        }
        ZonaEntrega z = match.get();
        return new FretePublicoResponse(
                true, nz(z.getTaxa()), z.getNome(), minimo, "Entrega em " + z.getNome() + ".");
    }

    /** Usado na criação do pedido — lança se fora da área. */
    @Transactional(readOnly = true)
    public BigDecimal taxaObrigatoriaParaCep(String cepRaw) {
        FretePublicoResponse r = cotar(cepRaw);
        if (!r.coberta()) {
            throw new IllegalArgumentException(
                    r.mensagem() != null ? r.mensagem() : "CEP fora da área de entrega.");
        }
        return nz(r.taxa());
    }

    static boolean cepCasaComZona(String cep8, String lista) {
        if (lista == null || lista.isBlank() || cep8 == null || cep8.length() != 8) {
            return false;
        }
        long cepNum;
        try {
            cepNum = Long.parseLong(cep8);
        } catch (NumberFormatException e) {
            return false;
        }
        for (String raw : lista.split("[,;\\n]")) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            // CEP completo formatado (74000-000) → match exato.
            if (token.matches("\\d{5}-\\d{3}")) {
                if (cep8.equals(soDigitos(token))) {
                    return true;
                }
                continue;
            }
            if (token.contains("-")) {
                String[] parts = token.split("-", 2);
                if (parts.length != 2) {
                    continue;
                }
                String a = soDigitos(parts[0]);
                String b = soDigitos(parts[1]);
                if (a.isEmpty() || b.isEmpty()) {
                    continue;
                }
                long ini = Long.parseLong(expandCepBound(a, true));
                long fim = Long.parseLong(expandCepBound(b, false));
                if (ini > fim) {
                    long tmp = ini;
                    ini = fim;
                    fim = tmp;
                }
                if (cepNum >= ini && cepNum <= fim) {
                    return true;
                }
            } else {
                String p = soDigitos(token);
                if (p.length() >= 3 && p.length() <= 8 && cep8.startsWith(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Completa prefixo para limite inferior/superior de 8 dígitos. */
    static String expandCepBound(String digits, boolean inicio) {
        if (digits == null || digits.isEmpty()) {
            return inicio ? "00000000" : "99999999";
        }
        if (digits.length() >= 8) {
            return digits.substring(0, 8);
        }
        StringBuilder sb = new StringBuilder(digits);
        while (sb.length() < 8) {
            sb.append(inicio ? '0' : '9');
        }
        return sb.toString();
    }

    private static String normalizarListaPrefixos(String raw) {
        String[] parts = raw.split("[,;\\n]");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um CEP ou faixa.");
        }
        return String.join(", ", out);
    }

    private static ZonaEntregaDto toDto(ZonaEntrega z) {
        return new ZonaEntregaDto(z.getId(), z.getNome(), z.getTaxa(), z.getCepPrefixos(), z.isAtivo(), z.getOrdem());
    }

    private static String soDigitos(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\D", "");
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
