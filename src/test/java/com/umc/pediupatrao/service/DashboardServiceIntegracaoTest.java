package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltro;
import com.umc.pediupatrao.dto.dashboard.OrdenacaoRanking;
import com.umc.pediupatrao.dto.dashboard.PaginaPedidos;
import com.umc.pediupatrao.dto.dashboard.ParametrosDashboard;
import com.umc.pediupatrao.dto.dashboard.PedidoResumo;
import com.umc.pediupatrao.dto.dashboard.PontoVendas;
import com.umc.pediupatrao.dto.dashboard.ProdutoVendido;
import com.umc.pediupatrao.dto.dashboard.QuantidadePorStatus;
import com.umc.pediupatrao.dto.dashboard.ResumoVendas;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidos;
import com.umc.pediupatrao.dto.dashboard.VendasPorHorario;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

@DataMongoTest
@Import({DashboardService.class, DashboardServiceIntegracaoTest.RelogioFixo.class})
class DashboardServiceIntegracaoTest {

    private static final ZoneId ZONA = ZoneId.systemDefault();
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 10, 15, 0);
    private static final ParametrosDashboard PERIODO = filtro(List.of(), null, null);

    @TestConfiguration
    static class RelogioFixo {

        @Bean
        Clock relogio() {
            return Clock.fixed(AGORA.atZone(ZONA).toInstant(), ZONA);
        }
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private DashboardService service;

    @BeforeEach
    void popularBanco() {
        mongoTemplate.dropCollection(Pedido.class);
        mongoTemplate.dropCollection(Produto.class);

        mongoTemplate.insertAll(List.of(
                produto("calabresa", "Pizza Calabresa", "Pizza", 52.90),
                produto("margherita", "Pizza Margherita", "Pizza", 49.90),
                produto("refri", "Refrigerante 2L", "Bebida", 14.00),
                produto("esfiha", "Esfiha de Carne", "Esfiha", 7.50),
                produto("pudim", "Pudim", "Sobremesa", 12.00)));

        LocalDateTime p1 = LocalDateTime.of(2026, 11, 3, 19, 30);
        Pedido pedido1 = pedido(p1, "atendente", calabresa(2), refri(1));
        finalizar(pedido1, p1, StatusPedido.SAIU_PARA_ENTREGA, 25, 50);

        LocalDateTime p2 = LocalDateTime.of(2026, 11, 3, 20, 10);
        Pedido pedido2 = pedido(p2, "ana", margherita(1));
        pedido2.aplicarDesconto(BigDecimal.TEN, "gerente", p2.plusMinutes(1));
        finalizar(pedido2, p2, StatusPedido.RETIRADO, 30, 32);

        LocalDateTime p3 = LocalDateTime.of(2026, 11, 5, 12, 15);
        Pedido pedido3 = pedido(p3, "ana", esfiha(3));
        pedido3.cancelar("Cliente desistiu do pedido", "gerente", p3.plusMinutes(3));

        LocalDateTime p4 = AGORA.minusMinutes(60);
        Pedido pedido4 = pedido(p4, "atendente", calabresa(1));
        pedido4.alterarStatus(StatusPedido.EM_PREPARACAO, "atendente", p4.plusMinutes(5));

        Pedido pedido5 = pedido(AGORA.minusMinutes(10), "bruno", refri(2));

        LocalDateTime p6 = LocalDateTime.of(2026, 10, 31, 23, 59);
        Pedido foraDoPeriodo = pedido(p6, "atendente", margherita(1));
        finalizar(foraDoPeriodo, p6, StatusPedido.RETIRADO, 20, 22);

        LocalDateTime p7 = LocalDateTime.of(2026, 11, 1, 0, 0);
        Pedido inicioDoPeriodo = pedido(p7, "atendente", refri(1));
        finalizar(inicioDoPeriodo, p7, StatusPedido.RETIRADO, 20, 22);

        mongoTemplate.insertAll(List.of(pedido1, pedido2, pedido3, pedido4, pedido5, foraDoPeriodo, inicioDoPeriodo));
    }

    @Test
    void resumoExcluiCanceladosDoFaturamentoERespeitaOsLimitesDoPeriodo() {
        ResumoVendas resumo = service.resumo(PERIODO).dados();

        assertThat(resumo.totalPedidos()).isEqualTo(6);
        assertThat(resumo.pedidosValidos()).isEqualTo(5);
        assertThat(resumo.pedidosCancelados()).isEqualTo(1);
        assertThat(resumo.taxaCancelamento()).isEqualByComparingTo("16.67");
        assertThat(resumo.faturamento()).isEqualByComparingTo("259.61");
        assertThat(resumo.faturamentoBruto()).isEqualByComparingTo("264.60");
        assertThat(resumo.totalDescontos()).isEqualByComparingTo("4.99");
        assertThat(resumo.valorCancelado()).isEqualByComparingTo("22.50");
        assertThat(resumo.ticketMedio()).isEqualByComparingTo("51.92");
        assertThat(resumo.itensVendidos()).isEqualTo(8);
    }

    @Test
    void resumoComparaComOPeriodoAnteriorDeMesmaDuracao() {
        var anterior = service.resumo(PERIODO).dados().periodoAnterior();

        assertThat(anterior.dataInicial()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(anterior.dataFinal()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(anterior.pedidosValidos()).isEqualTo(1);
        assertThat(anterior.faturamento()).isEqualByComparingTo("49.90");
        assertThat(anterior.variacaoPedidos()).isEqualByComparingTo("400.00");
        assertThat(anterior.variacaoFaturamento()).isEqualByComparingTo("420.26");
    }

    @Test
    void serieDiariaPreencheOsDiasSemVendaComZero() {
        List<PontoVendas> pontos = service.vendas(PERIODO, Agrupamento.DIA).dados().pontos();

        assertThat(pontos).hasSize(10);
        assertThat(pontos).extracting(PontoVendas::inicio, PontoVendas::pedidos, PontoVendas::cancelados)
                .contains(
                        tuple(LocalDate.of(2026, 11, 1), 1L, 0L),
                        tuple(LocalDate.of(2026, 11, 2), 0L, 0L),
                        tuple(LocalDate.of(2026, 11, 3), 2L, 0L),
                        tuple(LocalDate.of(2026, 11, 5), 0L, 1L),
                        tuple(LocalDate.of(2026, 11, 10), 2L, 0L));
        PontoVendas dia3 = pontos.get(2);
        assertThat(dia3.faturamento()).isEqualByComparingTo("164.71");
        assertThat(dia3.ticketMedio()).isEqualByComparingTo("82.36");
        assertThat(dia3.rotulo()).isEqualTo("03/11");
    }

    @Test
    void serieSemanalAgrupaASemanaAPartirDaSegundaFeira() {
        List<PontoVendas> pontos = service.vendas(PERIODO, Agrupamento.SEMANA).dados().pontos();

        assertThat(pontos).extracting(PontoVendas::inicio, PontoVendas::pedidos)
                .containsExactly(
                        tuple(LocalDate.of(2026, 10, 26), 1L),
                        tuple(LocalDate.of(2026, 11, 2), 2L),
                        tuple(LocalDate.of(2026, 11, 9), 2L));
        assertThat(pontos.get(2).faturamento()).isEqualByComparingTo("80.90");
    }

    @Test
    void serieMensalSomaTodoOPeriodo() {
        List<PontoVendas> pontos = service.vendas(PERIODO, Agrupamento.MES).dados().pontos();

        assertThat(pontos).hasSize(1);
        assertThat(pontos.get(0).faturamento()).isEqualByComparingTo("259.61");
        assertThat(pontos.get(0).rotulo()).isEqualTo("11/2026");
    }

    @Test
    void vendasPorHorarioUsamOFusoLocalEIgnoramCancelados() {
        List<VendasPorHorario> horarios = service.vendasPorHorario(PERIODO).dados();

        assertThat(horarios).hasSize(24);
        assertThat(horarios).filteredOn(h -> h.pedidos() > 0)
                .extracting(VendasPorHorario::hora, VendasPorHorario::pedidos)
                .containsExactly(tuple(0, 1L), tuple(14, 2L), tuple(19, 1L), tuple(20, 1L));
        assertThat(horarios.get(12).pedidos()).isZero();
    }

    @Test
    void situacaoDosPedidosIdentificaAtrasosETemposMedios() {
        SituacaoPedidos situacao = service.situacaoPedidos(PERIODO).dados();

        assertThat(situacao.total()).isEqualTo(6);
        assertThat(situacao.emAndamento()).isEqualTo(2);
        assertThat(situacao.atrasados()).isEqualTo(1);
        assertThat(situacao.tempoMedioAteSaidaMinutos()).isEqualTo(25.0);
        assertThat(situacao.tempoMedioAteFinalizacaoMinutos()).isEqualTo(34.7);
        assertThat(situacao.porStatus()).hasSize(StatusPedido.values().length)
                .extracting(QuantidadePorStatus::status, QuantidadePorStatus::quantidade)
                .contains(
                        tuple(StatusPedido.RECEBIDO, 1L),
                        tuple(StatusPedido.EM_PREPARACAO, 1L),
                        tuple(StatusPedido.PRONTO, 0L),
                        tuple(StatusPedido.FINALIZADO, 3L),
                        tuple(StatusPedido.CANCELADO, 1L));
    }

    @Test
    void rankingOrdenaPorQuantidadeOuFaturamentoESemCancelados() {
        List<ProdutoVendido> porQuantidade = service.produtosMaisVendidos(PERIODO, 10, OrdenacaoRanking.QUANTIDADE).dados();
        List<ProdutoVendido> porFaturamento = service.produtosMaisVendidos(PERIODO, 10, OrdenacaoRanking.FATURAMENTO).dados();

        assertThat(porQuantidade)
                .extracting(ProdutoVendido::posicao, ProdutoVendido::nome, ProdutoVendido::quantidade)
                .containsExactly(
                        tuple(1, "Refrigerante 2L", 4L),
                        tuple(2, "Pizza Calabresa", 3L),
                        tuple(3, "Pizza Margherita", 1L));
        assertThat(porQuantidade.get(0).participacaoQuantidade()).isEqualByComparingTo("50.00");
        assertThat(porFaturamento).extracting(ProdutoVendido::nome)
                .containsExactly("Pizza Calabresa", "Refrigerante 2L", "Pizza Margherita");
        assertThat(porFaturamento.get(0).faturamento()).isEqualByComparingTo("158.70");
        assertThat(service.produtosMaisVendidos(PERIODO, 1, OrdenacaoRanking.QUANTIDADE).dados()).hasSize(1);
    }

    @Test
    void filtroPorCategoriaRestringePedidosEItensDoRanking() {
        ParametrosDashboard bebidas = filtro(List.of(), "Bebida", null);

        assertThat(service.resumo(bebidas).dados().pedidosValidos()).isEqualTo(3);
        assertThat(service.produtosMaisVendidos(bebidas, 10, OrdenacaoRanking.QUANTIDADE).dados())
                .extracting(ProdutoVendido::nome, ProdutoVendido::tipo)
                .containsExactly(tuple("Refrigerante 2L", "Bebida"));
    }

    @Test
    void filtrosDeStatusEAtendente() {
        ResumoVendas cancelados = service.resumo(filtro(List.of(StatusPedido.CANCELADO), null, null)).dados();
        assertThat(cancelados.totalPedidos()).isEqualTo(1);
        assertThat(cancelados.faturamento()).isEqualByComparingTo("0");

        ResumoVendas daAna = service.resumo(filtro(List.of(), null, "ana")).dados();
        assertThat(daAna.totalPedidos()).isEqualTo(2);
        assertThat(daAna.faturamento()).isEqualByComparingTo("44.91");
    }

    @Test
    void listaDePedidosPaginaDoMaisRecenteParaOMaisAntigo() {
        PaginaPedidos primeira = service.pedidos(PERIODO, 0, 2).dados();
        PaginaPedidos ultima = service.pedidos(PERIODO, 2, 2).dados();

        assertThat(primeira.totalElementos()).isEqualTo(6);
        assertThat(primeira.totalPaginas()).isEqualTo(3);
        assertThat(primeira.conteudo()).extracting(PedidoResumo::responsavel, PedidoResumo::status)
                .containsExactly(tuple("bruno", StatusPedido.RECEBIDO), tuple("atendente", StatusPedido.EM_PREPARACAO));
        assertThat(primeira.conteudo().get(0).dataHoraEntrada()).isEqualTo(AGORA.minusMinutes(10).withNano(0));
        assertThat(ultima.conteudo()).hasSize(2);
        assertThat(ultima.conteudo().get(1).dataHoraEntrada()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
    }

    @Test
    void opcoesDeFiltroReunemCategoriasEAtendentesDoBanco() {
        OpcoesFiltro opcoes = service.opcoesFiltro().dados();

        assertThat(opcoes.tiposProduto()).containsExactly("Bebida", "Esfiha", "Pizza", "Sobremesa");
        assertThat(opcoes.responsaveis()).containsExactly("ana", "atendente", "bruno");
        assertThat(opcoes.primeiroPedido()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(opcoes.ultimoPedido()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(opcoes.status()).hasSize(StatusPedido.values().length);
    }

    @Test
    void periodoSemPedidosRetornaValoresZerados() {
        ParametrosDashboard vazio = new ParametrosDashboard(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), List.of(), null, null);

        ResumoVendas resumo = service.resumo(vazio).dados();
        assertThat(resumo.totalPedidos()).isZero();
        assertThat(resumo.ticketMedio()).isEqualByComparingTo("0");
        assertThat(resumo.taxaCancelamento()).isEqualByComparingTo("0");
        assertThat(resumo.periodoAnterior().variacaoFaturamento()).isNull();
        assertThat(service.vendas(vazio, Agrupamento.DIA).dados().pontos()).hasSize(31).allMatch(p -> p.pedidos() == 0);
        assertThat(service.produtosMaisVendidos(vazio, 10, null).dados()).isEmpty();
        assertThat(service.situacaoPedidos(vazio).dados().tempoMedioAteSaidaMinutos()).isNull();
    }

    private static ParametrosDashboard filtro(List<StatusPedido> status, String tipoProduto, String responsavel) {
        return new ParametrosDashboard(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), status, tipoProduto, responsavel);
    }

    private Pedido pedido(LocalDateTime entrada, String atendente, ItemPedido... itens) {
        Pedido pedido = new Pedido();
        pedido.setClienteId("cliente-1");
        pedido.setClienteNome("Maria Souza");
        pedido.setItens(List.of(itens));
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        pedido.registrarEntrada(atendente, entrada);
        pedido.recalcularValores();
        return pedido;
    }

    private void finalizar(Pedido pedido, LocalDateTime entrada, StatusPedido saida, int minutosAteSaida, int minutosAteFinal) {
        String responsavel = pedido.getResponsavelEntrada();
        pedido.alterarStatus(StatusPedido.EM_PREPARACAO, responsavel, entrada.plusMinutes(5));
        pedido.alterarStatus(StatusPedido.PRONTO, responsavel, entrada.plusMinutes(minutosAteSaida - 5L));
        pedido.alterarStatus(saida, responsavel, entrada.plusMinutes(minutosAteSaida));
        pedido.alterarStatus(StatusPedido.FINALIZADO, responsavel, entrada.plusMinutes(minutosAteFinal));
    }

    private static ItemPedido calabresa(int quantidade) {
        return new ItemPedido("calabresa", "Pizza Calabresa", "Pizza", quantidade, new BigDecimal("52.90"));
    }

    private static ItemPedido margherita(int quantidade) {
        return new ItemPedido("margherita", "Pizza Margherita", "Pizza", quantidade, new BigDecimal("49.90"));
    }

    private static ItemPedido refri(int quantidade) {
        return new ItemPedido("refri", "Refrigerante 2L", "Bebida", quantidade, new BigDecimal("14.00"));
    }

    private static ItemPedido esfiha(int quantidade) {
        return new ItemPedido("esfiha", "Esfiha de Carne", "Esfiha", quantidade, new BigDecimal("7.50"));
    }

    private static Produto produto(String id, String nome, String tipo, double preco) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(preco);
        produto.setAtivo(true);
        return produto;
    }
}
