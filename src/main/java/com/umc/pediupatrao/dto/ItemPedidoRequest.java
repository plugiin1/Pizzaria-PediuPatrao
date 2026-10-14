package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ItemPedidoRequest(
        @NotBlank(message = "informe o produto") String produtoId,
        @Min(value = 1, message = "a quantidade mínima é 1")
        @Max(value = 50, message = "a quantidade máxima por item é 50") int quantidade) {
}
