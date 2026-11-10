package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record FiltroDashboard(
        LocalDate dataInicial,
        LocalDate dataFinal,
        List<StatusPedido> status,
        String tipoProduto,
        String responsavel) {

    public long dias() {
        return ChronoUnit.DAYS.between(dataInicial, dataFinal) + 1;
    }

    public FiltroDashboard periodoAnterior() {
        LocalDate fimAnterior = dataInicial.minusDays(1);
        return new FiltroDashboard(fimAnterior.minusDays(dias() - 1), fimAnterior, status, tipoProduto, responsavel);
    }
}
