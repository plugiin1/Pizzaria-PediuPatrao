package com.umc.pediupatrao.dto.dashboard;

import java.util.List;

public record SituacaoPedidos(
        long total,
        long emAndamento,
        long atrasados,
        int limiteAtrasoMinutos,
        Double tempoMedioAteSaidaMinutos,
        Double tempoMedioAteFinalizacaoMinutos,
        List<QuantidadePorStatus> porStatus) {
}
