package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {

    public static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20.00");
    private static final int TAMANHO_MINIMO_JUSTIFICATIVA = 10;

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
            ProdutoRepository produtoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
    }

    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public Pedido criarPedido(NovoPedidoRequest request, String responsavel) {
        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new RegraNegocioException("Cliente informado não está cadastrado."));

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setItens(montarItens(request.itens()));
        pedido.setObservacao(request.observacao() == null ? null : request.observacao().trim());
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        pedido.registrarEntrada(responsavel, LocalDateTime.now());
        pedido.recalcularValores();

        return pedidoRepository.save(pedido);
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAllByOrderByDataHoraEntradaDesc();
    }

    public Pedido buscarPorId(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public Pedido atualizarStatus(String id, StatusPedido novoStatus, String responsavel) {
        if (novoStatus == null) {
            throw new RegraNegocioException("Informe o novo status do pedido.");
        }
        Pedido pedido = buscarPorId(id);
        StatusPedido atual = pedido.getStatus();

        if (atual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode ter o status alterado.");
        }
        if (novoStatus == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("O cancelamento deve ser feito pela operação de cancelamento, exclusiva do gerente.");
        }
        if (atual == null || !atual.podeAvancarPara(novoStatus)) {
            throw new RegraNegocioException(String.format("Transição inválida: %s → %s. Próximo status permitido: %s.",
                    atual == null ? "sem status" : atual.getDescricao(), novoStatus.getDescricao(), descreverProximos(atual)));
        }

        pedido.alterarStatus(novoStatus, responsavel, LocalDateTime.now());
        return pedidoRepository.save(pedido);
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual, String responsavel) {
        if (percentual == null || percentual.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("O desconto deve ser maior que zero.");
        }
        if (percentual.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new RegraNegocioException("O desconto não pode ultrapassar " + DESCONTO_MAXIMO.toPlainString() + "% do valor do pedido.");
        }
        Pedido pedido = buscarPorId(id);
        validarAlteravelAntesDaSaida(pedido, "aplicar desconto");

        pedido.aplicarDesconto(percentual, responsavel, LocalDateTime.now());
        return pedidoRepository.save(pedido);
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa, String responsavel) {
        if (justificativa == null || justificativa.trim().length() < TAMANHO_MINIMO_JUSTIFICATIVA) {
            throw new RegraNegocioException("Informe uma justificativa com pelo menos " + TAMANHO_MINIMO_JUSTIFICATIVA + " caracteres.");
        }
        Pedido pedido = buscarPorId(id);
        validarAlteravelAntesDaSaida(pedido, "cancelar");

        pedido.cancelar(justificativa.trim(), responsavel, LocalDateTime.now());
        return pedidoRepository.save(pedido);
    }

    private void validarAlteravelAntesDaSaida(Pedido pedido, String operacao) {
        StatusPedido status = pedido.getStatus();
        if (status == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode ser alterado.");
        }
        if (status == null || !status.isAntesDaSaida()) {
            throw new RegraNegocioException("Não é possível " + operacao + " após a saída do pedido para entrega ou retirada.");
        }
    }

    private String descreverProximos(StatusPedido atual) {
        if (atual == null || atual.getProximos().isEmpty()) {
            return "nenhum";
        }
        return atual.getProximos().stream().map(StatusPedido::getDescricao).collect(Collectors.joining(" ou "));
    }

    private List<ItemPedido> montarItens(List<ItemPedidoRequest> itensRequest) {
        Map<String, Integer> quantidadesPorProduto = new LinkedHashMap<>();
        for (ItemPedidoRequest item : itensRequest) {
            quantidadesPorProduto.merge(item.produtoId(), item.quantidade(), Integer::sum);
        }

        List<ItemPedido> itens = new ArrayList<>();
        quantidadesPorProduto.forEach((produtoId, quantidade) -> {
            Produto produto = produtoRepository.findById(produtoId)
                    .orElseThrow(() -> new RegraNegocioException("Produto informado não está cadastrado."));
            if (!Boolean.TRUE.equals(produto.getAtivo())) {
                throw new RegraNegocioException("O produto " + produto.getNome() + " não está disponível.");
            }
            if (produto.getPreco() == null || produto.getPreco() <= 0) {
                throw new RegraNegocioException("O produto " + produto.getNome() + " está sem preço válido.");
            }
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), quantidade, BigDecimal.valueOf(produto.getPreco())));
        });
        return itens;
    }
}
