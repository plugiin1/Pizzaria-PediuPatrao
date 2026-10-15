package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private PedidoService pedidoService;

    @Captor
    private ArgumentCaptor<List<AlteracaoCampo>> alteracoesCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocacao -> {
            Pedido pedido = invocacao.getArgument(0);
            if (pedido.getId() == null) {
                pedido.setId("pedido-1");
            }
            return pedido;
        });
    }

    @Test
    void criarPedidoCalculaValoresComPrecosDoCadastroERegistraEntrada() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente()));
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(produto("p1", "Pizza Calabresa", 52.90, true)));
        when(produtoRepository.findById("p2")).thenReturn(Optional.of(produto("p2", "Refrigerante 2L", 14.00, true)));
        NovoPedidoRequest request = new NovoPedidoRequest("c1", List.of(
                new ItemPedidoRequest("p1", 2),
                new ItemPedidoRequest("p2", 1),
                new ItemPedidoRequest("p1", 1)), "sem cebola");

        Pedido pedido = pedidoService.criarPedido(request, "atendente");

        assertThat(pedido.getItens()).extracting(ItemPedido::getNomeProduto, ItemPedido::getQuantidade)
                .containsExactly(tuple("Pizza Calabresa", 3), tuple("Refrigerante 2L", 1));
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("172.70");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.RECEBIDO);
        assertThat(pedido.getResponsavelEntrada()).isEqualTo("atendente");
        assertThat(pedido.getDataHoraEntrada()).isNotNull();
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_PEDIDO), eq(AuditoriaService.ENTIDADE_PEDIDO),
                eq("pedido-1"), anyList(), isNull());
    }

    @Test
    void criarPedidoRejeitaProdutoInativo() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente()));
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(produto("p1", "Pizza Antiga", 40.00, false)));
        NovoPedidoRequest request = new NovoPedidoRequest("c1", List.of(new ItemPedidoRequest("p1", 1)), null);

        assertThatThrownBy(() -> pedidoService.criarPedido(request, "atendente"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não está disponível");
        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void criarPedidoRejeitaClienteInexistente() {
        when(clienteRepository.findById("x")).thenReturn(Optional.empty());
        NovoPedidoRequest request = new NovoPedidoRequest("x", List.of(new ItemPedidoRequest("p1", 1)), null);

        assertThatThrownBy(() -> pedidoService.criarPedido(request, "atendente"))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void atualizarStatusImpedePularEtapas() {
        pedidoNoStatus();

        assertThatThrownBy(() -> pedidoService.atualizarStatus("pedido-1", StatusPedido.FINALIZADO, "atendente"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Transição inválida");
        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void atualizarStatusNaoPermiteCancelarPelaRotaComum() {
        pedidoNoStatus();

        assertThatThrownBy(() -> pedidoService.atualizarStatus("pedido-1", StatusPedido.CANCELADO, "atendente"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("exclusiva do gerente");
    }

    @Test
    void atualizarStatusRegistraSaidaEAuditaAlteracao() {
        pedidoNoStatus(StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO);

        Pedido pedido = pedidoService.atualizarStatus("pedido-1", StatusPedido.SAIU_PARA_ENTREGA, "atendente");

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.SAIU_PARA_ENTREGA);
        assertThat(pedido.getResponsavelSaida()).isEqualTo("atendente");
        assertThat(pedido.getDataHoraSaida()).isNotNull();
        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_STATUS_PEDIDO), eq(AuditoriaService.ENTIDADE_PEDIDO),
                eq("pedido-1"), alteracoesCaptor.capture(), isNull());
        assertThat(alteracoesCaptor.getValue())
                .extracting(AlteracaoCampo::getCampo, AlteracaoCampo::getValorAnterior, AlteracaoCampo::getValorNovo)
                .contains(tuple("status", "PRONTO", "SAIU_PARA_ENTREGA"), tuple("responsavelSaida", null, "atendente"));
    }

    @Test
    void pedidoCanceladoNaoPodeTerStatusAlterado() {
        Pedido pedido = pedidoNoStatus();
        pedido.cancelar("Cliente desistiu do pedido", "gerente", LocalDateTime.now());

        assertThatThrownBy(() -> pedidoService.atualizarStatus("pedido-1", StatusPedido.EM_PREPARACAO, "atendente"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("cancelado");
    }

    @Test
    void descontoAcimaDe20PorCentoERejeitado() {
        assertThatThrownBy(() -> pedidoService.aplicarDesconto("pedido-1", new BigDecimal("20.01"), "gerente"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("20");
        verifyNoInteractions(pedidoRepository, auditoriaService);
    }

    @Test
    void descontoRecalculaTotalEAuditaValoresAnterioresENovos() {
        pedidoNoStatus(StatusPedido.EM_PREPARACAO);

        Pedido pedido = pedidoService.aplicarDesconto("pedido-1", new BigDecimal("10"), "gerente");

        assertThat(pedido.getValorTotal()).isEqualByComparingTo("95.22");
        verify(auditoriaService).registrar(eq(TipoOperacao.DESCONTO_PEDIDO), eq(AuditoriaService.ENTIDADE_PEDIDO),
                eq("pedido-1"), alteracoesCaptor.capture(), isNull());
        assertThat(alteracoesCaptor.getValue())
                .extracting(AlteracaoCampo::getCampo, AlteracaoCampo::getValorAnterior, AlteracaoCampo::getValorNovo)
                .contains(tuple("percentualDesconto", "0.00", "10.00"), tuple("valorTotal", "105.80", "95.22"));
    }

    @Test
    void descontoAposSaidaERejeitado() {
        pedidoNoStatus(StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO, StatusPedido.RETIRADO);

        assertThatThrownBy(() -> pedidoService.aplicarDesconto("pedido-1", new BigDecimal("5"), "gerente"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("após a saída");
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void cancelamentoExigeJustificativa() {
        assertThatThrownBy(() -> pedidoService.cancelar("pedido-1", "curta", "gerente"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("justificativa");
        verifyNoInteractions(pedidoRepository, auditoriaService);
    }

    @Test
    void cancelamentoRegistraJustificativaNaAuditoria() {
        pedidoNoStatus(StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO);

        Pedido pedido = pedidoService.cancelar("pedido-1", "  Cliente desistiu por telefone  ", "gerente");

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(pedido.getStatusAntesCancelamento()).isEqualTo(StatusPedido.PRONTO);
        verify(auditoriaService).registrar(eq(TipoOperacao.CANCELAMENTO_PEDIDO), eq(AuditoriaService.ENTIDADE_PEDIDO),
                eq("pedido-1"), alteracoesCaptor.capture(), eq("Cliente desistiu por telefone"));
        assertThat(alteracoesCaptor.getValue())
                .extracting(AlteracaoCampo::getCampo, AlteracaoCampo::getValorAnterior, AlteracaoCampo::getValorNovo)
                .contains(tuple("status", "PRONTO", "CANCELADO"));
    }

    @Test
    void cancelamentoAposSaidaERejeitado() {
        pedidoNoStatus(StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO, StatusPedido.SAIU_PARA_ENTREGA);

        assertThatThrownBy(() -> pedidoService.cancelar("pedido-1", "Cliente desistiu do pedido", "gerente"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("após a saída");
    }

    @Test
    void pedidoCanceladoNaoPodeSerCanceladoNovamente() {
        Pedido pedido = pedidoNoStatus();
        pedido.cancelar("Primeiro cancelamento", "gerente", LocalDateTime.now());

        assertThatThrownBy(() -> pedidoService.cancelar("pedido-1", "Segundo cancelamento", "gerente"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("cancelado");
    }

    private Pedido pedidoNoStatus(StatusPedido... caminho) {
        Pedido pedido = new Pedido();
        pedido.setId("pedido-1");
        pedido.setClienteNome("Maria Souza");
        pedido.setItens(List.of(new ItemPedido("p1", "Pizza Calabresa", 2, new BigDecimal("52.90"))));
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        pedido.registrarEntrada("atendente", LocalDateTime.now());
        pedido.recalcularValores();
        for (StatusPedido status : caminho) {
            pedido.alterarStatus(status, "atendente", LocalDateTime.now());
        }
        lenient().when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));
        return pedido;
    }

    private Cliente cliente() {
        Cliente cliente = new Cliente();
        cliente.setId("c1");
        cliente.setNome("Maria Souza");
        return cliente;
    }

    private Produto produto(String id, String nome, double preco, boolean ativo) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setTipo("Pizza");
        produto.setPreco(preco);
        produto.setAtivo(ativo);
        return produto;
    }
}
