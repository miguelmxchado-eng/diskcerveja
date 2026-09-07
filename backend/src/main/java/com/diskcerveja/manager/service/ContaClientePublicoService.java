package com.diskcerveja.manager.service;

import com.diskcerveja.manager.domain.entity.Cliente;
import com.diskcerveja.manager.dto.ContaClienteAtualizarRequest;
import com.diskcerveja.manager.dto.ContaClienteLoginRequest;
import com.diskcerveja.manager.dto.ContaClienteRegistrarRequest;
import com.diskcerveja.manager.dto.ContaClienteResponse;
import com.diskcerveja.manager.repository.ClienteRepository;
import com.diskcerveja.manager.repository.PedidoRepository;
import com.diskcerveja.manager.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContaClientePublicoService {

    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public ContaClientePublicoService(
            ClienteRepository clienteRepository,
            PedidoRepository pedidoRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public ContaClienteResponse registrar(ContaClienteRegistrarRequest req) {
        String nome = req.nome().trim();
        String telefone = req.telefone().trim();
        String digits = soDigitos(telefone);
        if (digits.length() < 10) {
            throw new IllegalArgumentException("Informe um WhatsApp válido com DDD.");
        }
        if (req.senha() == null || req.senha().length() < 4) {
            throw new IllegalArgumentException("A senha precisa ter pelo menos 4 caracteres.");
        }

        Optional<Cliente> existente = clienteRepository.findAtivoByTelefoneDigits(digits);
        Cliente c;
        if (existente.isPresent()) {
            c = existente.get();
            if (c.temConta()) {
                throw new IllegalStateException("Esse WhatsApp já tem conta. Entre com a senha.");
            }
            // Cliente já existe (pedido anterior): exige nº de um pedido pago nesse WhatsApp.
            garantirPedidoPagoDoTelefone(req.pedidoId(), digits);
            c.setNome(nome);
            c.setTelefone(telefone);
            c.setSenhaHash(passwordEncoder.encode(req.senha()));
            aplicarEndereco(
                    c,
                    req.cep(),
                    req.logradouro(),
                    req.numero(),
                    req.complemento(),
                    req.bairro(),
                    req.cidade(),
                    req.uf());
        } else {
            c = new Cliente();
            c.setNome(nome);
            c.setTelefone(telefone);
            c.setSenhaHash(passwordEncoder.encode(req.senha()));
            c.setAtivo(true);
            aplicarEndereco(
                    c,
                    req.cep(),
                    req.logradouro(),
                    req.numero(),
                    req.complemento(),
                    req.bairro(),
                    req.cidade(),
                    req.uf());
        }
        c = clienteRepository.save(c);
        return toResponse(c, jwtTokenProvider.createClienteToken(c.getId(), digits));
    }

    private void garantirPedidoPagoDoTelefone(Long pedidoId, String digits) {
        if (pedidoId == null || pedidoId <= 0) {
            throw new IllegalArgumentException(
                    "Esse WhatsApp já fez pedido. Informe o número de um pedido pago (ex.: 42) para criar a conta.");
        }
        pedidoRepository
                .findPedidoPagoPorIdETelefone(pedidoId, digits)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Pedido #"
                                        + pedidoId
                                        + " não confere com esse WhatsApp (ou ainda não foi pago)."));
    }

    @Transactional(readOnly = true)
    public ContaClienteResponse login(ContaClienteLoginRequest req) {
        String digits = soDigitos(req.telefone());
        if (digits.length() < 10) {
            throw new IllegalArgumentException("Informe um WhatsApp válido com DDD.");
        }
        Cliente c = clienteRepository
                .findAtivoByTelefoneDigits(digits)
                .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada. Crie sua conta."));
        if (!c.temConta() || !passwordEncoder.matches(req.senha(), c.getSenhaHash())) {
            throw new IllegalArgumentException("WhatsApp ou senha incorretos.");
        }
        return toResponse(c, jwtTokenProvider.createClienteToken(c.getId(), digits));
    }

    @Transactional(readOnly = true)
    public ContaClienteResponse me(String authorizationHeader) {
        Cliente c = exigirCliente(authorizationHeader);
        String digits = soDigitos(c.getTelefone());
        return toResponse(c, jwtTokenProvider.createClienteToken(c.getId(), digits));
    }

    @Transactional
    public ContaClienteResponse atualizar(String authorizationHeader, ContaClienteAtualizarRequest req) {
        Cliente c = exigirCliente(authorizationHeader);
        if (req.nome() != null && !req.nome().isBlank()) {
            c.setNome(req.nome().trim());
        }
        aplicarEndereco(
                c,
                req.cep(),
                req.logradouro(),
                req.numero(),
                req.complemento(),
                req.bairro(),
                req.cidade(),
                req.uf());
        c = clienteRepository.save(c);
        String digits = soDigitos(c.getTelefone());
        return toResponse(c, jwtTokenProvider.createClienteToken(c.getId(), digits));
    }

    /** Resolve cliente autenticado do cardápio, se houver token válido. */
    @Transactional(readOnly = true)
    public Optional<Cliente> clienteAutenticado(String authorizationHeader) {
        try {
            return Optional.of(exigirCliente(authorizationHeader));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public Cliente exigirCliente(String authorizationHeader) {
        String token = extrairBearer(authorizationHeader);
        if (token == null) {
            throw new IllegalArgumentException("Faça login na sua conta.");
        }
        Claims claims;
        try {
            claims = jwtTokenProvider.parse(token);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Sessão expirada. Entre de novo.");
        }
        if (!jwtTokenProvider.isClienteToken(claims)) {
            throw new IllegalArgumentException("Faça login na sua conta.");
        }
        Long id = jwtTokenProvider.clienteId(claims);
        if (id == null) {
            throw new IllegalArgumentException("Sessão inválida. Entre de novo.");
        }
        return clienteRepository
                .findById(id)
                .filter(Cliente::isAtivo)
                .filter(Cliente::temConta)
                .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada. Entre de novo."));
    }

    private static void aplicarEndereco(
            Cliente c,
            String cep,
            String logradouro,
            String numero,
            String complemento,
            String bairro,
            String cidade,
            String uf) {
        if (cep != null) {
            String digits = soDigitos(cep);
            c.setCep(digits.length() == 8 ? formatCep(digits) : blankToNull(cep));
        }
        if (logradouro != null) {
            c.setLogradouro(blankToNull(logradouro.trim()));
        }
        if (numero != null) {
            c.setNumero(blankToNull(numero.trim()));
        }
        if (complemento != null) {
            c.setComplemento(blankToNull(complemento.trim()));
        }
        if (bairro != null) {
            c.setBairro(blankToNull(bairro.trim()));
        }
        if (cidade != null) {
            c.setCidade(blankToNull(cidade.trim()));
        }
        if (uf != null) {
            String u = uf.trim().toUpperCase();
            c.setUf(u.isBlank() ? null : u);
        }
        c.setEndereco(montarEndereco(c));
    }

    private static String montarEndereco(Cliente c) {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, c.getLogradouro());
        if (c.getNumero() != null && !c.getNumero().isBlank()) {
            appendPart(sb, "nº " + c.getNumero().trim());
        }
        appendPart(sb, c.getComplemento());
        appendPart(sb, c.getBairro());
        if (c.getCidade() != null && !c.getCidade().isBlank()) {
            String cidadeUf = c.getCidade().trim();
            if (c.getUf() != null && !c.getUf().isBlank()) {
                cidadeUf = cidadeUf + "/" + c.getUf().trim();
            }
            appendPart(sb, cidadeUf);
        }
        if (c.getCep() != null && !c.getCep().isBlank()) {
            appendPart(sb, "CEP " + c.getCep().trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(part.trim());
    }

    private ContaClienteResponse toResponse(Cliente c, String token) {
        return new ContaClienteResponse(
                token,
                c.getId(),
                c.getNome(),
                c.getTelefone(),
                c.getCep(),
                c.getLogradouro(),
                c.getNumero(),
                c.getComplemento(),
                c.getBairro(),
                c.getCidade(),
                c.getUf());
    }

    private static String extrairBearer(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String h = header.trim();
        if (h.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String t = h.substring(7).trim();
            return t.isEmpty() ? null : t;
        }
        return null;
    }

    private static String soDigitos(String s) {
        String d = s == null ? "" : s.replaceAll("\\D", "");
        // Remove DDI 55 se veio colado (ex.: InfinitePay / WhatsApp).
        if (d.startsWith("55") && d.length() >= 12) {
            d = d.substring(2);
        }
        return d;
    }

    private static String formatCep(String digits) {
        return digits.substring(0, 5) + "-" + digits.substring(5);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
