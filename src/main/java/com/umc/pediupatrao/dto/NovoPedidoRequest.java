package com.umc.pediupatrao.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record NovoPedidoRequest(
        @NotBlank(message = "informe o cliente") String clienteId,
        @NotEmpty(message = "o pedido deve ter ao menos um item")
        @Size(max = 30, message = "o pedido pode ter no máximo 30 itens") List<@Valid ItemPedidoRequest> itens,
        @Size(max = 300, message = "a observação pode ter no máximo 300 caracteres") String observacao) {
}
