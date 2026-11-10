package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;

public record QuantidadePorStatus(
        StatusPedido status,
        String descricao,
        long quantidade,
        BigDecimal percentual,
        BigDecimal valorTotal) {
}
