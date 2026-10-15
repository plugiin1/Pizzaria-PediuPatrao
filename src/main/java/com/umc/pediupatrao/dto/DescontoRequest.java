package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DescontoRequest(
        @NotNull(message = "informe o percentual de desconto")
        @DecimalMin(value = "0.01", message = "o desconto deve ser maior que zero")
        @DecimalMax(value = "20.00", message = "o desconto não pode ultrapassar 20%")
        @Digits(integer = 2, fraction = 2, message = "use no máximo duas casas decimais") BigDecimal percentual) {
}
