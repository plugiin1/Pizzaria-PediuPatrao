package com.umc.pediupatrao.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlteracaoCampoTest {

    @Test
    void retornaSomenteOsCamposAlteradosComValoresAnteriorENovo() {
        Map<String, String> anterior = new LinkedHashMap<>();
        anterior.put("nome", "Maria");
        anterior.put("logradouro", "Rua das Flores");
        anterior.put("numero", "10");

        Map<String, String> novo = new LinkedHashMap<>();
        novo.put("nome", "Maria");
        novo.put("logradouro", "Av. Brasil");
        novo.put("numero", "10");

        List<AlteracaoCampo> alteracoes = AlteracaoCampo.comparar(anterior, novo);

        assertThat(alteracoes)
                .extracting(AlteracaoCampo::getCampo, AlteracaoCampo::getValorAnterior, AlteracaoCampo::getValorNovo)
                .containsExactly(tuple("logradouro", "Rua das Flores", "Av. Brasil"));
    }

    @Test
    void consideraVazioEEspacosComoAusenciaDeValor() {
        Map<String, String> anterior = new LinkedHashMap<>();
        anterior.put("complemento", null);
        Map<String, String> novo = new LinkedHashMap<>();
        novo.put("complemento", "   ");

        assertThat(AlteracaoCampo.comparar(anterior, novo)).isEmpty();
    }

    @Test
    void inclusaoRegistraTodosOsCamposPreenchidosComoNovos() {
        Map<String, String> novo = new LinkedHashMap<>();
        novo.put("nome", "João");
        novo.put("complemento", null);

        assertThat(AlteracaoCampo.comparar(null, novo))
                .extracting(AlteracaoCampo::getCampo, AlteracaoCampo::getValorAnterior, AlteracaoCampo::getValorNovo)
                .containsExactly(tuple("nome", null, "João"));
    }
}
