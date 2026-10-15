package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.TipoOperacao;
import java.time.LocalDate;

public record FiltroAuditoria(String entidade, String entidadeId, TipoOperacao operacao, String usuario,
        LocalDate dataInicial, LocalDate dataFinal) {
}
