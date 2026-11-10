package com.umc.pediupatrao.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.DashboardController;
import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.OrdenacaoRanking;
import com.umc.pediupatrao.dto.dashboard.ParametrosDashboard;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.time.LocalDate;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = DashboardController.class)
@Import(SecurityConfig.class)
class DashboardApiTest {

    private static final String[] ENDPOINTS = {
        "/api/dashboard/resumo",
        "/api/dashboard/vendas",
        "/api/dashboard/vendas/horarios",
        "/api/dashboard/pedidos/status",
        "/api/dashboard/produtos/mais-vendidos",
        "/api/dashboard/pedidos",
        "/api/dashboard/filtros"
    };

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void semAutenticacaoRetorna401() throws Exception {
        for (String endpoint : ENDPOINTS) {
            mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(dashboardService);
    }

    @Test
    void atendenteNaoAcessaODashboard() throws Exception {
        for (String endpoint : ENDPOINTS) {
            mvc.perform(get(endpoint).with(perfil("ATENDENTE")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.mensagem", Matchers.containsString("Acesso negado")));
        }
        mvc.perform(get("/dashboard").with(perfil("ATENDENTE"))).andExpect(status().isForbidden());
        verifyNoInteractions(dashboardService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "GERENTE"})
    void adminEGerenteConsultamTodosOsEndpoints(String perfil) throws Exception {
        for (String endpoint : ENDPOINTS) {
            mvc.perform(get(endpoint).with(perfil(perfil))).andExpect(status().isOk());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "GERENTE"})
    void apiDoDashboardESomenteLeitura(String perfil) throws Exception {
        mvc.perform(post("/api/dashboard/resumo").with(perfil(perfil)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/dashboard/resumo").with(perfil(perfil)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/dashboard/pedidos").with(perfil(perfil)).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(dashboardService);
    }

    @Test
    void filtrosDaRequisicaoChegamAoServico() throws Exception {
        mvc.perform(get("/api/dashboard/vendas").with(perfil("GERENTE"))
                        .param("dataInicial", "2026-11-01")
                        .param("dataFinal", "2026-11-10")
                        .param("status", "FINALIZADO", "CANCELADO")
                        .param("tipoProduto", "Pizza")
                        .param("responsavel", "ana")
                        .param("agrupamento", "SEMANA"))
                .andExpect(status().isOk());

        ArgumentCaptor<ParametrosDashboard> captor = ArgumentCaptor.forClass(ParametrosDashboard.class);
        verify(dashboardService).vendas(captor.capture(), eq(Agrupamento.SEMANA));
        ParametrosDashboard parametros = captor.getValue();
        assertThat(parametros.dataInicial()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(parametros.dataFinal()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(parametros.status()).containsExactly(StatusPedido.FINALIZADO, StatusPedido.CANCELADO);
        assertThat(parametros.tipoProduto()).isEqualTo("Pizza");
        assertThat(parametros.responsavel()).isEqualTo("ana");
    }

    @Test
    void valoresPadraoDoRankingEDaPaginacao() throws Exception {
        mvc.perform(get("/api/dashboard/produtos/mais-vendidos").with(perfil("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/pedidos").with(perfil("ADMIN"))).andExpect(status().isOk());

        verify(dashboardService).produtosMaisVendidos(any(), eq(10), eq(OrdenacaoRanking.QUANTIDADE));
        verify(dashboardService).pedidos(any(), eq(0), eq(20));
    }

    @Test
    void parametrosEmFormatoInvalidoRetornam400() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").param("dataInicial", "10/11/2026").with(perfil("GERENTE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", Matchers.containsString("dataInicial")));
        mvc.perform(get("/api/dashboard/resumo").param("status", "ENTREGUE").with(perfil("GERENTE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", Matchers.containsString("status")));
        mvc.perform(get("/api/dashboard/vendas").param("agrupamento", "ANO").with(perfil("GERENTE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", Matchers.containsString("agrupamento")));
        mvc.perform(get("/api/dashboard/produtos/mais-vendidos").param("ordenarPor", "PRECO").with(perfil("GERENTE")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/pedidos").param("pagina", "um").with(perfil("GERENTE")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(dashboardService);
    }

    @Test
    void regraDeFiltroVioladaRetorna400ComAMensagemDoServico() throws Exception {
        when(dashboardService.resumo(any()))
                .thenThrow(new ParametroInvalidoException("A data inicial não pode ser posterior à data final."));

        mvc.perform(get("/api/dashboard/resumo").with(perfil("GERENTE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value("A data inicial não pode ser posterior à data final."));
    }

    private RequestPostProcessor perfil(String perfil) {
        return user(perfil.toLowerCase()).roles(perfil);
    }
}
