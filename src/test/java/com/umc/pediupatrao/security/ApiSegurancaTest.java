package com.umc.pediupatrao.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.AuditoriaController;
import com.umc.pediupatrao.controller.ClienteController;
import com.umc.pediupatrao.controller.PedidoController;
import com.umc.pediupatrao.controller.ProdutoController;
import com.umc.pediupatrao.controller.UsuarioController;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = {PedidoController.class, ClienteController.class, UsuarioController.class,
    AuditoriaController.class, ProdutoController.class})
@Import(SecurityConfig.class)
class ApiSegurancaTest {

    private static final String DESCONTO = "{\"percentual\": 10}";
    private static final String CANCELAMENTO = "{\"justificativa\": \"Cliente desistiu do pedido\"}";
    private static final String NOVO_PEDIDO = "{\"clienteId\": \"c1\", \"itens\": [{\"produtoId\": \"p1\", \"quantidade\": 1}]}";
    private static final String NOVO_CLIENTE = "{\"nome\": \"Maria\", \"telefone\": \"(11) 98888-1111\"}";
    private static final String NOVO_USUARIO = "{\"username\": \"invasor\", \"password\": \"123456\", \"perfil\": \"ADMIN\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoService pedidoService;

    @MockitoBean
    private ClienteService clienteService;

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private AuditoriaService auditoriaService;

    @MockitoBean
    private ProdutoService produtoService;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void requisicaoSemAutenticacaoRetorna401() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auditoria")).andExpect(status().isUnauthorized());
    }

    @Test
    void paginaSemAutenticacaoRedirecionaParaLogin() throws Exception {
        mvc.perform(get("/pedidos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ATENDENTE"})
    void somenteGerenteAplicaDesconto(String perfil) throws Exception {
        mvc.perform(json(post("/api/pedidos/p1/desconto"), DESCONTO).with(perfil(perfil)).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem", Matchers.containsString("Acesso negado")));
        verifyNoInteractions(pedidoService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ATENDENTE"})
    void somenteGerenteCancelaPedido(String perfil) throws Exception {
        mvc.perform(json(post("/api/pedidos/p1/cancelamento"), CANCELAMENTO).with(perfil(perfil)).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedidoService);
    }

    @Test
    void gerenteAplicaDescontoECancela() throws Exception {
        when(pedidoService.aplicarDesconto(any(), any(), any())).thenReturn(new Pedido());
        when(pedidoService.cancelar(any(), any(), any())).thenReturn(new Pedido());

        mvc.perform(json(post("/api/pedidos/p1/desconto"), DESCONTO).with(perfil("GERENTE")).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/pedidos/p1/cancelamento"), CANCELAMENTO).with(perfil("GERENTE")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void descontoAcimaDoLimiteRetorna400() throws Exception {
        mvc.perform(json(post("/api/pedidos/p1/desconto"), "{\"percentual\": 25}").with(perfil("GERENTE")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", Matchers.containsString("20%")));
        verifyNoInteractions(pedidoService);
    }

    @Test
    void cancelamentoSemJustificativaRetorna400() throws Exception {
        mvc.perform(json(post("/api/pedidos/p1/cancelamento"), "{}").with(perfil("GERENTE")).with(csrf()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(pedidoService);
    }

    @Test
    void operacaoSemTokenCsrfERecusada() throws Exception {
        mvc.perform(json(post("/api/pedidos/p1/desconto"), DESCONTO).with(perfil("GERENTE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem", Matchers.containsString("CSRF")));
        verifyNoInteractions(pedidoService);
    }

    @Test
    void adminConsultaPedidosMasNaoRegistraNemAlteraStatus() throws Exception {
        mvc.perform(get("/api/pedidos").with(perfil("ADMIN"))).andExpect(status().isOk());
        mvc.perform(json(post("/api/pedidos"), NOVO_PEDIDO).with(perfil("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/p1/status").param("status", "EM_PREPARACAO").with(perfil("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void atendenteRegistraPedidoEAvancaStatus() throws Exception {
        when(pedidoService.criarPedido(any(), any())).thenReturn(new Pedido());
        when(pedidoService.atualizarStatus(any(), any(), any())).thenReturn(new Pedido());

        mvc.perform(json(post("/api/pedidos"), NOVO_PEDIDO).with(perfil("ATENDENTE")).with(csrf()))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/pedidos/p1/status").param("status", "EM_PREPARACAO").with(perfil("ATENDENTE")).with(csrf()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GERENTE", "ATENDENTE"})
    void somenteAdminAdministraUsuarios(String perfil) throws Exception {
        mvc.perform(get("/api/usuarios").with(perfil(perfil))).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/usuarios"), NOVO_USUARIO).with(perfil(perfil)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(json(put("/api/usuarios/u1"), NOVO_USUARIO).with(perfil(perfil)).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(usuarioService);
    }

    @Test
    void adminListaUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios").with(perfil("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void auditoriaConsultadaApenasPorAdminEGerente() throws Exception {
        mvc.perform(get("/api/auditoria").with(perfil("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/auditoria").with(perfil("GERENTE"))).andExpect(status().isOk());
        mvc.perform(get("/api/auditoria").with(perfil("ATENDENTE"))).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "GERENTE"})
    void registrosDeAuditoriaNaoPodemSerAlteradosNemExcluidos(String perfil) throws Exception {
        mvc.perform(delete("/api/auditoria").with(perfil(perfil)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/auditoria/r1").with(perfil(perfil)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(json(put("/api/auditoria/r1"), "{}").with(perfil(perfil)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/auditoria"), "{}").with(perfil(perfil)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void permissoesDeClientesPorPerfil() throws Exception {
        when(clienteService.salvar(any())).thenReturn(new Cliente());

        mvc.perform(get("/api/clientes").with(perfil("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/clientes").with(perfil("GERENTE"))).andExpect(status().isOk());
        mvc.perform(json(post("/api/clientes"), NOVO_CLIENTE).with(perfil("GERENTE")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/clientes"), NOVO_CLIENTE).with(perfil("ATENDENTE")).with(csrf()))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/clientes/c1").with(perfil("ATENDENTE")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/clientes/c1").with(perfil("GERENTE")).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void somenteGerenteGerenciaProdutos() throws Exception {
        String produto = "{\"nome\": \"Pizza\", \"tipo\": \"Pizza\", \"preco\": 10}";
        mvc.perform(get("/api/produtos").with(perfil("ATENDENTE"))).andExpect(status().isOk());
        mvc.perform(json(post("/api/produtos"), produto).with(perfil("ATENDENTE")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/produtos"), produto).with(perfil("ADMIN")).with(csrf())).andExpect(status().isForbidden());
    }

    private RequestPostProcessor perfil(String perfil) {
        return user(perfil.toLowerCase()).roles(perfil);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String corpo) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }
}
