package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, PasswordEncoder encoder) {
        return args -> {
            criarUsuarioSeAusente(repo, encoder, "admin", Perfil.ADMIN);
            criarUsuarioSeAusente(repo, encoder, "gerente", Perfil.GERENTE);
            criarUsuarioSeAusente(repo, encoder, "atendente", Perfil.ATENDENTE);
        };
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
