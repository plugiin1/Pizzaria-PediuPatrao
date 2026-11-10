package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PontoVendas(
        LocalDate inicio,
        String rotulo,
        long pedidos,
        BigDecimal faturamento,
        BigDecimal ticketMedio,
        long cancelados) {
}
