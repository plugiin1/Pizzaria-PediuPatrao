package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.ParametrosDashboard;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class DashboardServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);

    private MongoTemplate mongoTemplate;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        ZoneId zona = ZoneId.of("America/Sao_Paulo");
        Clock relogio = Clock.fixed(ZonedDateTime.of(HOJE.atTime(15, 0), zona).toInstant(), zona);
        service = new DashboardService(mongoTemplate, relogio);
    }

    @Test
    void semParametrosUsaOsUltimos30Dias() {
        FiltroDashboard filtro = service.normalizar(null);

        assertThat(filtro.dataFinal()).isEqualTo(HOJE);
        assertThat(filtro.dataInicial()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(filtro.dias()).isEqualTo(30);
        assertThat(filtro.status()).isEmpty();
    }

    @Test
    void somenteDataFinalInformadaRecuaTrintaDias() {
        FiltroDashboard filtro = service.normalizar(parametros(null, LocalDate.of(2026, 9, 30)));

        assertThat(filtro.dataInicial()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(filtro.dataFinal()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void dataInicialPosteriorAFinalERecusada() {
        assertThatThrownBy(() -> service.normalizar(parametros(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1))))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("posterior");
    }

    @Test
    void periodoAcimaDe366DiasERecusado() {
        assertThat(service.normalizar(parametros(LocalDate.of(2025, 11, 10), LocalDate.of(2026, 11, 10))).dias()).isEqualTo(366);

        assertThatThrownBy(() -> service.normalizar(parametros(LocalDate.of(2025, 11, 9), LocalDate.of(2026, 11, 10))))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("366");
    }

    @Test
    void normalizaStatusRepetidosETextosEmBranco() {
        ParametrosDashboard parametros = new ParametrosDashboard(null, null,
                Arrays.asList(StatusPedido.PRONTO, null, StatusPedido.PRONTO, StatusPedido.CANCELADO), "   ", " ana ");

        FiltroDashboard filtro = service.normalizar(parametros);

        assertThat(filtro.status()).containsExactly(StatusPedido.PRONTO, StatusPedido.CANCELADO);
        assertThat(filtro.tipoProduto()).isNull();
        assertThat(filtro.responsavel()).isEqualTo("ana");
    }

    @Test
    void limiteDoRankingForaDaFaixaERecusadoSemConsultarOBanco() {
        assertThatThrownBy(() -> service.produtosMaisVendidos(null, 0, null)).isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> service.produtosMaisVendidos(null, 51, null)).isInstanceOf(ParametroInvalidoException.class);
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void paginacaoInvalidaERecusadaSemConsultarOBanco() {
        assertThatThrownBy(() -> service.pedidos(null, -1, 20)).isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> service.pedidos(null, 0, 0)).isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> service.pedidos(null, 0, 101)).isInstanceOf(ParametroInvalidoException.class);
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void calculosAuxiliaresTratamDivisaoPorZero() {
        assertThat(DashboardService.percentual(BigDecimal.ONE, BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(DashboardService.percentual(BigDecimal.ONE, BigDecimal.valueOf(3))).isEqualByComparingTo("33.33");
        assertThat(DashboardService.variacao(BigDecimal.TEN, BigDecimal.ZERO)).isNull();
        assertThat(DashboardService.variacao(BigDecimal.valueOf(15), BigDecimal.TEN)).isEqualByComparingTo("50.00");
        assertThat(DashboardService.variacao(BigDecimal.valueOf(5), BigDecimal.TEN)).isEqualByComparingTo("-50.00");
        assertThat(DashboardService.decimal(null)).isEqualByComparingTo("0.00");
        assertThat(DashboardService.inteiro("texto")).isZero();
    }

    private ParametrosDashboard parametros(LocalDate inicio, LocalDate fim) {
        return new ParametrosDashboard(inicio, fim, List.of(), null, null);
    }
}
