package com.umc.pediupatrao.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StatusPedidoTest {

    @ParameterizedTest
    @CsvSource({
        "RECEBIDO, EM_PREPARACAO",
        "EM_PREPARACAO, PRONTO",
        "PRONTO, SAIU_PARA_ENTREGA",
        "PRONTO, RETIRADO",
        "SAIU_PARA_ENTREGA, FINALIZADO",
        "RETIRADO, FINALIZADO"
    })
    void permiteSomenteAsTransicoesDoFluxo(StatusPedido atual, StatusPedido proximo) {
        assertThat(atual.podeAvancarPara(proximo)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "RECEBIDO, PRONTO",
        "RECEBIDO, FINALIZADO",
        "EM_PREPARACAO, FINALIZADO",
        "EM_PREPARACAO, SAIU_PARA_ENTREGA",
        "PRONTO, EM_PREPARACAO",
        "RETIRADO, SAIU_PARA_ENTREGA",
        "FINALIZADO, RECEBIDO",
        "RECEBIDO, CANCELADO"
    })
    void bloqueiaTransicoesInvalidas(StatusPedido atual, StatusPedido proximo) {
        assertThat(atual.podeAvancarPara(proximo)).isFalse();
    }

    @Test
    void estadosFinaisNaoPossuemProximoStatus() {
        assertThat(StatusPedido.FINALIZADO.getProximos()).isEmpty();
        assertThat(StatusPedido.CANCELADO.getProximos()).isEmpty();
    }

    @Test
    void identificaEstadosAntesDaSaida() {
        assertThat(StatusPedido.RECEBIDO.isAntesDaSaida()).isTrue();
        assertThat(StatusPedido.EM_PREPARACAO.isAntesDaSaida()).isTrue();
        assertThat(StatusPedido.PRONTO.isAntesDaSaida()).isTrue();
        assertThat(StatusPedido.SAIU_PARA_ENTREGA.isAntesDaSaida()).isFalse();
        assertThat(StatusPedido.RETIRADO.isAntesDaSaida()).isFalse();
        assertThat(StatusPedido.FINALIZADO.isAntesDaSaida()).isFalse();
        assertThat(StatusPedido.CANCELADO.isAntesDaSaida()).isFalse();
    }
}
