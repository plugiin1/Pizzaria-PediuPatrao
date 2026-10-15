package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelamentoRequest(
        @NotBlank(message = "a justificativa é obrigatória")
        @Size(min = 10, max = 500, message = "a justificativa deve ter entre 10 e 500 caracteres") String justificativa) {
}
