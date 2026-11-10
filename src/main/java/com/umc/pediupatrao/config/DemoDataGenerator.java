package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
@Order(Ordered.LOWEST_PRECEDENCE)
public class DemoDataGenerator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataGenerator.class);

    private static final String TELEFONE_MARCADOR = "(11) 90000-0001";
    private static final String GERENTE = "gerente";
    private static final List<String> ATENDENTES = List.of("atendente", "ana.souza", "bruno.lima");
    private static final double[] PESO_POR_HORA = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 4, 3, 1, 0.5, 0.5, 1, 4, 8, 9, 7, 4, 1.5};
    private static final Map<DayOfWeek, Double> PEDIDOS_POR_DIA = Map.of(
            DayOfWeek.MONDAY, 2.0, DayOfWeek.TUESDAY, 2.2, DayOfWeek.WEDNESDAY, 2.8, DayOfWeek.THURSDAY, 3.2,
            DayOfWeek.FRIDAY, 5.0, DayOfWeek.SATURDAY, 6.0, DayOfWeek.SUNDAY, 5.2);
    private static final List<String> JUSTIFICATIVAS = List.of(
            "Cliente desistiu do pedido por telefone",
            "Endereço fora da área de entrega",
            "Pedido duplicado pelo cliente",
            "Falta de ingrediente para a pizza solicitada",
            "Cliente não confirmou o pagamento");
    private static final List<String> OBSERVACOES = List.of(
            "sem cebola", "borda recheada", "trocar azeitona por milho", "bem assada", "enviar sachês de ketchup");
    private static final String[][] CLIENTES = {
        {"Mariana Alves", "Rua das Flores", "120", "Centro"},
        {"Carlos Eduardo Pereira", "Av. Brasil", "1450", "Vila Industrial"},
        {"Fernanda Rocha", "Rua XV de Novembro", "88", "Centro"},
        {"Rafael Nogueira", "Rua José Bonifácio", "301", "Jardim Santista"},
        {"Juliana Martins", "Av. Voluntário Fernando Pinheiro Franco", "512", "Centro"},
        {"Pedro Henrique Costa", "Rua Dr. Deodato Wertheimer", "77", "Mogilar"},
        {"Beatriz Fernandes", "Rua Ipiranga", "940", "Vila Oliveira"},
        {"Lucas Carvalho", "Rua Navajás", "15", "Centro"},
        {"Camila Ribeiro", "Av. Francisco Rodrigues Filho", "2200", "Mogilar"},
        {"Gustavo Teixeira", "Rua Coronel Souza Franco", "430", "Centro"},
        {"Larissa Gomes", "Rua Prof. Flaviano de Melo", "64", "Vila Mogilar"},
        {"Thiago Barbosa", "Av. Japão", "1780", "Vila Nova Socorro"},
        {"Amanda Duarte", "Rua Barão de Jaceguai", "203", "Centro"},
        {"Diego Moreira", "Rua Manoel de Oliveira", "56", "Brás Cubas"},
        {"Patrícia Lopes", "Av. Pref. Carlos Ferreira Lopes", "990", "Vila Industrial"},
        {"Rodrigo Azevedo", "Rua Santana", "318", "Jundiapeba"},
        {"Vanessa Cardoso", "Rua Ricardo Vilela", "12", "Centro"},
        {"Felipe Araújo", "Av. Cívica", "45", "Centro Cívico"},
        {"Aline Correia", "Rua Ângelo Rossi", "700", "Jardim Universo"},
        {"Bruno Mendes", "Rua Tenente Manoel Alves dos Anjos", "160", "Vila Oliveira"}
    };

    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock relogio;
    private final int dias;
    private final Random aleatorio;

    public DemoDataGenerator(ClienteRepository clienteRepository, ProdutoRepository produtoRepository,
            PedidoRepository pedidoRepository, UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            Clock relogio, @Value("${pediupatrao.demo.dias:90}") int dias,
            @Value("${pediupatrao.demo.semente:2026}") long semente) {
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.pedidoRepository = pedidoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.relogio = relogio;
        this.dias = dias;
        this.aleatorio = new Random(semente);
    }

    @Override
    public void run(String... args) {
        if (!clienteRepository.findByTelefone(TELEFONE_MARCADOR).isEmpty()) {
            log.info("Dados de demonstração já existem; nada foi gerado.");
            return;
        }
        List<Produto> produtos = produtoRepository.findByAtivoTrueOrderByNomeAsc();
        if (produtos.isEmpty()) {
            log.warn("Nenhum produto ativo cadastrado; dados de demonstração não foram gerados.");
            return;
        }

        criarAtendentesDemo();
        List<Cliente> clientes = criarClientes();
        Map<String, List<Produto>> produtosPorTipo = agruparPorTipo(produtos);

        LocalDateTime agora = LocalDateTime.now(relogio);
        LocalDate hoje = agora.toLocalDate();
        List<Pedido> pedidos = new ArrayList<>();
        for (int d = dias - 1; d >= 0; d--) {
            LocalDate dia = hoje.minusDays(d);
            double tendencia = 0.8 + 0.4 * (dias - d) / (double) dias;
            int quantidade = poisson(PEDIDOS_POR_DIA.get(dia.getDayOfWeek()) * tendencia);
            for (int i = 0; i < quantidade; i++) {
                LocalDateTime entrada = dia.atTime(sortearHora(), aleatorio.nextInt(60), aleatorio.nextInt(60));
                if (entrada.isAfter(agora)) {
                    continue;
                }
                pedidos.add(montarPedido(entrada, agora, sortear(clientes), produtosPorTipo));
            }
        }
        adicionarPedidosEmAndamento(pedidos, agora, clientes, produtosPorTipo);

        pedidoRepository.saveAll(pedidos);
        log.info("Dados de demonstração gerados: {} clientes, {} pedidos em {} dias", clientes.size(), pedidos.size(), dias);
    }

    private Pedido montarPedido(LocalDateTime entrada, LocalDateTime agora, Cliente cliente,
            Map<String, List<Produto>> produtosPorTipo) {
        Pedido pedido = novoPedido(cliente, produtosPorTipo);
        String atendente = sortear(ATENDENTES);
        pedido.registrarEntrada(atendente, entrada);
        pedido.recalcularValores();

        LocalDateTime preparo = entrada.plusMinutes(2 + aleatorio.nextInt(6));
        LocalDateTime pronto = preparo.plusMinutes(12 + aleatorio.nextInt(15));
        LocalDateTime saida = pronto.plusMinutes(3 + aleatorio.nextInt(10));
        boolean entrega = aleatorio.nextDouble() < 0.65;
        LocalDateTime finalizacao = saida.plusMinutes(entrega ? 12 + aleatorio.nextInt(25) : 1 + aleatorio.nextInt(4));

        if (aleatorio.nextDouble() < 0.12) {
            pedido.aplicarDesconto(BigDecimal.valueOf(5L * (1 + aleatorio.nextInt(4))), GERENTE, preparo.minusMinutes(1));
        }
        if (aleatorio.nextDouble() < 0.07) {
            StatusPedido ate = sortear(List.of(StatusPedido.RECEBIDO, StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO));
            if (ate != StatusPedido.RECEBIDO) {
                pedido.alterarStatus(StatusPedido.EM_PREPARACAO, atendente, preparo);
            }
            if (ate == StatusPedido.PRONTO) {
                pedido.alterarStatus(StatusPedido.PRONTO, atendente, pronto);
            }
            pedido.cancelar(sortear(JUSTIFICATIVAS), GERENTE, (ate == StatusPedido.PRONTO ? pronto : preparo).plusMinutes(3));
            return pedido;
        }

        avancarAte(pedido, atendente, agora, List.of(
                Map.entry(StatusPedido.EM_PREPARACAO, preparo),
                Map.entry(StatusPedido.PRONTO, pronto),
                Map.entry(entrega ? StatusPedido.SAIU_PARA_ENTREGA : StatusPedido.RETIRADO, saida),
                Map.entry(StatusPedido.FINALIZADO, finalizacao)));
        return pedido;
    }

    private void adicionarPedidosEmAndamento(List<Pedido> pedidos, LocalDateTime agora, List<Cliente> clientes,
            Map<String, List<Produto>> produtosPorTipo) {
        int[] minutosAtras = {4, 11, 19, 27, 62, 75};
        StatusPedido[] situacao = {StatusPedido.RECEBIDO, StatusPedido.EM_PREPARACAO, StatusPedido.EM_PREPARACAO,
            StatusPedido.PRONTO, StatusPedido.EM_PREPARACAO, StatusPedido.RECEBIDO};
        for (int i = 0; i < minutosAtras.length; i++) {
            LocalDateTime entrada = agora.minusMinutes(minutosAtras[i]);
            Pedido pedido = novoPedido(sortear(clientes), produtosPorTipo);
            String atendente = sortear(ATENDENTES);
            pedido.registrarEntrada(atendente, entrada);
            pedido.recalcularValores();
            if (situacao[i] != StatusPedido.RECEBIDO) {
                pedido.alterarStatus(StatusPedido.EM_PREPARACAO, atendente, entrada.plusMinutes(2));
            }
            if (situacao[i] == StatusPedido.PRONTO) {
                pedido.alterarStatus(StatusPedido.PRONTO, atendente, entrada.plusMinutes(minutosAtras[i] - 2));
            }
            pedidos.add(pedido);
        }
    }

    private void avancarAte(Pedido pedido, String atendente, LocalDateTime agora,
            List<Map.Entry<StatusPedido, LocalDateTime>> etapas) {
        for (Map.Entry<StatusPedido, LocalDateTime> etapa : etapas) {
            if (etapa.getValue().isAfter(agora)) {
                return;
            }
            pedido.alterarStatus(etapa.getKey(), atendente, etapa.getValue());
        }
    }

    private Pedido novoPedido(Cliente cliente, Map<String, List<Produto>> produtosPorTipo) {
        Map<String, ItemPedido> itens = new LinkedHashMap<>();
        int pizzas = 1 + (aleatorio.nextDouble() < 0.35 ? 1 : 0) + (aleatorio.nextDouble() < 0.1 ? 1 : 0);
        adicionarItens(itens, produtosPorTipo.get("Pizza"), pizzas, 1);
        if (aleatorio.nextDouble() < 0.7) {
            adicionarItens(itens, produtosPorTipo.get("Bebida"), 1, 1 + aleatorio.nextInt(2));
        }
        if (aleatorio.nextDouble() < 0.3) {
            adicionarItens(itens, produtosPorTipo.get("Esfiha"), 1, 2 + aleatorio.nextInt(5));
        }
        if (itens.isEmpty()) {
            adicionarItens(itens, produtosPorTipo.values().iterator().next(), 1, 1);
        }

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setItens(new ArrayList<>(itens.values()));
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        if (aleatorio.nextDouble() < 0.15) {
            pedido.setObservacao(sortear(OBSERVACOES));
        }
        return pedido;
    }

    private void adicionarItens(Map<String, ItemPedido> itens, List<Produto> opcoes, int sorteios, int quantidade) {
        if (opcoes == null || opcoes.isEmpty()) {
            return;
        }
        for (int i = 0; i < sorteios; i++) {
            Produto produto = sortear(opcoes);
            ItemPedido existente = itens.get(produto.getId());
            int total = quantidade + (existente == null ? 0 : existente.getQuantidade());
            itens.put(produto.getId(), new ItemPedido(produto.getId(), produto.getNome(), produto.getTipo(), total,
                    BigDecimal.valueOf(produto.getPreco())));
        }
    }

    private Map<String, List<Produto>> agruparPorTipo(List<Produto> produtos) {
        Map<String, List<Produto>> porTipo = new LinkedHashMap<>();
        for (Produto produto : produtos) {
            porTipo.computeIfAbsent(produto.getTipo(), tipo -> new ArrayList<>()).add(produto);
        }
        return porTipo;
    }

    private List<Cliente> criarClientes() {
        List<Cliente> clientes = new ArrayList<>();
        for (int i = 0; i < CLIENTES.length; i++) {
            Cliente cliente = new Cliente();
            cliente.setNome(CLIENTES[i][0]);
            cliente.setTelefone(String.format("(11) 90000-%04d", i + 1));
            cliente.setLogradouro(CLIENTES[i][1]);
            cliente.setNumero(CLIENTES[i][2]);
            cliente.setBairro(CLIENTES[i][3]);
            cliente.setCidade("Mogi das Cruzes");
            cliente.setEstado("SP");
            clientes.add(cliente);
        }
        return clienteRepository.saveAll(clientes);
    }

    private void criarAtendentesDemo() {
        for (String username : ATENDENTES) {
            if (usuarioRepository.existsByUsername(username)) {
                continue;
            }
            Usuario usuario = new Usuario();
            usuario.setUsername(username);
            usuario.setPassword(passwordEncoder.encode("teste123"));
            usuario.setPerfil(Perfil.ATENDENTE);
            usuarioRepository.save(usuario);
        }
    }

    private int sortearHora() {
        double total = 0;
        for (double peso : PESO_POR_HORA) {
            total += peso;
        }
        double alvo = aleatorio.nextDouble() * total;
        for (int hora = 0; hora < PESO_POR_HORA.length; hora++) {
            alvo -= PESO_POR_HORA[hora];
            if (alvo <= 0) {
                return hora;
            }
        }
        return 20;
    }

    private int poisson(double media) {
        double limite = Math.exp(-media);
        double produto = aleatorio.nextDouble();
        int quantidade = 0;
        while (produto > limite) {
            produto *= aleatorio.nextDouble();
            quantidade++;
        }
        return quantidade;
    }

    private <T> T sortear(List<T> opcoes) {
        return opcoes.get(aleatorio.nextInt(opcoes.size()));
    }
}
