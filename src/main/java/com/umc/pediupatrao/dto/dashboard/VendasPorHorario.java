package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

public record VendasPorHorario(int hora, long pedidos, BigDecimal faturamento) {
}
