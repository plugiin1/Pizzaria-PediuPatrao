package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PedidoResumo(
        String id,
        String cliente,
        LocalDateTime dataHoraEntrada,
        StatusPedido status,
        String statusDescricao,
        long quantidadeItens,
        BigDecimal valorTotal,
        String responsavel) {
}
