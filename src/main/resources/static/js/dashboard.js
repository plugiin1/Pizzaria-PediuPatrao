(function () {
    const API = '/api/dashboard';
    const PERIODO_PADRAO = 30;
    const PERIODO_MAXIMO = 366;
    const TAMANHO_PAGINA = 10;

    const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    const numero = new Intl.NumberFormat('pt-BR');
    const decimal = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });
    const dataHora = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
    const hora = new Intl.DateTimeFormat('pt-BR', { timeStyle: 'medium' });

    const CORES_STATUS = {
        RECEBIDO: '#0d6efd',
        EM_PREPARACAO: '#ffc107',
        PRONTO: '#0dcaf0',
        SAIU_PARA_ENTREGA: '#6610f2',
        RETIRADO: '#20c997',
        FINALIZADO: '#198754',
        CANCELADO: '#dc3545'
    };
    const BADGES_STATUS = {
        RECEBIDO: 'bg-primary',
        EM_PREPARACAO: 'bg-warning text-dark',
        PRONTO: 'bg-info text-dark',
        SAIU_PARA_ENTREGA: 'bg-indigo',
        RETIRADO: 'bg-teal',
        FINALIZADO: 'bg-success',
        CANCELADO: 'bg-danger'
    };

    const estado = {
        agrupamento: 'DIA',
        ordenacao: 'QUANTIDADE',
        pagina: 0,
        status: [],
        controlador: null,
        temporizador: null,
        ultimaCarga: 0,
        graficos: {}
    };

    const $ = (id) => document.getElementById(id);

    function escapar(texto) {
        const div = document.createElement('div');
        div.textContent = texto == null ? '' : String(texto);
        return div.innerHTML;
    }

    function hojeIso() {
        const agora = new Date();
        agora.setMinutes(agora.getMinutes() - agora.getTimezoneOffset());
        return agora.toISOString().slice(0, 10);
    }

    function deslocarIso(iso, dias) {
        const data = new Date(`${iso}T12:00:00`);
        data.setDate(data.getDate() + dias);
        return data.toISOString().slice(0, 10);
    }

    function diasEntre(inicio, fim) {
        return Math.round((new Date(`${fim}T12:00:00`) - new Date(`${inicio}T12:00:00`)) / 86400000) + 1;
    }

    function formatarData(iso) {
        const [ano, mes, dia] = iso.split('-');
        return `${dia}/${mes}/${ano}`;
    }

    function formatarMinutos(minutos) {
        if (minutos == null) {
            return '—';
        }
        const total = Math.round(minutos);
        return total < 60 ? `${total} min` : `${Math.floor(total / 60)} h ${String(total % 60).padStart(2, '0')} min`;
    }

    function lerUrl() {
        const parametros = new URLSearchParams(window.location.search);
        const hoje = hojeIso();
        $('dataFinal').value = parametros.get('dataFinal') || hoje;
        $('dataInicial').value = parametros.get('dataInicial') || deslocarIso($('dataFinal').value, -(PERIODO_PADRAO - 1));
        estado.status = parametros.getAll('status');
        estado.agrupamento = parametros.get('agrupamento') || 'DIA';
        return { tipoProduto: parametros.get('tipoProduto') || '', responsavel: parametros.get('responsavel') || '' };
    }

    function parametrosFiltro() {
        const parametros = new URLSearchParams();
        parametros.set('dataInicial', $('dataInicial').value);
        parametros.set('dataFinal', $('dataFinal').value);
        if ($('tipoProduto').value) {
            parametros.set('tipoProduto', $('tipoProduto').value);
        }
        if ($('responsavel').value) {
            parametros.set('responsavel', $('responsavel').value);
        }
        estado.status.forEach((status) => parametros.append('status', status));
        return parametros;
    }

    function validarFiltros() {
        const inicio = $('dataInicial').value;
        const fim = $('dataFinal').value;
        let mensagem = '';
        if (!inicio || !fim) {
            mensagem = 'Informe a data inicial e a data final.';
        } else if (inicio > fim) {
            mensagem = 'A data inicial não pode ser posterior à data final.';
        } else if (diasEntre(inicio, fim) > PERIODO_MAXIMO) {
            mensagem = `O período não pode ultrapassar ${PERIODO_MAXIMO} dias.`;
        }
        $('erroFiltro').textContent = mensagem;
        $('erroFiltro').classList.toggle('d-none', !mensagem);
        if (mensagem && !estado.ultimaCarga) {
            $('periodoDescricao').textContent = 'Ajuste os filtros para carregar os dados.';
        }
        return !mensagem;
    }

    async function requisitar(caminho, extras = {}) {
        const parametros = parametrosFiltro();
        Object.entries(extras).forEach(([chave, valor]) => parametros.set(chave, valor));
        const resposta = await fetch(`${API}${caminho}?${parametros}`, {
            headers: { Accept: 'application/json' },
            signal: estado.controlador.signal
        });
        if (resposta.status === 401) {
            window.location.href = '/login';
            throw new Error('Sessão expirada. Faça login novamente.');
        }
        if (!resposta.ok) {
            throw new Error(await lerErroApi(resposta));
        }
        return resposta.json();
    }

    function marcarCarregando(secao, carregando) {
        document.querySelectorAll(`[data-secao="${secao}"]`).forEach((elemento) => elemento.classList.toggle('carregando', carregando));
    }

    async function carregarSecao(secao, caminho, extras, desenhar) {
        marcarCarregando(secao, true);
        try {
            const resposta = await requisitar(caminho, extras);
            desenhar(resposta.dados, resposta);
            return resposta;
        } finally {
            marcarCarregando(secao, false);
        }
    }

    async function atualizar() {
        if (!validarFiltros()) {
            return;
        }
        if (estado.controlador) {
            estado.controlador.abort();
        }
        estado.controlador = new AbortController();
        history.replaceState(null, '', `${window.location.pathname}?${parametrosFiltro()}&agrupamento=${estado.agrupamento}`);

        const resultados = await Promise.allSettled([
            carregarSecao('resumo', '/resumo', {}, desenharResumo),
            carregarSecao('vendas', '/vendas', { agrupamento: estado.agrupamento }, desenharVendas),
            carregarSecao('horarios', '/vendas/horarios', {}, desenharHorarios),
            carregarSecao('status', '/pedidos/status', {}, desenharStatus),
            carregarSecao('ranking', '/produtos/mais-vendidos', { limite: 10, ordenarPor: estado.ordenacao }, desenharRanking),
            carregarSecao('pedidos', '/pedidos', { pagina: estado.pagina, tamanho: TAMANHO_PAGINA }, desenharPedidos)
        ]);

        const erros = resultados
                .filter((resultado) => resultado.status === 'rejected' && resultado.reason.name !== 'AbortError')
                .map((resultado) => resultado.reason.message);
        mostrarErro([...new Set(erros)].join(' '));

        const sucesso = resultados.find((resultado) => resultado.status === 'fulfilled');
        if (sucesso) {
            estado.ultimaCarga = Date.now();
            $('ultimaAtualizacao').textContent = `Atualizado às ${hora.format(new Date(sucesso.value.geradoEm))}`;
            const filtro = sucesso.value.filtro;
            $('periodoDescricao').textContent =
                `${formatarData(filtro.dataInicial)} a ${formatarData(filtro.dataFinal)} · ${filtro.status.length ? filtro.status.length + ' status' : 'todos os status'}`
                + `${filtro.tipoProduto ? ' · ' + filtro.tipoProduto : ''}${filtro.responsavel ? ' · ' + filtro.responsavel : ''}`;
        }
        destacarAtalho();
    }

    function mostrarErro(mensagem) {
        $('mensagemErro').textContent = mensagem ? `Não foi possível atualizar parte do dashboard: ${mensagem}` : '';
        $('alertaErro').classList.toggle('d-none', !mensagem);
        $('alertaErro').classList.toggle('d-flex', !!mensagem);
    }

    function variacao(elemento, valor) {
        if (valor == null) {
            elemento.className = 'small text-muted';
            elemento.textContent = 'sem dados no período anterior';
            return;
        }
        const positivo = valor >= 0;
        elemento.className = `small ${positivo ? 'text-success' : 'text-danger'}`;
        elemento.innerHTML = `<i class="bi bi-arrow-${positivo ? 'up' : 'down'}-right"></i> ${positivo ? '+' : ''}${decimal.format(valor)}% vs período anterior`;
    }

    function desenharResumo(dados) {
        $('kpiFaturamento').textContent = moeda.format(dados.faturamento);
        $('kpiPedidos').textContent = numero.format(dados.pedidosValidos);
        $('kpiTicket').textContent = moeda.format(dados.ticketMedio);
        $('kpiCancelados').textContent = numero.format(dados.pedidosCancelados);
        $('kpiTaxaCancelamento').textContent = `${decimal.format(dados.taxaCancelamento)}% dos pedidos · ${moeda.format(dados.valorCancelado)}`;
        $('kpiDescontos').textContent = moeda.format(dados.totalDescontos);
        $('kpiBruto').textContent = `Faturamento bruto ${moeda.format(dados.faturamentoBruto)}`;
        $('kpiItens').textContent = numero.format(dados.itensVendidos);
        $('kpiItensPorPedido').textContent = dados.pedidosValidos
            ? `${decimal.format(dados.itensVendidos / dados.pedidosValidos)} itens por pedido`
            : '';
        variacao($('varFaturamento'), dados.periodoAnterior.variacaoFaturamento);
        variacao($('varPedidos'), dados.periodoAnterior.variacaoPedidos);
        variacao($('varTicket'), dados.periodoAnterior.variacaoTicketMedio);
    }

    function grafico(nome, canvasId, configuracao) {
        const existente = estado.graficos[nome];
        if (existente && existente.config.type === configuracao.type) {
            existente.data = configuracao.data;
            existente.update();
            return;
        }
        if (existente) {
            existente.destroy();
        }
        estado.graficos[nome] = new Chart($(canvasId), configuracao);
    }

    function semDados(canvasId, vazio, mensagem) {
        const area = $(canvasId).parentElement;
        let aviso = area.querySelector('.vazio');
        if (!aviso) {
            aviso = document.createElement('div');
            aviso.className = 'vazio position-absolute top-50 start-50 translate-middle';
            area.appendChild(aviso);
        }
        aviso.textContent = mensagem;
        aviso.classList.toggle('d-none', !vazio);
        $(canvasId).style.visibility = vazio ? 'hidden' : 'visible';
    }

    function desenharVendas(dados) {
        const pontos = dados.pontos;
        semDados('graficoVendas', pontos.every((p) => p.pedidos === 0 && p.cancelados === 0), 'Nenhuma venda no período selecionado.');
        grafico('vendas', 'graficoVendas', {
            type: 'bar',
            data: {
                labels: pontos.map((p) => p.rotulo),
                datasets: [
                    { type: 'bar', label: 'Faturamento', data: pontos.map((p) => p.faturamento), backgroundColor: 'rgba(13, 110, 253, 0.65)', yAxisID: 'y', order: 2 },
                    { type: 'line', label: 'Pedidos', data: pontos.map((p) => p.pedidos), borderColor: '#fd7e14', backgroundColor: '#fd7e14', cubicInterpolationMode: 'monotone', pointRadius: 2, yAxisID: 'y1', order: 1 }
                ]
            },
            options: {
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                plugins: {
                    tooltip: {
                        callbacks: {
                            label: (contexto) => contexto.dataset.yAxisID === 'y'
                                ? `Faturamento: ${moeda.format(contexto.parsed.y)}`
                                : `Pedidos: ${numero.format(contexto.parsed.y)}`,
                            afterBody: (itens) => {
                                const ponto = pontos[itens[0].dataIndex];
                                return [`Ticket médio: ${moeda.format(ponto.ticketMedio)}`, `Cancelados: ${ponto.cancelados}`];
                            }
                        }
                    }
                },
                scales: {
                    y: { beginAtZero: true, ticks: { callback: (valor) => moeda.format(valor) } },
                    y1: { beginAtZero: true, position: 'right', grid: { drawOnChartArea: false }, ticks: { precision: 0 } }
                }
            }
        });
    }

    function desenharHorarios(dados) {
        const total = dados.reduce((soma, h) => soma + h.pedidos, 0);
        const inicio = dados.findIndex((h) => h.pedidos > 0);
        const fim = dados.length - 1 - [...dados].reverse().findIndex((h) => h.pedidos > 0);
        const visiveis = total ? dados.slice(Math.max(0, inicio - 1), Math.min(dados.length, fim + 2)) : dados.slice(10, 24);
        semDados('graficoHorarios', total === 0, 'Sem pedidos no período.');
        grafico('horarios', 'graficoHorarios', {
            type: 'bar',
            data: {
                labels: visiveis.map((h) => `${String(h.hora).padStart(2, '0')}h`),
                datasets: [{ label: 'Pedidos', data: visiveis.map((h) => h.pedidos), backgroundColor: 'rgba(25, 135, 84, 0.7)' }]
            },
            options: {
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: { callbacks: { afterLabel: (contexto) => `Faturamento: ${moeda.format(visiveis[contexto.dataIndex].faturamento)}` } }
                },
                scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
            }
        });
        const pico = dados.reduce((maior, h) => (h.pedidos > maior.pedidos ? h : maior), dados[0]);
        $('horarioPico').textContent = total
            ? `Horário de pico: ${String(pico.hora).padStart(2, '0')}h, com ${numero.format(pico.pedidos)} pedidos (${decimal.format(pico.pedidos * 100 / total)}% do total).`
            : '';
    }

    function desenharStatus(dados) {
        $('kpiAndamento').textContent = numero.format(dados.emAndamento);
        $('kpiAtrasados').textContent = numero.format(dados.atrasados);
        $('kpiAtrasados').classList.toggle('text-danger', dados.atrasados > 0);
        $('kpiTempoSaida').textContent = formatarMinutos(dados.tempoMedioAteSaidaMinutos);
        $('kpiTempoFinal').textContent = formatarMinutos(dados.tempoMedioAteFinalizacaoMinutos);
        $('legendaAtraso').textContent = `Atrasados: pedidos ainda não despachados há mais de ${dados.limiteAtrasoMinutos} minutos. Tempos médios calculados desde a entrada do pedido.`;

        const comPedidos = dados.porStatus.filter((s) => s.quantidade > 0);
        semDados('graficoStatus', dados.total === 0, 'Sem pedidos.');
        grafico('status', 'graficoStatus', {
            type: 'doughnut',
            data: {
                labels: comPedidos.map((s) => s.descricao),
                datasets: [{ data: comPedidos.map((s) => s.quantidade), backgroundColor: comPedidos.map((s) => CORES_STATUS[s.status]) }]
            },
            options: { maintainAspectRatio: false, cutout: '60%', plugins: { legend: { display: false } } }
        });

        $('tabelaStatus').innerHTML = dados.porStatus.map((s) => `
            <tr class="${s.quantidade ? '' : 'text-muted'}">
                <td><span class="d-inline-block rounded-circle me-2" style="width:10px;height:10px;background:${CORES_STATUS[s.status]}"></span>${escapar(s.descricao)}</td>
                <td class="text-end fw-medium">${numero.format(s.quantidade)}</td>
                <td class="text-end text-muted">${decimal.format(s.percentual)}%</td>
            </tr>`).join('');
    }

    function desenharRanking(dados) {
        if (!dados.length) {
            $('tabelaRanking').innerHTML = '<tr><td colspan="5" class="vazio">Nenhum produto vendido no período selecionado.</td></tr>';
            return;
        }
        const porFaturamento = estado.ordenacao === 'FATURAMENTO';
        $('tabelaRanking').innerHTML = dados.map((p) => {
            const participacao = porFaturamento ? p.participacaoFaturamento : p.participacaoQuantidade;
            return `
            <tr>
                <td class="text-muted">${p.posicao}º</td>
                <td>
                    <div class="fw-medium">${escapar(p.nome)}</div>
                    <div class="text-muted">${escapar(p.tipo || 'Sem categoria')} · ${numero.format(p.pedidos)} pedido(s)</div>
                </td>
                <td class="text-end">${numero.format(p.quantidade)}</td>
                <td class="text-end">${moeda.format(p.faturamento)}</td>
                <td>
                    <div class="d-flex align-items-center gap-2">
                        <div class="progress flex-fill barra-participacao">
                            <div class="progress-bar" style="width:${participacao}%"></div>
                        </div>
                        <span class="text-muted">${decimal.format(participacao)}%</span>
                    </div>
                </td>
            </tr>`;
        }).join('');
    }

    function desenharPedidos(dados) {
        $('totalPedidosLista').textContent = `${numero.format(dados.totalElementos)} pedido(s)`;
        $('tabelaPedidos').innerHTML = dados.conteudo.length
            ? dados.conteudo.map((p) => `
                <tr>
                    <td class="ps-3">${p.dataHoraEntrada ? dataHora.format(new Date(p.dataHoraEntrada)) : '—'}</td>
                    <td>${escapar(p.cliente || '—')}</td>
                    <td>${escapar(p.responsavel || '—')}</td>
                    <td class="text-end">${numero.format(p.quantidadeItens)}</td>
                    <td class="text-end">${moeda.format(p.valorTotal)}</td>
                    <td class="pe-3"><span class="badge ${BADGES_STATUS[p.status] || 'bg-secondary'}">${escapar(p.statusDescricao || p.status || '—')}</span></td>
                </tr>`).join('')
            : '<tr><td colspan="6" class="vazio">Nenhum pedido encontrado para os filtros selecionados.</td></tr>';
        const totalPaginas = Math.max(dados.totalPaginas, 1);
        $('paginaAtual').textContent = `Página ${dados.pagina + 1} de ${totalPaginas}`;
        $('paginaAnterior').disabled = dados.pagina === 0;
        $('proximaPagina').disabled = dados.pagina + 1 >= totalPaginas;
    }

    async function atualizarPedidos() {
        if (!estado.controlador) {
            estado.controlador = new AbortController();
        }
        try {
            await carregarSecao('pedidos', '/pedidos', { pagina: estado.pagina, tamanho: TAMANHO_PAGINA }, desenharPedidos);
            mostrarErro('');
        } catch (erro) {
            if (erro.name !== 'AbortError') {
                mostrarErro(erro.message);
            }
        }
    }

    async function carregarOpcoes(selecionados) {
        try {
            const resposta = await fetch(`${API}/filtros`, { headers: { Accept: 'application/json' } });
            if (resposta.status === 401) {
                window.location.href = '/login';
                return;
            }
            if (!resposta.ok) {
                throw new Error(await lerErroApi(resposta));
            }
            const opcoes = (await resposta.json()).dados;
            preencherSelect('tipoProduto', opcoes.tiposProduto, selecionados.tipoProduto);
            preencherSelect('responsavel', opcoes.responsaveis, selecionados.responsavel);
            $('opcoesStatus').innerHTML = opcoes.status.map((s) => `
                <div class="form-check">
                    <input class="form-check-input" type="checkbox" value="${s.valor}" id="status-${s.valor}" ${estado.status.includes(s.valor) ? 'checked' : ''}>
                    <label class="form-check-label" for="status-${s.valor}">${escapar(s.descricao)}</label>
                </div>`).join('');
            atualizarRotuloStatus();
        } catch (erro) {
            mostrarErro(erro.message);
        }
    }

    function preencherSelect(id, valores, selecionado) {
        const select = $(id);
        valores.forEach((valor) => select.add(new Option(valor, valor, false, valor === selecionado)));
    }

    function atualizarRotuloStatus() {
        estado.status = [...$('opcoesStatus').querySelectorAll('input:checked')].map((campo) => campo.value);
        $('botaoStatus').textContent = estado.status.length === 0 ? 'Todos' : `${estado.status.length} selecionado(s)`;
    }

    function destacarAtalho() {
        const fim = $('dataFinal').value;
        const dias = diasEntre($('dataInicial').value, fim);
        document.querySelectorAll('#atalhosPeriodo button').forEach((botao) => {
            botao.classList.toggle('active', fim === hojeIso() && Number(botao.dataset.dias) === dias);
        });
    }

    function selecionarBotao(grupoId, botaoAtivo) {
        document.querySelectorAll(`#${grupoId} button`).forEach((botao) => botao.classList.toggle('active', botao === botaoAtivo));
    }

    function configurarAtualizacaoAutomatica() {
        clearInterval(estado.temporizador);
        const segundos = Number($('intervaloAtualizacao').value);
        try {
            localStorage.setItem('dashboard.intervalo', String(segundos));
        } catch (e) {
        }
        if (segundos > 0) {
            estado.temporizador = setInterval(() => {
                if (!document.hidden) {
                    atualizar();
                }
            }, segundos * 1000);
        }
    }

    function registrarEventos() {
        $('formFiltros').addEventListener('submit', (evento) => {
            evento.preventDefault();
            estado.pagina = 0;
            atualizar();
        });
        $('botaoLimpar').addEventListener('click', () => {
            $('dataFinal').value = hojeIso();
            $('dataInicial').value = deslocarIso(hojeIso(), -(PERIODO_PADRAO - 1));
            $('tipoProduto').value = '';
            $('responsavel').value = '';
            $('opcoesStatus').querySelectorAll('input').forEach((campo) => { campo.checked = false; });
            atualizarRotuloStatus();
            estado.pagina = 0;
            atualizar();
        });
        $('atalhosPeriodo').addEventListener('click', (evento) => {
            const botao = evento.target.closest('button');
            if (!botao) {
                return;
            }
            $('dataFinal').value = hojeIso();
            $('dataInicial').value = deslocarIso(hojeIso(), -(Number(botao.dataset.dias) - 1));
            estado.pagina = 0;
            atualizar();
        });
        $('opcoesStatus').addEventListener('change', atualizarRotuloStatus);
        $('agrupamentos').addEventListener('click', (evento) => {
            const botao = evento.target.closest('button');
            if (!botao) {
                return;
            }
            estado.agrupamento = botao.dataset.agrupamento;
            selecionarBotao('agrupamentos', botao);
            atualizar();
        });
        $('ordenacoesRanking').addEventListener('click', (evento) => {
            const botao = evento.target.closest('button');
            if (!botao) {
                return;
            }
            estado.ordenacao = botao.dataset.ordenacao;
            selecionarBotao('ordenacoesRanking', botao);
            atualizar();
        });
        $('paginaAnterior').addEventListener('click', () => {
            estado.pagina = Math.max(0, estado.pagina - 1);
            atualizarPedidos();
        });
        $('proximaPagina').addEventListener('click', () => {
            estado.pagina += 1;
            atualizarPedidos();
        });
        $('botaoAtualizar').addEventListener('click', atualizar);
        $('botaoTentarNovamente').addEventListener('click', atualizar);
        $('intervaloAtualizacao').addEventListener('change', configurarAtualizacaoAutomatica);
        document.addEventListener('visibilitychange', () => {
            const segundos = Number($('intervaloAtualizacao').value);
            if (!document.hidden && segundos > 0 && Date.now() - estado.ultimaCarga > segundos * 1000) {
                atualizar();
            }
        });
    }

    async function iniciar() {
        const selecionados = lerUrl();
        try {
            const intervalo = localStorage.getItem('dashboard.intervalo');
            if (intervalo !== null) {
                $('intervaloAtualizacao').value = intervalo;
            }
        } catch (e) {
        }
        const botaoAgrupamento = document.querySelector(`#agrupamentos [data-agrupamento="${estado.agrupamento}"]`);
        if (botaoAgrupamento) {
            selecionarBotao('agrupamentos', botaoAgrupamento);
        } else {
            estado.agrupamento = 'DIA';
        }
        registrarEventos();
        await carregarOpcoes(selecionados);
        configurarAtualizacaoAutomatica();
        atualizar();
    }

    iniciar();
})();
