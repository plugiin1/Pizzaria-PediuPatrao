package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = "pedidos")
public class Pedido {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    @Id
    private String id;
    private String clienteId;
    private String clienteNome;
    private List<ItemPedido> itens = new ArrayList<>();
    private String observacao;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal percentualDesconto = BigDecimal.ZERO;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    private StatusPedido status;
    private LocalDateTime dataHoraEntrada;
    private String responsavelEntrada;

    public void recalcularValores() {
        subtotal = itens.stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentual = percentualDesconto == null ? BigDecimal.ZERO : percentualDesconto;
        valorDesconto = subtotal.multiply(percentual).divide(CEM, 2, RoundingMode.HALF_UP);
        valorTotal = subtotal.subtract(valorDesconto).setScale(2, RoundingMode.HALF_UP);
    }

    public int getQuantidadeTotalItens() {
        return itens.stream().mapToInt(ItemPedido::getQuantidade).sum();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public String getClienteNome() {
        return clienteNome;
    }

    public void setClienteNome(String clienteNome) {
        this.clienteNome = clienteNome;
    }

    public List<ItemPedido> getItens() {
        return itens;
    }

    public void setItens(List<ItemPedido> itens) {
        this.itens = itens == null ? new ArrayList<>() : new ArrayList<>(itens);
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getPercentualDesconto() {
        return percentualDesconto;
    }

    public void setPercentualDesconto(BigDecimal percentualDesconto) {
        this.percentualDesconto = percentualDesconto;
    }

    public BigDecimal getValorDesconto() {
        return valorDesconto;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public void setStatus(StatusPedido status) {
        this.status = status;
    }

    public LocalDateTime getDataHoraEntrada() {
        return dataHoraEntrada;
    }

    public void setDataHoraEntrada(LocalDateTime dataHoraEntrada) {
        this.dataHoraEntrada = dataHoraEntrada;
    }

    public String getResponsavelEntrada() {
        return responsavelEntrada;
    }

    public void setResponsavelEntrada(String responsavelEntrada) {
        this.responsavelEntrada = responsavelEntrada;
    }
}
