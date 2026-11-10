package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDateTime;

public record RespostaDashboard<T>(LocalDateTime geradoEm, FiltroDashboard filtro, T dados) {
}
