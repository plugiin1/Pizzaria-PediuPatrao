package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.ComparativoPeriodo;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltro;
import com.umc.pediupatrao.dto.dashboard.OrdenacaoRanking;
import com.umc.pediupatrao.dto.dashboard.PaginaPedidos;
import com.umc.pediupatrao.dto.dashboard.ParametrosDashboard;
import com.umc.pediupatrao.dto.dashboard.PedidoResumo;
import com.umc.pediupatrao.dto.dashboard.PontoVendas;
import com.umc.pediupatrao.dto.dashboard.ProdutoVendido;
import com.umc.pediupatrao.dto.dashboard.QuantidadePorStatus;
import com.umc.pediupatrao.dto.dashboard.RespostaDashboard;
import com.umc.pediupatrao.dto.dashboard.ResumoVendas;
import com.umc.pediupatrao.dto.dashboard.SerieVendas;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidos;
import com.umc.pediupatrao.dto.dashboard.VendasPorHorario;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.ParametroInvalidoException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
public class DashboardService {

    public static final int PERIODO_PADRAO_DIAS = 30;
    public static final int PERIODO_MAXIMO_DIAS = 366;
    public static final int LIMITE_ATRASO_MINUTOS = 45;
    public static final int LIMITE_MAXIMO_RANKING = 50;
    public static final int TAMANHO_MAXIMO_PAGINA = 100;

    private static final String PEDIDOS = "pedidos";
    private static final String PRODUTOS = "produtos";
    private static final String CANCELADO = StatusPedido.CANCELADO.name();
    private static final List<String> ANTES_DA_SAIDA = List.of(
            StatusPedido.RECEBIDO.name(), StatusPedido.EM_PREPARACAO.name(), StatusPedido.PRONTO.name());
    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final Document NAO_CANCELADO = new Document("$ne", List.of("$status", CANCELADO));
    private static final Document E_CANCELADO = new Document("$eq", List.of("$status", CANCELADO));

    private final MongoTemplate mongoTemplate;
    private final Clock relogio;

    public DashboardService(MongoTemplate mongoTemplate, Clock relogio) {
        this.mongoTemplate = mongoTemplate;
        this.relogio = relogio;
    }

    public RespostaDashboard<ResumoVendas> resumo(ParametrosDashboard parametros) {
        FiltroDashboard filtro = normalizar(parametros);
        Totais atual = totais(filtro);
        FiltroDashboard filtroAnterior = filtro.periodoAnterior();
        Totais anterior = totais(filtroAnterior);

        ComparativoPeriodo comparativo = new ComparativoPeriodo(
                filtroAnterior.dataInicial(), filtroAnterior.dataFinal(),
                anterior.validos(), anterior.faturamento(), anterior.ticketMedio(),
                variacao(BigDecimal.valueOf(atual.validos()), BigDecimal.valueOf(anterior.validos())),
                variacao(atual.faturamento(), anterior.faturamento()),
                variacao(atual.ticketMedio(), anterior.ticketMedio()));

        ResumoVendas resumo = new ResumoVendas(atual.total(), atual.validos(), atual.cancelados(),
                percentual(BigDecimal.valueOf(atual.cancelados()), BigDecimal.valueOf(atual.total())),
                atual.faturamento(), atual.faturamentoBruto(), atual.descontos(), atual.valorCancelado(),
                atual.ticketMedio(), atual.itens(), comparativo);
        return responder(filtro, resumo);
    }

    public RespostaDashboard<SerieVendas> vendas(ParametrosDashboard parametros, Agrupamento agrupamento) {
        FiltroDashboard filtro = normalizar(parametros);
        Agrupamento unidade = agrupamento == null ? Agrupamento.DIA : agrupamento;

        Document truncamento = new Document("date", "$dataHoraEntrada")
                .append("unit", unidade.getUnidadeMongo())
                .append("timezone", fusoMongo());
        if (unidade == Agrupamento.SEMANA) {
            truncamento.append("startOfWeek", "monday");
        }
        Document grupo = new Document("_id", new Document("$dateTrunc", truncamento))
                .append("pedidos", soma(condicao(NAO_CANCELADO, 1, 0)))
                .append("faturamento", soma(condicao(NAO_CANCELADO, "$valorTotal", 0)))
                .append("cancelados", soma(condicao(E_CANCELADO, 1, 0)));

        Map<LocalDate, Document> porPeriodo = new HashMap<>();
        for (Document documento : agregar(List.of(estagioMatch(filtro), new Document("$group", grupo)))) {
            porPeriodo.put(paraData(documento.getDate("_id")), documento);
        }

        List<PontoVendas> pontos = new ArrayList<>();
        for (LocalDate inicio = unidade.inicioDoPeriodo(filtro.dataInicial());
                !inicio.isAfter(filtro.dataFinal());
                inicio = unidade.proximo(inicio)) {
            Document documento = porPeriodo.getOrDefault(inicio, new Document());
            long pedidos = inteiro(documento.get("pedidos"));
            BigDecimal faturamento = decimal(documento.get("faturamento"));
            pontos.add(new PontoVendas(inicio, unidade.rotulo(inicio), pedidos, faturamento,
                    media(faturamento, pedidos), inteiro(documento.get("cancelados"))));
        }
        return responder(filtro, new SerieVendas(unidade, pontos));
    }

    public RespostaDashboard<List<VendasPorHorario>> vendasPorHorario(ParametrosDashboard parametros) {
        FiltroDashboard filtro = normalizar(parametros);
        Document hora = new Document("$hour", new Document("date", "$dataHoraEntrada").append("timezone", fusoMongo()));
        Document grupo = new Document("_id", hora)
                .append("pedidos", new Document("$sum", 1))
                .append("faturamento", new Document("$sum", "$valorTotal"));

        Map<Integer, Document> porHora = new HashMap<>();
        for (Document documento : agregar(List.of(estagioMatch(filtro), naoCancelados(), new Document("$group", grupo)))) {
            porHora.put(((Number) documento.get("_id")).intValue(), documento);
        }

        List<VendasPorHorario> horarios = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Document documento = porHora.getOrDefault(h, new Document());
            horarios.add(new VendasPorHorario(h, inteiro(documento.get("pedidos")), decimal(documento.get("faturamento"))));
        }
        return responder(filtro, horarios);
    }

    public RespostaDashboard<SituacaoPedidos> situacaoPedidos(ParametrosDashboard parametros) {
        FiltroDashboard filtro = normalizar(parametros);

        Document grupoStatus = new Document("_id", "$status")
                .append("quantidade", new Document("$sum", 1))
                .append("valor", new Document("$sum", "$valorTotal"));
        Map<String, Document> porStatus = new HashMap<>();
        for (Document documento : agregar(List.of(estagioMatch(filtro), new Document("$group", grupoStatus)))) {
            porStatus.put(documento.getString("_id"), documento);
        }

        Date limiteAtraso = Date.from(LocalDateTime.now(relogio).minusMinutes(LIMITE_ATRASO_MINUTOS).atZone(zona()).toInstant());
        Document atrasado = new Document("$and", List.of(
                new Document("$in", List.of("$status", ANTES_DA_SAIDA)),
                new Document("$lt", List.of("$dataHoraEntrada", limiteAtraso))));
        Document grupoGeral = new Document("_id", null)
                .append("atrasados", soma(condicao(atrasado, 1, 0)))
                .append("tempoSaida", new Document("$avg", new Document("$subtract", List.of("$dataHoraSaida", "$dataHoraEntrada"))))
                .append("tempoFinalizacao", new Document("$avg", new Document("$subtract", List.of("$dataHoraFinalizacao", "$dataHoraEntrada"))));
        List<Document> geral = agregar(List.of(estagioMatch(filtro), new Document("$group", grupoGeral)));
        Document totaisGerais = geral.isEmpty() ? new Document() : geral.get(0);

        long total = porStatus.values().stream().mapToLong(documento -> inteiro(documento.get("quantidade"))).sum();
        List<QuantidadePorStatus> quantidades = new ArrayList<>();
        long emAndamento = 0;
        for (StatusPedido status : StatusPedido.values()) {
            Document documento = porStatus.getOrDefault(status.name(), new Document());
            long quantidade = inteiro(documento.get("quantidade"));
            if (status != StatusPedido.FINALIZADO && status != StatusPedido.CANCELADO) {
                emAndamento += quantidade;
            }
            quantidades.add(new QuantidadePorStatus(status, status.getDescricao(), quantidade,
                    percentual(BigDecimal.valueOf(quantidade), BigDecimal.valueOf(total)), decimal(documento.get("valor"))));
        }

        return responder(filtro, new SituacaoPedidos(total, emAndamento, inteiro(totaisGerais.get("atrasados")),
                LIMITE_ATRASO_MINUTOS, minutos(totaisGerais.get("tempoSaida")), minutos(totaisGerais.get("tempoFinalizacao")),
                quantidades));
    }

    public RespostaDashboard<List<ProdutoVendido>> produtosMaisVendidos(ParametrosDashboard parametros, Integer limite,
            OrdenacaoRanking ordenacao) {
        FiltroDashboard filtro = normalizar(parametros);
        int quantidadeMaxima = limite == null ? 10 : limite;
        if (quantidadeMaxima < 1 || quantidadeMaxima > LIMITE_MAXIMO_RANKING) {
            throw new ParametroInvalidoException("O parâmetro 'limite' deve estar entre 1 e " + LIMITE_MAXIMO_RANKING + ".");
        }
        OrdenacaoRanking criterio = ordenacao == null ? OrdenacaoRanking.QUANTIDADE : ordenacao;

        List<Document> pipeline = new ArrayList<>(List.of(estagioMatch(filtro), naoCancelados(), new Document("$unwind", "$itens")));
        if (filtro.tipoProduto() != null) {
            pipeline.add(new Document("$match", new Document("itens.tipoProduto", filtro.tipoProduto())));
        }
        pipeline.add(new Document("$group", new Document("_id", "$itens.produtoId")
                .append("nome", new Document("$max", "$itens.nomeProduto"))
                .append("tipo", new Document("$max", "$itens.tipoProduto"))
                .append("quantidade", new Document("$sum", "$itens.quantidade"))
                .append("faturamento", new Document("$sum", "$itens.subtotal"))
                .append("pedidos", new Document("$sum", 1))));
        List<Document> grupos = agregar(pipeline);

        BigDecimal quantidadeTotal = BigDecimal.valueOf(grupos.stream().mapToLong(g -> inteiro(g.get("quantidade"))).sum());
        BigDecimal faturamentoTotal = grupos.stream().map(g -> decimal(g.get("faturamento"))).reduce(BigDecimal.ZERO, BigDecimal::add);

        Comparator<Document> comparador = criterio == OrdenacaoRanking.FATURAMENTO
                ? Comparator.comparing((Document g) -> decimal(g.get("faturamento")))
                : Comparator.comparingLong((Document g) -> inteiro(g.get("quantidade")));
        comparador = comparador.reversed().thenComparing(g -> Objects.toString(g.getString("nome"), ""));

        List<Document> ordenados = grupos.stream().sorted(comparador).limit(quantidadeMaxima).toList();
        List<ProdutoVendido> ranking = new ArrayList<>();
        for (int i = 0; i < ordenados.size(); i++) {
            Document grupo = ordenados.get(i);
            long quantidade = inteiro(grupo.get("quantidade"));
            BigDecimal faturamento = decimal(grupo.get("faturamento"));
            ranking.add(new ProdutoVendido(i + 1, grupo.getString("_id"), grupo.getString("nome"), grupo.getString("tipo"),
                    quantidade, faturamento, inteiro(grupo.get("pedidos")),
                    percentual(BigDecimal.valueOf(quantidade), quantidadeTotal), percentual(faturamento, faturamentoTotal)));
        }
        return responder(filtro, ranking);
    }

    public RespostaDashboard<PaginaPedidos> pedidos(ParametrosDashboard parametros, Integer pagina, Integer tamanho) {
        FiltroDashboard filtro = normalizar(parametros);
        int numeroPagina = pagina == null ? 0 : pagina;
        int tamanhoPagina = tamanho == null ? 20 : tamanho;
        if (numeroPagina < 0) {
            throw new ParametroInvalidoException("O parâmetro 'pagina' não pode ser negativo.");
        }
        if (tamanhoPagina < 1 || tamanhoPagina > TAMANHO_MAXIMO_PAGINA) {
            throw new ParametroInvalidoException("O parâmetro 'tamanho' deve estar entre 1 e " + TAMANHO_MAXIMO_PAGINA + ".");
        }

        Document criterio = criterio(filtro);
        long totalElementos = mongoTemplate.getCollection(PEDIDOS).countDocuments(criterio);
        List<PedidoResumo> conteudo = new ArrayList<>();
        for (Document documento : mongoTemplate.getCollection(PEDIDOS).find(criterio)
                .sort(new Document("dataHoraEntrada", -1))
                .skip(numeroPagina * tamanhoPagina)
                .limit(tamanhoPagina)) {
            conteudo.add(paraResumo(documento));
        }
        int totalPaginas = (int) Math.ceil((double) totalElementos / tamanhoPagina);
        return responder(filtro, new PaginaPedidos(conteudo, numeroPagina, tamanhoPagina, totalElementos, totalPaginas));
    }

    public RespostaDashboard<OpcoesFiltro> opcoesFiltro() {
        Collator ordemAlfabetica = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        TreeSet<String> tipos = new TreeSet<>(ordemAlfabetica);
        mongoTemplate.getCollection(PRODUTOS).distinct("tipo", String.class).forEach(tipos::add);
        mongoTemplate.getCollection(PEDIDOS).distinct("itens.tipoProduto", String.class).forEach(tipos::add);
        TreeSet<String> responsaveis = new TreeSet<>(ordemAlfabetica);
        mongoTemplate.getCollection(PEDIDOS).distinct("responsavelEntrada", String.class).forEach(responsaveis::add);

        List<OpcoesFiltro.OpcaoStatus> status = Arrays.stream(StatusPedido.values())
                .map(s -> new OpcoesFiltro.OpcaoStatus(s.name(), s.getDescricao()))
                .toList();
        OpcoesFiltro opcoes = new OpcoesFiltro(status, new ArrayList<>(tipos), new ArrayList<>(responsaveis),
                dataDoPedidoMaisAntigo(1), dataDoPedidoMaisAntigo(-1), PERIODO_MAXIMO_DIAS);
        return new RespostaDashboard<>(LocalDateTime.now(relogio), null, opcoes);
    }

    public FiltroDashboard normalizar(ParametrosDashboard parametros) {
        LocalDate hoje = LocalDate.now(relogio);
        LocalDate dataFinal = parametros == null || parametros.dataFinal() == null ? hoje : parametros.dataFinal();
        LocalDate dataInicial = parametros == null || parametros.dataInicial() == null
                ? dataFinal.minusDays(PERIODO_PADRAO_DIAS - 1L)
                : parametros.dataInicial();
        if (dataInicial.isAfter(dataFinal)) {
            throw new ParametroInvalidoException("A data inicial não pode ser posterior à data final.");
        }
        FiltroDashboard filtro = new FiltroDashboard(dataInicial, dataFinal,
                parametros == null || parametros.status() == null
                        ? List.of()
                        : parametros.status().stream().filter(Objects::nonNull).distinct().toList(),
                texto(parametros == null ? null : parametros.tipoProduto()),
                texto(parametros == null ? null : parametros.responsavel()));
        if (filtro.dias() > PERIODO_MAXIMO_DIAS) {
            throw new ParametroInvalidoException("O período consultado não pode ultrapassar " + PERIODO_MAXIMO_DIAS + " dias.");
        }
        return filtro;
    }

    private Totais totais(FiltroDashboard filtro) {
        Document grupo = new Document("_id", null)
                .append("total", new Document("$sum", 1))
                .append("cancelados", soma(condicao(E_CANCELADO, 1, 0)))
                .append("faturamento", soma(condicao(NAO_CANCELADO, "$valorTotal", 0)))
                .append("faturamentoBruto", soma(condicao(NAO_CANCELADO, "$subtotal", 0)))
                .append("descontos", soma(condicao(NAO_CANCELADO, "$valorDesconto", 0)))
                .append("valorCancelado", soma(condicao(E_CANCELADO, "$valorTotal", 0)))
                .append("itens", soma(condicao(NAO_CANCELADO, new Document("$sum", "$itens.quantidade"), 0)));
        List<Document> resultado = agregar(List.of(estagioMatch(filtro), new Document("$group", grupo)));
        Document documento = resultado.isEmpty() ? new Document() : resultado.get(0);
        return new Totais(inteiro(documento.get("total")), inteiro(documento.get("cancelados")),
                decimal(documento.get("faturamento")), decimal(documento.get("faturamentoBruto")),
                decimal(documento.get("descontos")), decimal(documento.get("valorCancelado")),
                inteiro(documento.get("itens")));
    }

    private Document criterio(FiltroDashboard filtro) {
        Document criterio = new Document("dataHoraEntrada", new Document("$gte", inicioDoDia(filtro.dataInicial()))
                .append("$lt", inicioDoDia(filtro.dataFinal().plusDays(1))));
        if (!filtro.status().isEmpty()) {
            criterio.append("status", new Document("$in", filtro.status().stream().map(Enum::name).toList()));
        }
        if (filtro.tipoProduto() != null) {
            criterio.append("itens.tipoProduto", filtro.tipoProduto());
        }
        if (filtro.responsavel() != null) {
            criterio.append("responsavelEntrada", filtro.responsavel());
        }
        return criterio;
    }

    private Document estagioMatch(FiltroDashboard filtro) {
        return new Document("$match", criterio(filtro));
    }

    private Document naoCancelados() {
        return new Document("$match", new Document("status", new Document("$ne", CANCELADO)));
    }

    private List<Document> agregar(List<Document> pipeline) {
        return mongoTemplate.getCollection(PEDIDOS).aggregate(pipeline).into(new ArrayList<>());
    }

    private PedidoResumo paraResumo(Document documento) {
        StatusPedido status = statusOuNulo(documento.getString("status"));
        long quantidadeItens = documento.getList("itens", Document.class, List.of()).stream()
                .mapToLong(item -> inteiro(item.get("quantidade")))
                .sum();
        return new PedidoResumo(documento.getObjectId("_id").toHexString(), documento.getString("clienteNome"),
                paraDataHora(documento.getDate("dataHoraEntrada")), status, status == null ? null : status.getDescricao(),
                quantidadeItens, decimal(documento.get("valorTotal")), documento.getString("responsavelEntrada"));
    }

    private LocalDate dataDoPedidoMaisAntigo(int ordem) {
        Document documento = mongoTemplate.getCollection(PEDIDOS).find()
                .sort(new Document("dataHoraEntrada", ordem))
                .projection(new Document("dataHoraEntrada", 1))
                .first();
        return documento == null || documento.getDate("dataHoraEntrada") == null
                ? null
                : paraData(documento.getDate("dataHoraEntrada"));
    }

    private <T> RespostaDashboard<T> responder(FiltroDashboard filtro, T dados) {
        return new RespostaDashboard<>(LocalDateTime.now(relogio), filtro, dados);
    }

    private ZoneId zona() {
        return relogio.getZone();
    }

    private String fusoMongo() {
        String id = zona().normalized().getId();
        return "Z".equals(id) ? "UTC" : id;
    }

    private Date inicioDoDia(LocalDate data) {
        return Date.from(data.atStartOfDay(zona()).toInstant());
    }

    private LocalDate paraData(Date data) {
        return data.toInstant().atZone(zona()).toLocalDate();
    }

    private LocalDateTime paraDataHora(Date data) {
        return data == null ? null : LocalDateTime.ofInstant(data.toInstant(), zona());
    }

    private static Document soma(Object expressao) {
        return new Document("$sum", expressao);
    }

    private static Document condicao(Object se, Object entao, Object senao) {
        return new Document("$cond", Arrays.asList(se, entao, senao));
    }

    private static StatusPedido statusOuNulo(String valor) {
        try {
            return valor == null ? null : StatusPedido.valueOf(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    static long inteiro(Object valor) {
        return valor instanceof Number numero ? numero.longValue() : 0L;
    }

    static BigDecimal decimal(Object valor) {
        BigDecimal resultado;
        if (valor instanceof Decimal128 decimal128) {
            resultado = decimal128.bigDecimalValue();
        } else if (valor instanceof BigDecimal bigDecimal) {
            resultado = bigDecimal;
        } else if (valor instanceof Number numero) {
            resultado = new BigDecimal(numero.toString());
        } else {
            resultado = BigDecimal.ZERO;
        }
        return resultado.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return parte.multiply(CEM).divide(total, 2, RoundingMode.HALF_UP);
    }

    static BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior.signum() == 0) {
            return null;
        }
        return atual.subtract(anterior).multiply(CEM).divide(anterior, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal media(BigDecimal valor, long quantidade) {
        return quantidade == 0 ? BigDecimal.ZERO.setScale(2) : valor.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
    }

    private static Double minutos(Object milissegundos) {
        if (!(milissegundos instanceof Number numero)) {
            return null;
        }
        return BigDecimal.valueOf(numero.doubleValue() / 60000d).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private record Totais(long total, long cancelados, BigDecimal faturamento, BigDecimal faturamentoBruto,
            BigDecimal descontos, BigDecimal valorCancelado, long itens) {

        long validos() {
            return total - cancelados;
        }

        BigDecimal ticketMedio() {
            return media(faturamento, validos());
        }
    }
}
