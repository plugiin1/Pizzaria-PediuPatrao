package com.umc.pediupatrao.dto.dashboard;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

public enum Agrupamento {
    DIA("day", DateTimeFormatter.ofPattern("dd/MM")),
    SEMANA("week", DateTimeFormatter.ofPattern("dd/MM")),
    MES("month", DateTimeFormatter.ofPattern("MM/yyyy"));

    private final String unidadeMongo;
    private final DateTimeFormatter formatoRotulo;

    Agrupamento(String unidadeMongo, DateTimeFormatter formatoRotulo) {
        this.unidadeMongo = unidadeMongo;
        this.formatoRotulo = formatoRotulo;
    }

    public String getUnidadeMongo() {
        return unidadeMongo;
    }

    public LocalDate inicioDoPeriodo(LocalDate data) {
        return switch (this) {
            case DIA -> data;
            case SEMANA -> data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> data.withDayOfMonth(1);
        };
    }

    public LocalDate proximo(LocalDate inicio) {
        return switch (this) {
            case DIA -> inicio.plusDays(1);
            case SEMANA -> inicio.plusWeeks(1);
            case MES -> inicio.plusMonths(1);
        };
    }

    public String rotulo(LocalDate inicio) {
        return inicio.format(formatoRotulo);
    }
}
