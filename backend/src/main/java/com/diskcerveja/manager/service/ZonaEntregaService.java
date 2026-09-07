package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.ZonaEntrega;
import com.diskcerveja.manager.dto.FretePublicoResponse;
import com.diskcerveja.manager.dto.ZonaEntregaDto;
import com.diskcerveja.manager.repository.ZonaEntregaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
                String prefixos = normalizarListaOpcional(dto.cepPrefixos());
                String bairros = normalizarListaOpcional(dto.bairros());
                if ((prefixos == null || prefixos.isBlank()) && (bairros == null || bairros.isBlank())) {
                    throw new IllegalArgumentException(
                            "Informe CEPs ou bairros na zona \"" + dto.nome().trim() + "\".");
                }
                BigDecimal taxa = (dto.taxa() != null ? dto.taxa() : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                if (taxa.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Taxa inválida na zona \"" + dto.nome().trim() + "\".");
                }
                limpas.add(new ZonaEntregaDto(
                        null,
                        dto.nome().trim(),
                        taxa,
                        prefixos != null ? prefixos : "",
                        bairros != null ? bairros : "",
                        dto.ativo(),
                        dto.ordem() >= 0 ? dto.ordem() : i));
                i++;
            }
        }

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
            z.setCepPrefixos(dto.cepPrefixos() != null ? dto.cepPrefixos() : "");
            z.setBairros(dto.bairros() != null ? dto.bairros() : "");
            z.setAtivo(dto.ativo());
            z.setOrdem(ordem++);
            salvas.add(repository.save(z));
        }
        return salvas.stream().map(ZonaEntregaService::toDto).toList();
    }

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
    public FretePublicoResponse cotar(String cepRaw, String bairroRaw) {
        String cep = soDigitos(cepRaw);
        String bairro = bairroRaw != null ? bairroRaw.trim() : "";
        BigDecimal minimo = nz(configSistemaService.getLoja().pedidoMinimo());

        List<ZonaEntrega> ativas = repository.findByAtivoTrueOrderByOrdemAscIdAsc();
        if (ativas.isEmpty()) {
            if (cep.length() != 8 && bairro.isEmpty()) {
                return new FretePublicoResponse(
                        false, BigDecimal.ZERO, null, minimo, "Informe o CEP ou o bairro.");
            }
            BigDecimal taxa = nz(configSistemaService.getLoja().taxaEntrega());
            return new FretePublicoResponse(
                    true, taxa, "Taxa padrão", minimo, "Taxa de entrega padrão da loja.");
        }

        // Preferência: bairro (mapa). Depois CEP.
        if (!bairro.isEmpty()) {
            Optional<ZonaEntrega> porBairro =
                    ativas.stream().filter(z -> bairroCasaComZona(bairro, z.getBairros())).findFirst();
            if (porBairro.isPresent()) {
                ZonaEntrega z = porBairro.get();
                return new FretePublicoResponse(
                        true, nz(z.getTaxa()), z.getNome(), minimo, "Entrega em " + z.getNome() + ".");
            }
        }

        if (cep.length() == 8) {
            Optional<ZonaEntrega> porCep =
                    ativas.stream().filter(z -> cepCasaComZona(cep, z.getCepPrefixos())).findFirst();
            if (porCep.isPresent()) {
                ZonaEntrega z = porCep.get();
                return new FretePublicoResponse(
                        true, nz(z.getTaxa()), z.getNome(), minimo, "Entrega em " + z.getNome() + ".");
            }
        }

        if (cep.length() != 8 && bairro.isEmpty()) {
            return new FretePublicoResponse(
                    false, BigDecimal.ZERO, null, minimo, "Informe um CEP válido ou o bairro.");
        }
        return new FretePublicoResponse(
                false,
                BigDecimal.ZERO,
                null,
                minimo,
                "Este endereço está fora da área de entrega.");
    }

    /** Compat: só CEP. */
    @Transactional(readOnly = true)
    public FretePublicoResponse cotar(String cepRaw) {
        return cotar(cepRaw, null);
    }

    @Transactional(readOnly = true)
    public BigDecimal taxaObrigatoriaParaCep(String cepRaw) {
        return taxaObrigatoria(cepRaw, null);
    }

    @Transactional(readOnly = true)
    public BigDecimal taxaObrigatoria(String cepRaw, String bairro) {
        FretePublicoResponse r = cotar(cepRaw, bairro);
        if (!r.coberta()) {
            throw new IllegalArgumentException(
                    r.mensagem() != null ? r.mensagem() : "Endereço fora da área de entrega.");
        }
        return nz(r.taxa());
    }

    static boolean bairroCasaComZona(String bairro, String lista) {
        if (bairro == null || bairro.isBlank() || lista == null || lista.isBlank()) {
            return false;
        }
        String alvo = normalizarNome(bairro);
        if (alvo.isEmpty()) {
            return false;
        }
        for (String raw : lista.split("[,;\\n]")) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            String n = normalizarNome(token);
            if (n.equals(alvo) || n.contains(alvo) || alvo.contains(n)) {
                return true;
            }
        }
        return false;
    }

    static String normalizarNome(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        n = n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
        // remove prefixos comuns
        n = n.replaceFirst("^bairro ", "");
        n = n.replaceFirst("^jardim ", "jardim ");
        return n;
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

    private static String normalizarListaOpcional(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] parts = raw.split("[,;\\n]");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return String.join(", ", out);
    }

    private static ZonaEntregaDto toDto(ZonaEntrega z) {
        return new ZonaEntregaDto(
                z.getId(),
                z.getNome(),
                z.getTaxa(),
                z.getCepPrefixos() != null ? z.getCepPrefixos() : "",
                z.getBairros() != null ? z.getBairros() : "",
                z.isAtivo(),
                z.getOrdem());
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
