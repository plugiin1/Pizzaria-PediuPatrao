package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    public List<Produto> listarProdutos() {
        return produtoRepository.findAll();
    }

    public List<Produto> listarAtivos() {
        return produtoRepository.findByAtivoTrueOrderByNomeAsc();
    }

    public void excluir(String id) {
        if (!produtoRepository.existsById(id)) {
            throw new RecursoNaoEncontradoException("Produto não encontrado.");
        }
        produtoRepository.deleteById(id);
    }

    public Produto salvar(Produto produto) {
        validar(produto);
        if (produto.getAtivo() == null) {
            produto.setAtivo(false);
        }
        if (produto.getId() != null && !produtoRepository.existsById(produto.getId())) {
            throw new RecursoNaoEncontradoException("Produto não encontrado para atualização.");
        }
        return produtoRepository.save(produto);
    }

    private void validar(Produto produto) {
        if (produto.getNome() == null || produto.getNome().isBlank()) {
            throw new RegraNegocioException("Informe o nome do produto.");
        }
        if (produto.getTipo() == null || produto.getTipo().isBlank()) {
            throw new RegraNegocioException("Informe o tipo do produto.");
        }
        if (produto.getPreco() == null || produto.getPreco() <= 0) {
            throw new RegraNegocioException("O preço do produto deve ser maior que zero.");
        }
        produto.setNome(produto.getNome().trim());
    }
}
