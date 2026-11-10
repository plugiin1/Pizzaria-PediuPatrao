package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

public record ParametrosDashboard(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal,
        List<StatusPedido> status,
        String tipoProduto,
        String responsavel) {
}
