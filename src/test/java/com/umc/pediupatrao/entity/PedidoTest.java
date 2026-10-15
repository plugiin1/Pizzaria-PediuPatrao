package com.umc.pediupatrao.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PedidoTest {

    private Pedido pedido;

    @BeforeEach
    void setUp() {
        pedido = new Pedido();
        pedido.setItens(List.of(
                new ItemPedido("p1", "Pizza Calabresa", 3, new BigDecimal("52.90")),
                new ItemPedido("p2", "Refrigerante 2L", 1, new BigDecimal("14.00"))));
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        pedido.registrarEntrada("atendente", LocalDateTime.of(2026, 9, 23, 14, 35));
        pedido.recalcularValores();
    }

    @Test
    void calculaSubtotalETotalAPartirDosItens() {
        assertThat(pedido.getItens().get(0).getSubtotal()).isEqualByComparingTo("158.70");
        assertThat(pedido.getSubtotal()).isEqualByComparingTo("172.70");
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("172.70");
        assertThat(pedido.getQuantidadeTotalItens()).isEqualTo(4);
    }

    @Test
    void registraEntradaComStatusRecebidoEHistorico() {
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.RECEBIDO);
        assertThat(pedido.getResponsavelEntrada()).isEqualTo("atendente");
        assertThat(pedido.getDataHoraEntrada()).isEqualTo(LocalDateTime.of(2026, 9, 23, 14, 35));
        assertThat(pedido.getHistoricoStatus()).hasSize(1);
    }

    @Test
    void aplicaDescontoPercentualSobreOSubtotal() {
        pedido.aplicarDesconto(new BigDecimal("10"), "gerente", LocalDateTime.now());

        assertThat(pedido.getValorDesconto()).isEqualByComparingTo("17.27");
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("155.43");
        assertThat(pedido.getResponsavelDesconto()).isEqualTo("gerente");
    }

    @Test
    void registraResponsavelEHorarioDaSaida() {
        LocalDateTime saida = LocalDateTime.of(2026, 9, 23, 15, 10);
        pedido.alterarStatus(StatusPedido.EM_PREPARACAO, "atendente", LocalDateTime.now());
        pedido.alterarStatus(StatusPedido.PRONTO, "atendente", LocalDateTime.now());
        pedido.alterarStatus(StatusPedido.SAIU_PARA_ENTREGA, "entregador01", saida);

        assertThat(pedido.getDataHoraSaida()).isEqualTo(saida);
        assertThat(pedido.getResponsavelSaida()).isEqualTo("entregador01");
        assertThat(pedido.getHistoricoStatus()).hasSize(4);
    }

    @Test
    void cancelamentoPreservaStatusAnteriorEJustificativa() {
        pedido.alterarStatus(StatusPedido.EM_PREPARACAO, "atendente", LocalDateTime.now());
        pedido.cancelar("Cliente desistiu do pedido", "gerente", LocalDateTime.now());

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(pedido.getStatusAntesCancelamento()).isEqualTo(StatusPedido.EM_PREPARACAO);
        assertThat(pedido.getJustificativaCancelamento()).isEqualTo("Cliente desistiu do pedido");
        assertThat(pedido.getResponsavelCancelamento()).isEqualTo("gerente");
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("172.70");
    }
}
