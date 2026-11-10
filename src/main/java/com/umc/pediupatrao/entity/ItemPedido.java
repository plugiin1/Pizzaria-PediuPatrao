package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

public class ItemPedido {

    private String produtoId;
    private String nomeProduto;
    private String tipoProduto;
    private int quantidade;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal precoUnitario;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal subtotal;

    public ItemPedido() {
    }

    public ItemPedido(String produtoId, String nomeProduto, int quantidade, BigDecimal precoUnitario) {
        this(produtoId, nomeProduto, null, quantidade, precoUnitario);
    }

    public ItemPedido(String produtoId, String nomeProduto, String tipoProduto, int quantidade, BigDecimal precoUnitario) {
        this.produtoId = produtoId;
        this.nomeProduto = nomeProduto;
        this.tipoProduto = tipoProduto;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario.setScale(2, RoundingMode.HALF_UP);
        this.subtotal = this.precoUnitario.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_UP);
    }

    public String getProdutoId() {
        return produtoId;
    }

    public String getNomeProduto() {
        return nomeProduto;
    }

    public String getTipoProduto() {
        return tipoProduto;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
