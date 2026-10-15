package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.FiltroAuditoria;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    public static final String ENTIDADE_PEDIDO = "PEDIDO";
    public static final String ENTIDADE_CLIENTE = "CLIENTE";
    public static final String ENTIDADE_USUARIO = "USUARIO";

    private static final int LIMITE_CONSULTA = 500;
    private static final Set<String> CAMPOS_TELEFONE = Set.of("telefone");

    private final AuditoriaRepository auditoriaRepository;
    private final MongoTemplate mongoTemplate;

    public AuditoriaService(AuditoriaRepository auditoriaRepository, MongoTemplate mongoTemplate) {
        this.auditoriaRepository = auditoriaRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public RegistroAuditoria registrar(TipoOperacao operacao, String entidade, String entidadeId,
            List<AlteracaoCampo> alteracoes, String justificativa) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        String usuario = autenticacao != null && autenticacao.isAuthenticated() ? autenticacao.getName() : "sistema";
        String perfil = autenticacao == null ? "SISTEMA" : autenticacao.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(autoridade -> autoridade.startsWith("ROLE_"))
                .map(autoridade -> autoridade.substring("ROLE_".length()))
                .findFirst()
                .orElse("SISTEMA");

        RegistroAuditoria registro = new RegistroAuditoria(null, usuario, perfil, LocalDateTime.now(), operacao,
                entidade, entidadeId, alteracoes, justificativa);
        return auditoriaRepository.insert(registro);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<RegistroAuditoria> consultar(FiltroAuditoria filtro) {
        Query query = new Query();
        if (filtro != null) {
            if (temValor(filtro.entidade())) {
                query.addCriteria(Criteria.where("entidade").is(filtro.entidade().trim().toUpperCase()));
            }
            if (temValor(filtro.entidadeId())) {
                query.addCriteria(Criteria.where("entidadeId").is(filtro.entidadeId().trim()));
            }
            if (filtro.operacao() != null) {
                query.addCriteria(Criteria.where("operacao").is(filtro.operacao()));
            }
            if (temValor(filtro.usuario())) {
                query.addCriteria(Criteria.where("usuario").is(filtro.usuario().trim()));
            }
            if (filtro.dataInicial() != null || filtro.dataFinal() != null) {
                Criteria periodo = Criteria.where("dataHora");
                if (filtro.dataInicial() != null) {
                    periodo.gte(filtro.dataInicial().atStartOfDay());
                }
                if (filtro.dataFinal() != null) {
                    periodo.lte(filtro.dataFinal().atTime(LocalTime.MAX));
                }
                query.addCriteria(periodo);
            }
        }
        query.with(Sort.by(Sort.Direction.DESC, "dataHora")).limit(LIMITE_CONSULTA);

        return mongoTemplate.find(query, RegistroAuditoria.class).stream()
                .map(this::protegerDadosPessoais)
                .toList();
    }

    private RegistroAuditoria protegerDadosPessoais(RegistroAuditoria registro) {
        List<AlteracaoCampo> alteracoes = registro.getAlteracoes().stream()
                .map(alteracao -> CAMPOS_TELEFONE.contains(alteracao.getCampo())
                        ? new AlteracaoCampo(alteracao.getCampo(), mascararTelefone(alteracao.getValorAnterior()),
                                mascararTelefone(alteracao.getValorNovo()))
                        : alteracao)
                .toList();
        return new RegistroAuditoria(registro.getId(), registro.getUsuario(), registro.getPerfil(), registro.getDataHora(),
                registro.getOperacao(), registro.getEntidade(), registro.getEntidadeId(), alteracoes, registro.getJustificativa());
    }

    static String mascararTelefone(String telefone) {
        if (telefone == null) {
            return null;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() <= 4) {
            return "****";
        }
        return "*".repeat(digitos.length() - 4) + digitos.substring(digitos.length() - 4);
    }

    private boolean temValor(String valor) {
        return valor != null && !valor.isBlank();
    }
}
