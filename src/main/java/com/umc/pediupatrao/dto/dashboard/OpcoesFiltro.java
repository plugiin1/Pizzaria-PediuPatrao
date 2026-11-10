package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDate;
import java.util.List;

public record OpcoesFiltro(
        List<OpcaoStatus> status,
        List<String> tiposProduto,
        List<String> responsaveis,
        LocalDate primeiroPedido,
        LocalDate ultimoPedido,
        int periodoMaximoDias) {

    public record OpcaoStatus(String valor, String descricao) {
    }
}
