(function () {
    const fetchOriginal = window.fetch.bind(window);
    window.fetch = (url, opcoes = {}) => {
        const token = document.querySelector('meta[name="_csrf"]');
        const header = document.querySelector('meta[name="_csrf_header"]');
        const metodo = (opcoes.method || 'GET').toUpperCase();
        if (token && header && !['GET', 'HEAD', 'OPTIONS'].includes(metodo)) {
            const headers = new Headers(opcoes.headers || {});
            headers.set(header.content, token.content);
            opcoes = { ...opcoes, headers };
        }
        return fetchOriginal(url, opcoes);
    };
})();

async function lerErroApi(response) {
    try {
        const corpo = await response.json();
        return corpo.mensagem || `Erro ${response.status}`;
    } catch (e) {
        return `Erro ${response.status}`;
    }
}
