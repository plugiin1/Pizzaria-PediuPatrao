package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltro;
import com.umc.pediupatrao.dto.dashboard.OrdenacaoRanking;
import com.umc.pediupatrao.dto.dashboard.PaginaPedidos;
import com.umc.pediupatrao.dto.dashboard.ParametrosDashboard;
import com.umc.pediupatrao.dto.dashboard.ProdutoVendido;
import com.umc.pediupatrao.dto.dashboard.RespostaDashboard;
import com.umc.pediupatrao.dto.dashboard.ResumoVendas;
import com.umc.pediupatrao.dto.dashboard.SerieVendas;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidos;
import com.umc.pediupatrao.dto.dashboard.VendasPorHorario;
import com.umc.pediupatrao.service.DashboardService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    public RespostaDashboard<ResumoVendas> resumo(@ModelAttribute ParametrosDashboard parametros) {
        return dashboardService.resumo(parametros);
    }

    @GetMapping("/vendas")
    public RespostaDashboard<SerieVendas> vendas(@ModelAttribute ParametrosDashboard parametros,
            @RequestParam(defaultValue = "DIA") Agrupamento agrupamento) {
        return dashboardService.vendas(parametros, agrupamento);
    }

    @GetMapping("/vendas/horarios")
    public RespostaDashboard<List<VendasPorHorario>> vendasPorHorario(@ModelAttribute ParametrosDashboard parametros) {
        return dashboardService.vendasPorHorario(parametros);
    }

    @GetMapping("/pedidos/status")
    public RespostaDashboard<SituacaoPedidos> situacaoPedidos(@ModelAttribute ParametrosDashboard parametros) {
        return dashboardService.situacaoPedidos(parametros);
    }

    @GetMapping("/produtos/mais-vendidos")
    public RespostaDashboard<List<ProdutoVendido>> produtosMaisVendidos(@ModelAttribute ParametrosDashboard parametros,
            @RequestParam(defaultValue = "10") Integer limite,
            @RequestParam(defaultValue = "QUANTIDADE") OrdenacaoRanking ordenarPor) {
        return dashboardService.produtosMaisVendidos(parametros, limite, ordenarPor);
    }

    @GetMapping("/pedidos")
    public RespostaDashboard<PaginaPedidos> pedidos(@ModelAttribute ParametrosDashboard parametros,
            @RequestParam(defaultValue = "0") Integer pagina,
            @RequestParam(defaultValue = "20") Integer tamanho) {
        return dashboardService.pedidos(parametros, pagina, tamanho);
    }

    @GetMapping("/filtros")
    public RespostaDashboard<OpcoesFiltro> filtros() {
        return dashboardService.opcoesFiltro();
    }
}
