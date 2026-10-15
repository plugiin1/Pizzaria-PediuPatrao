package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private ClienteService clienteService;

    @Captor
    private ArgumentCaptor<List<AlteracaoCampo>> alteracoesCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocacao -> {
            Cliente cliente = invocacao.getArgument(0);
            if (cliente.getId() == null) {
                cliente.setId("cliente-1");
            }
            return cliente;
        });
    }

    @Test
    void alteracaoDeEnderecoETelefoneRegistraValoresAnterioresENovos() {
        when(clienteRepository.findById("cliente-1")).thenReturn(Optional.of(cliente("Rua das Flores", "(11) 98888-1111")));

        clienteService.salvar(cliente("Av. Brasil", "(11) 97777-2222"));

        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_CLIENTE), eq(AuditoriaService.ENTIDADE_CLIENTE),
                eq("cliente-1"), alteracoesCaptor.capture(), isNull());
        assertThat(alteracoesCaptor.getValue())
                .extracting(AlteracaoCampo::getCampo, AlteracaoCampo::getValorAnterior, AlteracaoCampo::getValorNovo)
                .containsExactly(
                        tuple("telefone", "(11) 98888-1111", "(11) 97777-2222"),
                        tuple("logradouro", "Rua das Flores", "Av. Brasil"));
    }

    @Test
    void salvarSemAlteracoesNaoGeraAuditoria() {
        when(clienteRepository.findById("cliente-1")).thenReturn(Optional.of(cliente("Rua das Flores", "(11) 98888-1111")));

        clienteService.salvar(cliente("Rua das Flores", "(11) 98888-1111"));

        verify(clienteRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void inclusaoDeClienteEAuditada() {
        Cliente novo = cliente("Rua das Flores", "(11) 98888-1111");
        novo.setId(null);

        clienteService.salvar(novo);

        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_CLIENTE), eq(AuditoriaService.ENTIDADE_CLIENTE),
                eq("cliente-1"), alteracoesCaptor.capture(), isNull());
        assertThat(alteracoesCaptor.getValue()).extracting(AlteracaoCampo::getCampo).contains("nome", "telefone", "logradouro");
    }

    @Test
    void exclusaoDeClienteEAuditada() {
        Cliente existente = cliente("Rua das Flores", "(11) 98888-1111");
        when(clienteRepository.findById("cliente-1")).thenReturn(Optional.of(existente));

        clienteService.excluir("cliente-1");

        verify(clienteRepository).delete(existente);
        verify(auditoriaService).registrar(eq(TipoOperacao.EXCLUSAO_CLIENTE), eq(AuditoriaService.ENTIDADE_CLIENTE),
                eq("cliente-1"), any(), isNull());
    }

    @Test
    void telefoneSemDddERejeitado() {
        Cliente cliente = cliente("Rua das Flores", "9999-1111");

        assertThatThrownBy(() -> clienteService.salvar(cliente))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("telefone");
        verifyNoInteractions(auditoriaService);
    }

    private Cliente cliente(String logradouro, String telefone) {
        Cliente cliente = new Cliente();
        cliente.setId("cliente-1");
        cliente.setNome("Maria Souza");
        cliente.setTelefone(telefone);
        cliente.setLogradouro(logradouro);
        cliente.setNumero("10");
        cliente.setCidade("Mogi das Cruzes");
        cliente.setEstado("SP");
        return cliente;
    }
}
