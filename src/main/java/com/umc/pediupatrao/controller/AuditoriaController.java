package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.FiltroAuditoria;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.service.AuditoriaService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<RegistroAuditoria> consultar(
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) TipoOperacao operacao,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal) {
        return auditoriaService.consultar(new FiltroAuditoria(entidade, entidadeId, operacao, usuario, dataInicial, dataFinal));
    }
}
