package com.diskcerveja.manager.bootstrap;

import com.diskcerveja.manager.domain.entity.ConfigSistema;
import com.diskcerveja.manager.domain.entity.Produto;
import com.diskcerveja.manager.domain.entity.Usuario;
import com.diskcerveja.manager.domain.enums.CategoriaProduto;
import com.diskcerveja.manager.domain.enums.PerfilUsuario;
import com.diskcerveja.manager.repository.ConfigSistemaRepository;
import com.diskcerveja.manager.repository.ProdutoRepository;
import com.diskcerveja.manager.repository.UsuarioRepository;
import java.math.BigDecimal;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final ProdutoRepository produtoRepository;
    private final ConfigSistemaRepository configSistemaRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            UsuarioRepository usuarioRepository,
            ProdutoRepository produtoRepository,
            ConfigSistemaRepository configSistemaRepository,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.produtoRepository = produtoRepository;
        this.configSistemaRepository = configSistemaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioRepository.count() == 0) {
            usuarioRepository.save(usuario("Administrador", "admin", PerfilUsuario.ADMIN));
            usuarioRepository.save(usuario("Operador Balcão", "operador", PerfilUsuario.OPERADOR));
            usuarioRepository.save(usuario("Entregador", "entregador", PerfilUsuario.ENTREGADOR));
        }
        if (configSistemaRepository.findById(ConfigSistema.CHAVE_CAIXA_OBRIGATORIO).isEmpty()) {
            ConfigSistema c = new ConfigSistema();
            c.setChave(ConfigSistema.CHAVE_CAIXA_OBRIGATORIO);
            c.setValor("true");
            configSistemaRepository.save(c);
        }
        if (produtoRepository.count() == 0) {
            produtoRepository.save(produto("Heineken 600ml", CategoriaProduto.CERVEJAS, "8.50", 120, 24));
            produtoRepository.save(produto("Skol 1L", CategoriaProduto.CERVEJAS, "6.00", 200, 30));
            produtoRepository.save(produto("Coca 2L", CategoriaProduto.REFRIGERANTES, "12.00", 40, 10));
            produtoRepository.save(produto("Batata frita", CategoriaProduto.PETISCOS, "18.00", 25, 5));
        }
    }

    private Usuario usuario(String nome, String login, PerfilUsuario perfil) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setLogin(login);
        u.setSenha(passwordEncoder.encode("admin123"));
        u.setPerfil(perfil);
        u.setAtivo(true);
        return u;
    }

    private Produto produto(String nome, CategoriaProduto cat, String preco, int est, int min) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setCategoria(cat);
        p.setPreco(new BigDecimal(preco));
        p.setCusto(null);
        p.setEstoqueAtual(est);
        p.setEstoqueMinimo(min);
        p.setAtivo(true);
        return p;
    }
}
