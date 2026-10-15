package com.umc.pediupatrao.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.umc.pediupatrao.dto.FiltroAuditoria;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(SegurancaMetodosTest.Configuracao.class)
class SegurancaMetodosTest {

    @Configuration
    @EnableMethodSecurity
    static class Configuracao {

        @Bean
        AuditoriaService auditoriaService() {
            return new AuditoriaService(mock(AuditoriaRepository.class), mock(MongoTemplate.class));
        }

        @Bean
        PedidoService pedidoService(AuditoriaService auditoriaService) {
            return new PedidoService(mock(PedidoRepository.class), mock(ClienteRepository.class),
                    mock(ProdutoRepository.class), auditoriaService);
        }

        @Bean
        UsuarioService usuarioService(AuditoriaService auditoriaService) {
            return new UsuarioService(mock(UsuarioRepository.class), mock(PasswordEncoder.class), auditoriaService);
        }
    }

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private AuditoriaService auditoriaService;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoAplicaDescontoNemCancelaMesmoChamandoOServico() {
        assertThatThrownBy(() -> pedidoService.aplicarDesconto("p1", BigDecimal.TEN, "admin"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> pedidoService.cancelar("p1", "Justificativa qualquer", "admin"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> pedidoService.atualizarStatus("p1", StatusPedido.EM_PREPARACAO, "admin"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "atendente", roles = "ATENDENTE")
    void atendenteNaoAplicaDescontoNemConsultaAuditoria() {
        assertThatThrownBy(() -> pedidoService.aplicarDesconto("p1", BigDecimal.TEN, "atendente"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> auditoriaService.consultar(new FiltroAuditoria(null, null, null, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void gerenteNaoAdministraUsuarios() {
        assertThatThrownBy(() -> usuarioService.listarTodos()).isInstanceOf(AccessDeniedException.class);
    }
}
