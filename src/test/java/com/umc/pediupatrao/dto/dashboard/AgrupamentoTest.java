package com.umc.pediupatrao.dto.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgrupamentoTest {

    private static final LocalDate QUARTA = LocalDate.of(2026, 11, 4);

    @Test
    void semanaComecaNaSegundaFeira() {
        assertThat(Agrupamento.SEMANA.inicioDoPeriodo(QUARTA)).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(Agrupamento.SEMANA.inicioDoPeriodo(LocalDate.of(2026, 11, 1))).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(Agrupamento.SEMANA.proximo(LocalDate.of(2026, 11, 2))).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void mesComecaNoPrimeiroDia() {
        assertThat(Agrupamento.MES.inicioDoPeriodo(QUARTA)).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(Agrupamento.MES.proximo(LocalDate.of(2026, 11, 1))).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(Agrupamento.MES.rotulo(LocalDate.of(2026, 11, 1))).isEqualTo("11/2026");
    }

    @Test
    void diaMantemAData() {
        assertThat(Agrupamento.DIA.inicioDoPeriodo(QUARTA)).isEqualTo(QUARTA);
        assertThat(Agrupamento.DIA.proximo(QUARTA)).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(Agrupamento.DIA.rotulo(QUARTA)).isEqualTo("04/11");
    }

    @Test
    void periodoAnteriorTemAMesmaDuracaoEAntecedeOFiltro() {
        FiltroDashboard filtro = new FiltroDashboard(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), List.of(), null, null);

        FiltroDashboard anterior = filtro.periodoAnterior();

        assertThat(filtro.dias()).isEqualTo(10);
        assertThat(anterior.dataInicial()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(anterior.dataFinal()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(anterior.dias()).isEqualTo(10);
    }
}
