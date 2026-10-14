package com.umc.pediupatrao.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum StatusPedido {
    RECEBIDO("Recebido"),
    EM_PREPARACAO("Em preparação"),
    PRONTO("Pronto"),
    SAIU_PARA_ENTREGA("Saiu para entrega"),
    RETIRADO("Retirado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private static final Map<StatusPedido, Set<StatusPedido>> TRANSICOES = Map.of(
            RECEBIDO, EnumSet.of(EM_PREPARACAO),
            EM_PREPARACAO, EnumSet.of(PRONTO),
            PRONTO, EnumSet.of(SAIU_PARA_ENTREGA, RETIRADO),
            SAIU_PARA_ENTREGA, EnumSet.of(FINALIZADO),
            RETIRADO, EnumSet.of(FINALIZADO),
            FINALIZADO, EnumSet.noneOf(StatusPedido.class),
            CANCELADO, EnumSet.noneOf(StatusPedido.class)
    );

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public Set<StatusPedido> getProximos() {
        return TRANSICOES.get(this);
    }

    public boolean podeAvancarPara(StatusPedido novo) {
        return getProximos().contains(novo);
    }

    public boolean isSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    public boolean isAntesDaSaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }
}
