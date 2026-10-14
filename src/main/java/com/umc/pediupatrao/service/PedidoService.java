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
import org.springframework.stereotype.Service;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
            ProdutoRepository produtoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
    }

    public Pedido criarPedido(NovoPedidoRequest request, String responsavel) {
        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new RegraNegocioException("Cliente informado não está cadastrado."));

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setItens(montarItens(request.itens()));
        pedido.setObservacao(request.observacao() == null ? null : request.observacao().trim());
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDataHoraEntrada(LocalDateTime.now());
        pedido.setResponsavelEntrada(responsavel);
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

    public Pedido atualizarStatus(String id, StatusPedido status) {
        Pedido pedido = buscarPorId(id);
        pedido.setStatus(status);
        return pedidoRepository.save(pedido);
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
