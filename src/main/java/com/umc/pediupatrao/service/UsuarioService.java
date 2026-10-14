package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {

    private static final Pattern USERNAME_VALIDO = Pattern.compile("^[a-zA-Z0-9._-]{3,30}$");
    private static final int TAMANHO_MINIMO_SENHA = 6;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario criarUsuario(Usuario dados) {
        String username = validarUsername(dados.getUsername());
        if (usuarioRepository.existsByUsername(username)) {
            throw new RegraNegocioException("Já existe um usuário com o username informado.");
        }
        validarSenha(dados.getPassword());
        validarPerfil(dados.getPerfil());

        Usuario novo = new Usuario();
        novo.setUsername(username);
        novo.setPassword(passwordEncoder.encode(dados.getPassword()));
        novo.setPerfil(dados.getPerfil());
        return usuarioRepository.save(novo);
    }

    public Usuario atualizarUsuario(String id, Usuario dados, String usernameLogado) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        String username = validarUsername(dados.getUsername());
        if (!username.equals(existente.getUsername()) && usuarioRepository.existsByUsername(username)) {
            throw new RegraNegocioException("Já existe um usuário com o username informado.");
        }
        validarPerfil(dados.getPerfil());

        boolean proprioUsuario = existente.getUsername().equals(usernameLogado);
        if (proprioUsuario && existente.getPerfil() != dados.getPerfil()) {
            throw new RegraNegocioException("Não é permitido alterar o próprio perfil de acesso.");
        }
        if (existente.getPerfil() == Perfil.ADMIN && dados.getPerfil() != Perfil.ADMIN) {
            garantirOutroAdministrador();
        }

        existente.setUsername(username);
        existente.setPerfil(dados.getPerfil());
        if (dados.getPassword() != null && !dados.getPassword().isBlank()) {
            validarSenha(dados.getPassword());
            existente.setPassword(passwordEncoder.encode(dados.getPassword()));
        }
        return usuarioRepository.save(existente);
    }

    public void deletarUsuario(String id, String usernameLogado) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        if (existente.getUsername().equals(usernameLogado)) {
            throw new RegraNegocioException("Não é permitido excluir o próprio usuário.");
        }
        if (existente.getPerfil() == Perfil.ADMIN) {
            garantirOutroAdministrador();
        }
        usuarioRepository.delete(existente);
    }

    private void garantirOutroAdministrador() {
        if (usuarioRepository.countByPerfil(Perfil.ADMIN) <= 1) {
            throw new RegraNegocioException("O sistema deve manter ao menos um administrador.");
        }
    }

    private String validarUsername(String username) {
        if (username == null || !USERNAME_VALIDO.matcher(username.trim()).matches()) {
            throw new RegraNegocioException("Username inválido: use de 3 a 30 caracteres (letras, números, ponto, hífen ou sublinhado).");
        }
        return username.trim();
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new RegraNegocioException("A senha deve ter no mínimo " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
    }

    private void validarPerfil(Perfil perfil) {
        if (perfil == null) {
            throw new RegraNegocioException("Informe um perfil de acesso válido (ADMIN, GERENTE ou ATENDENTE).");
        }
    }
}
