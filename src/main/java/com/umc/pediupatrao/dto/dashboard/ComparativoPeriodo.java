package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ComparativoPeriodo(
        LocalDate dataInicial,
        LocalDate dataFinal,
        long pedidosValidos,
        BigDecimal faturamento,
        BigDecimal ticketMedio,
        BigDecimal variacaoPedidos,
        BigDecimal variacaoFaturamento,
        BigDecimal variacaoTicketMedio) {
}
