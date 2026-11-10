package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

public record ResumoVendas(
        long totalPedidos,
        long pedidosValidos,
        long pedidosCancelados,
        BigDecimal taxaCancelamento,
        BigDecimal faturamento,
        BigDecimal faturamentoBruto,
        BigDecimal totalDescontos,
        BigDecimal valorCancelado,
        BigDecimal ticketMedio,
        long itensVendidos,
        ComparativoPeriodo periodoAnterior) {
}
