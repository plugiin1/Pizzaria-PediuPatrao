package com.umc.pediupatrao.entity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AlteracaoCampo {

    private final String campo;
    private final String valorAnterior;
    private final String valorNovo;

    public AlteracaoCampo(String campo, String valorAnterior, String valorNovo) {
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
    }

    public static List<AlteracaoCampo> comparar(Map<String, String> anterior, Map<String, String> novo) {
        Set<String> campos = new LinkedHashSet<>();
        if (anterior != null) {
            campos.addAll(anterior.keySet());
        }
        if (novo != null) {
            campos.addAll(novo.keySet());
        }
        List<AlteracaoCampo> alteracoes = new ArrayList<>();
        for (String campo : campos) {
            String valorAnterior = normalizar(anterior == null ? null : anterior.get(campo));
            String valorNovo = normalizar(novo == null ? null : novo.get(campo));
            if (!Objects.equals(valorAnterior, valorNovo)) {
                alteracoes.add(new AlteracaoCampo(campo, valorAnterior, valorNovo));
            }
        }
        return alteracoes;
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public String getCampo() {
        return campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNovo() {
        return valorNovo;
    }
}
