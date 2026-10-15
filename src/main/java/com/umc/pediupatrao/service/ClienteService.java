package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    @Transactional
    public Cliente salvar(Cliente cliente) {
        validar(cliente);
        if (cliente.getId() == null || cliente.getId().isBlank()) {
            cliente.setId(null);
            Cliente salvo = clienteRepository.save(cliente);
            auditoriaService.registrar(TipoOperacao.INCLUSAO_CLIENTE, AuditoriaService.ENTIDADE_CLIENTE, salvo.getId(),
                    AlteracaoCampo.comparar(null, camposAuditaveis(salvo)), null);
            return salvo;
        }

        Cliente existente = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado para atualização."));
        List<AlteracaoCampo> alteracoes = AlteracaoCampo.comparar(camposAuditaveis(existente), camposAuditaveis(cliente));
        if (alteracoes.isEmpty()) {
            return existente;
        }
        Cliente salvo = clienteRepository.save(cliente);
        auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, AuditoriaService.ENTIDADE_CLIENTE, salvo.getId(),
                alteracoes, null);
        return salvo;
    }

    @Transactional
    public void excluir(String id) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        clienteRepository.delete(existente);
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_CLIENTE, AuditoriaService.ENTIDADE_CLIENTE, id,
                AlteracaoCampo.comparar(camposAuditaveis(existente), null), null);
    }

    private void validar(Cliente cliente) {
        if (cliente.getNome() == null || cliente.getNome().isBlank()) {
            throw new RegraNegocioException("Informe o nome do cliente.");
        }
        if (cliente.getTelefone() == null || cliente.getTelefone().replaceAll("\\D", "").length() < 10) {
            throw new RegraNegocioException("Informe um telefone válido com DDD.");
        }
    }

    private Map<String, String> camposAuditaveis(Cliente cliente) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("nome", cliente.getNome());
        campos.put("telefone", cliente.getTelefone());
        campos.put("cep", cliente.getCep());
        campos.put("logradouro", cliente.getLogradouro());
        campos.put("numero", cliente.getNumero());
        campos.put("complemento", cliente.getComplemento());
        campos.put("bairro", cliente.getBairro());
        campos.put("cidade", cliente.getCidade());
        campos.put("estado", cliente.getEstado());
        return campos;
    }
}
