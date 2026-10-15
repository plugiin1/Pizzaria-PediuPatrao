package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface AuditoriaRepository extends Repository<RegistroAuditoria, String> {

    RegistroAuditoria insert(RegistroAuditoria registro);

    List<RegistroAuditoria> findByEntidadeAndEntidadeIdOrderByDataHoraDesc(String entidade, String entidadeId);
}
