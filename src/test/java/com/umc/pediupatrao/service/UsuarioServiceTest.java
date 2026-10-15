package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private UsuarioService usuarioService;

    @Captor
    private ArgumentCaptor<List<AlteracaoCampo>> alteracoesCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacao -> {
            Usuario usuario = invocacao.getArgument(0);
            if (usuario.getId() == null) {
                usuario.setId("novo-id");
            }
            return usuario;
        });
        lenient().when(passwordEncoder.encode(any())).thenReturn("$2a$10$hash");
    }

    @Test
    void criarUsuarioArmazenaSenhaCriptografadaEIgnoraIdInformado() {
        Usuario dados = usuario("tentativa-de-sobrescrever", "gerente02", Perfil.GERENTE);
        dados.setPassword("segredo123");

        Usuario criado = usuarioService.criarUsuario(dados);

        assertThat(criado.getId()).isEqualTo("novo-id");
        assertThat(criado.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(criado.getPerfil()).isEqualTo(Perfil.GERENTE);
    }

    @Test
    void criarUsuarioExigePerfil() {
        Usuario dados = usuario(null, "semperfil", null);
        dados.setPassword("segredo123");

        assertThatThrownBy(() -> usuarioService.criarUsuario(dados))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("perfil");
    }

    @Test
    void administradorNaoPodeAlterarOProprioPerfil() {
        when(usuarioRepository.findById("a1")).thenReturn(Optional.of(usuario("a1", "admin", Perfil.ADMIN)));

        assertThatThrownBy(() -> usuarioService.atualizarUsuario("a1", usuario(null, "admin", Perfil.ATENDENTE), "admin"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("próprio perfil");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void naoPermiteRemoverOUltimoAdministrador() {
        when(usuarioRepository.findById("a2")).thenReturn(Optional.of(usuario("a2", "admin2", Perfil.ADMIN)));
        when(usuarioRepository.countByPerfil(Perfil.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.atualizarUsuario("a2", usuario(null, "admin2", Perfil.GERENTE), "admin"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("administrador");
    }

    @Test
    void naoPermiteExcluirOProprioUsuario() {
        when(usuarioRepository.findById("a1")).thenReturn(Optional.of(usuario("a1", "admin", Perfil.ADMIN)));

        assertThatThrownBy(() -> usuarioService.deletarUsuario("a1", "admin"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("próprio usuário");
        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    void auditoriaDeAlteracaoNaoRegistraSenha() {
        when(usuarioRepository.findById("u1")).thenReturn(Optional.of(usuario("u1", "atendente02", Perfil.ATENDENTE)));
        Usuario dados = usuario(null, "atendente02", Perfil.GERENTE);
        dados.setPassword("novaSenhaSecreta");

        usuarioService.atualizarUsuario("u1", dados, "admin");

        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_USUARIO), eq(AuditoriaService.ENTIDADE_USUARIO),
                eq("u1"), alteracoesCaptor.capture(), isNull());
        assertThat(alteracoesCaptor.getValue())
                .extracting(AlteracaoCampo::getCampo, AlteracaoCampo::getValorAnterior, AlteracaoCampo::getValorNovo)
                .containsExactly(tuple("perfil", "ATENDENTE", "GERENTE"), tuple("senha", null, "[redefinida]"));
        assertThat(alteracoesCaptor.getValue().toString()).doesNotContain("novaSenhaSecreta", "$2a$10$hash");
    }

    private Usuario usuario(String id, String username, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setUsername(username);
        usuario.setPerfil(perfil);
        usuario.setPassword("$2a$10$hashAntigo");
        return usuario;
    }
}
