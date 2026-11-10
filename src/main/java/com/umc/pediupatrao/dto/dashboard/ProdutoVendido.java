package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

public record ProdutoVendido(
        int posicao,
        String produtoId,
        String nome,
        String tipo,
        long quantidade,
        BigDecimal faturamento,
        long pedidos,
        BigDecimal participacaoQuantidade,
        BigDecimal participacaoFaturamento) {
}
