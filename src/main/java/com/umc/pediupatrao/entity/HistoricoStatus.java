package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;

public class HistoricoStatus {

    private StatusPedido statusAnterior;
    private StatusPedido statusNovo;
    private LocalDateTime dataHora;
    private String usuario;

    public HistoricoStatus() {
    }

    public HistoricoStatus(StatusPedido statusAnterior, StatusPedido statusNovo, LocalDateTime dataHora, String usuario) {
        this.statusAnterior = statusAnterior;
        this.statusNovo = statusNovo;
        this.dataHora = dataHora;
        this.usuario = usuario;
    }

    public StatusPedido getStatusAnterior() {
        return statusAnterior;
    }

    public StatusPedido getStatusNovo() {
        return statusNovo;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getUsuario() {
        return usuario;
    }
}
