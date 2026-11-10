package com.umc.pediupatrao.dto.dashboard;

import java.util.List;

public record PaginaPedidos(List<PedidoResumo> conteudo, int pagina, int tamanho, long totalElementos, int totalPaginas) {
}
