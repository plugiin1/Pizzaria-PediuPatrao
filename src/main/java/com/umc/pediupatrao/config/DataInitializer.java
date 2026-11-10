package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    CommandLineRunner initDatabase(UsuarioRepository repo, ProdutoRepository produtoRepository, PasswordEncoder encoder,
            MongoTemplate mongoTemplate) {
        return args -> {
            criarUsuarioSeAusente(repo, encoder, "admin", Perfil.ADMIN);
            criarUsuarioSeAusente(repo, encoder, "gerente", Perfil.GERENTE);
            criarUsuarioSeAusente(repo, encoder, "atendente", Perfil.ATENDENTE);
            criarProdutosIniciais(produtoRepository);
            preencherTipoDosItensAntigos(produtoRepository, mongoTemplate);
        };
    }

    private void preencherTipoDosItensAntigos(ProdutoRepository produtoRepository, MongoTemplate mongoTemplate) {
        long atualizados = 0;
        for (Produto produto : produtoRepository.findAll()) {
            if (produto.getTipo() == null) {
                continue;
            }
            Query pedidosDoProduto = new Query(Criteria.where("itens").elemMatch(
                    Criteria.where("produtoId").is(produto.getId()).and("tipoProduto").exists(false)));
            Update definirTipo = new Update().set("itens.$[item].tipoProduto", produto.getTipo())
                    .filterArray(Criteria.where("item.produtoId").is(produto.getId()).and("item.tipoProduto").exists(false));
            atualizados += mongoTemplate.updateMulti(pedidosDoProduto, definirTipo, "pedidos").getModifiedCount();
        }
        if (atualizados > 0) {
            log.info("Tipo do produto preenchido nos itens de {} pedido(s) antigo(s)", atualizados);
        }
    }

    private void criarProdutosIniciais(ProdutoRepository produtoRepository) {
        if (produtoRepository.count() > 0) {
            return;
        }
        produtoRepository.saveAll(List.of(
                produto("Pizza Calabresa", "Pizza", 52.90),
                produto("Pizza Margherita", "Pizza", 49.90),
                produto("Pizza Quatro Queijos", "Pizza", 58.90),
                produto("Pizza Portuguesa", "Pizza", 56.90),
                produto("Esfiha de Carne", "Esfiha", 7.50),
                produto("Refrigerante 2L", "Bebida", 14.00),
                produto("Suco Natural 500ml", "Bebida", 9.50)
        ));
        log.info("Produtos iniciais cadastrados");
    }

    private Produto produto(String nome, String tipo, double preco) {
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(preco);
        produto.setAtivo(true);
        return produto;
    }

    private void criarUsuarioSeAusente(UsuarioRepository repo, PasswordEncoder encoder, String username, Perfil perfil) {
        repo.findByUsername(username).ifPresentOrElse(existente -> {
            if (existente.getPerfil() == null) {
                existente.setPerfil(perfil);
                repo.save(existente);
                log.info("Perfil {} atribuído ao usuário {}", perfil, username);
            }
        }, () -> {
            Usuario usuario = new Usuario();
            usuario.setUsername(username);
            usuario.setPassword(encoder.encode("teste123"));
            usuario.setPerfil(perfil);
            repo.save(usuario);
            log.info("Usuário {} criado com perfil {}", username, perfil);
        });
    }
}
