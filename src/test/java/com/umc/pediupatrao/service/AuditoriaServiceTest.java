package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.FiltroAuditoria;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private AuditoriaRepository auditoriaRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private AuditoriaService auditoriaService;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registraUsuarioEPerfilAutenticadosComDataHora() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "atendente01", null, List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE"))));
        when(auditoriaRepository.insert(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, AuditoriaService.ENTIDADE_CLIENTE, "c1",
                List.of(new AlteracaoCampo("logradouro", "Rua das Flores", "Av. Brasil")), null);

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(auditoriaRepository).insert(captor.capture());
        RegistroAuditoria registro = captor.getValue();
        assertThat(registro.getUsuario()).isEqualTo("atendente01");
        assertThat(registro.getPerfil()).isEqualTo("ATENDENTE");
        assertThat(registro.getDataHora()).isNotNull();
        assertThat(registro.getOperacao()).isEqualTo(TipoOperacao.ALTERACAO_CLIENTE);
        assertThat(registro.getEntidadeId()).isEqualTo("c1");
        assertThat(registro.getAlteracoes())
                .extracting(AlteracaoCampo::getCampo, AlteracaoCampo::getValorAnterior, AlteracaoCampo::getValorNovo)
                .containsExactly(tuple("logradouro", "Rua das Flores", "Av. Brasil"));
    }

    @Test
    void consultaOcultaParteDoTelefone() {
        RegistroAuditoria armazenado = new RegistroAuditoria("r1", "atendente", "ATENDENTE", LocalDateTime.now(),
                TipoOperacao.ALTERACAO_CLIENTE, AuditoriaService.ENTIDADE_CLIENTE, "c1", List.of(
                        new AlteracaoCampo("telefone", "(11) 98888-1111", "(11) 97777-2222"),
                        new AlteracaoCampo("logradouro", "Rua das Flores", "Av. Brasil")), null);
        when(mongoTemplate.find(any(Query.class), eq(RegistroAuditoria.class))).thenReturn(List.of(armazenado));

        List<RegistroAuditoria> resultado = auditoriaService.consultar(
                new FiltroAuditoria("CLIENTE", "c1", null, null, null, null));

        assertThat(resultado.get(0).getAlteracoes())
                .extracting(AlteracaoCampo::getCampo, AlteracaoCampo::getValorAnterior, AlteracaoCampo::getValorNovo)
                .containsExactly(
                        tuple("telefone", "*******1111", "*******2222"),
                        tuple("logradouro", "Rua das Flores", "Av. Brasil"));
    }

    @Test
    void mascaraTelefoneMantendoApenasOsQuatroUltimosDigitos() {
        assertThat(AuditoriaService.mascararTelefone("(11) 98888-1234")).isEqualTo("*******1234");
        assertThat(AuditoriaService.mascararTelefone("123")).isEqualTo("****");
        assertThat(AuditoriaService.mascararTelefone(null)).isNull();
    }
}
