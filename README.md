# PediuPatrão

Sistema de pedidos para pizzaria, desenvolvido em Java com Spring Boot, Spring Security, MongoDB e Thymeleaf.


## Sumário

- [Tecnologias](#tecnologias)
- [Como executar](#como-executar)
- [Testes automatizados](#testes-automatizados)
- [Perfis de acesso](#perfis-de-acesso)
- [Requisitos implementados](#requisitos-implementados)
- [Principais alterações](#principais-alterações)
- [API REST](#api-rest)
- [Estrutura do projeto](#estrutura-do-projeto)

## Tecnologias

| Tecnologia | Uso |
|---|---|
| Java 21+ | Linguagem |
| Spring Boot 3.4 | Base da aplicação |
| Spring Security | Autenticação, autorização por perfil e CSRF |
| Spring Data MongoDB | Persistência e transações |
| MongoDB Atlas | Banco de dados |
| Thymeleaf | Interface web |
| JUnit 5, Mockito e MockMvc | Testes automatizados |

## Como executar

### Pré-requisitos

- JDK 21 ou superior
- Um cluster MongoDB (o plano gratuito do MongoDB Atlas é suficiente), com o IP da máquina liberado em *Network Access*

O Maven não precisa ser instalado, pois o projeto inclui o Maven Wrapper (`mvnw`).

### 1. Configurar a conexão com o banco

Copie o arquivo `.env.example` para `.env` na raiz do projeto e informe a string de conexão:

```properties
MONGODB_URI=mongodb+srv://USUARIO:SENHA@SEU-CLUSTER.mongodb.net/?appName=SEU-CLUSTER
MONGODB_DATABASE=pediupatrao
```

O arquivo `.env` está no `.gitignore`, portanto as credenciais não são enviadas ao repositório. Também é possível definir `MONGODB_URI` e `MONGODB_DATABASE` como variáveis de ambiente.

### 2. Iniciar a aplicação

Pelo terminal, na raiz do projeto:

```bash
./mvnw spring-boot:run        # Linux / macOS
.\mvnw spring-boot:run        # Windows
```

Ou pela IDE, executando a classe `PizzariaPedidosApplication`.

A aplicação fica disponível em **http://localhost:8080**.

### 3. Acessar

Na primeira execução são criados automaticamente três usuários e um cardápio inicial de produtos:

| Usuário | Senha | Perfil |
|---|---|---|
| `admin` | `teste123` | ADMIN |
| `gerente` | `teste123` | GERENTE |
| `atendente` | `teste123` | ATENDENTE |

Recomenda-se alterar essas senhas pela tela de usuários após o primeiro acesso.

## Testes automatizados

```bash
./mvnw test
```

São 75 testes que não dependem do banco de dados (os repositórios são simulados) e cobrem:

| Classe | Escopo |
|---|---|
| `StatusPedidoTest` | Transições de status válidas e inválidas |
| `PedidoTest` | Cálculo de valores, entrada, saída e cancelamento |
| `AlteracaoCampoTest` | Comparação de campos alterados para a auditoria |
| `PedidoServiceTest` | Regras de pedidos, descontos, cancelamentos e registro na auditoria |
| `ClienteServiceTest` | Auditoria de inclusão, alteração e exclusão de clientes |
| `UsuarioServiceTest` | Administração de perfis, proteção contra autoelevação e ausência de senha na auditoria |
| `AuditoriaServiceTest` | Identificação do usuário e proteção de dados pessoais na consulta |
| `ApiSegurancaTest` | Acesso às APIs por perfil, respostas 401/403 e proteção CSRF |
| `SegurancaMetodosTest` | Autorização nos serviços com `@PreAuthorize` |

## Perfis de acesso

| Funcionalidade | ADMIN | GERENTE | ATENDENTE |
|---|:---:|:---:|:---:|
| Administrar usuários e atribuir perfis | Sim | Não | Não |
| Consultar auditoria | Sim | Sim | Não |
| Consultar pedidos | Sim | Sim | Sim |
| Registrar pedidos | Não | Sim | Sim |
| Avançar status dos pedidos | Não | Sim | Sim |
| Aplicar desconto | Não | Sim | Não |
| Cancelar pedido | Não | Sim | Não |
| Consultar clientes | Não | Sim | Sim |
| Cadastrar e atualizar clientes | Não | Não | Sim |
| Excluir clientes | Não | Sim | Não |
| Gerenciar produtos | Não | Sim | Não |

As permissões são aplicadas no backend em duas camadas: regras por rota e método HTTP no `SecurityConfig` e anotações `@PreAuthorize` nos serviços. A interface apenas oculta as opções indisponíveis para cada perfil; uma chamada direta à API sem permissão recebe `403 Forbidden`.

## Requisitos implementados

### RF01 — Controle de usuários e permissões

- Perfis `ADMIN`, `GERENTE` e `ATENDENTE` definidos no enum `Perfil` e atribuídos na autenticação pelo `UsuarioDetailsService`. Usuários sem perfil ficam desabilitados.
- Autorização por rota no `SecurityConfig` e por método nos serviços.
- APIs respondem `401` sem autenticação e `403` com mensagem em JSON quando o perfil não tem permissão. As páginas redirecionam para o login ou exibem a página de acesso negado.
- Proteção CSRF reativada para formulários e requisições `fetch`, com o token enviado por cabeçalho.
- Cadastro de usuários restrito ao ADMIN, inclusive na API, que antes era pública.
- Proteção contra autoelevação: o usuário não altera o próprio perfil nem exclui a própria conta, e o sistema mantém sempre ao menos um administrador.
- O hash da senha não é exposto na API nem nos formulários.

### RF02 — Evolução do cadastro de pedidos

- O pedido passa a ter itens (`ItemPedido`) com produto, quantidade, preço unitário e subtotal, além de subtotal, percentual e valor de desconto, valor total, observação, responsável pelo registro e data/hora de entrada.
- A requisição de criação aceita apenas cliente, itens e observação (`NovoPedidoRequest`). Preços vêm do cadastro de produtos e todos os valores são calculados no servidor; campos como total, desconto ou status enviados na requisição são ignorados.
- Validações: cliente existente, produto existente e ativo, quantidade entre 1 e 50 e ao menos um item.

### RF03 — Controle de entrada, saída e transições

Fluxo obrigatório:

```
RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA → FINALIZADO
                                  → RETIRADO          → FINALIZADO
```

- As transições permitidas são definidas no enum `StatusPedido`; qualquer outra (pular etapa, voltar, alterar pedido finalizado) é recusada.
- A entrada registra data/hora e responsável. A saída (entrega ou retirada) registra obrigatoriamente data/hora e responsável. A finalização também é registrada.
- Cada mudança de status é guardada no histórico do pedido.
- `CANCELADO` só pode ser definido pela operação de cancelamento, e um pedido cancelado não aceita nenhuma alteração posterior.

### RF04 — Cancelamentos e descontos

- Operações exclusivas do GERENTE, inclusive em relação ao ADMIN.
- Cancelamento com justificativa obrigatória (10 a 500 caracteres). Preserva itens, valores e o status anterior.
- Desconto percentual de até 20% sobre o subtotal, com o total recalculado no servidor. Aplicar novo desconto substitui o anterior, sem acumular.
- Ambas as operações só são permitidas antes da saída para entrega ou retirada e são registradas na auditoria.

### RF05 — Auditoria

- Entidade `RegistroAuditoria` persistida na coleção `auditoria` do MongoDB. Cada registro contém usuário autenticado e perfil, data e hora, tipo de operação, entidade e identificador, campos alterados com valores anteriores e novos e justificativa, quando exigida.
- Operações auditadas: inclusão de pedido, mudança de status, desconto e cancelamento; inclusão, alteração e exclusão de clientes; inclusão, alteração e exclusão de usuários.
- Em alterações são registrados somente os campos que realmente mudaram; salvar um cadastro sem mudanças não gera registro.
- Integridade: o registro é imutável, o `AuditoriaRepository` só permite inclusão e consulta e a API expõe apenas `GET`; demais métodos em `/api/auditoria` são negados. A operação e seu registro de auditoria são gravados na mesma transação.
- Consulta exclusiva para ADMIN e GERENTE, pela tela `/auditoria` (com filtros por entidade, operação, usuário, identificador e período) ou pela API.
- Proteção de dados: senhas, hashes e tokens nunca são registrados (a troca de senha aparece apenas como `[redefinida]`), e os telefones são exibidos parcialmente na consulta.

Exemplo de registro, conforme exibido na tela de auditoria:

| Usuário | Operação | Campo | Valor anterior | Novo valor | Data e hora |
|---|---|---|---|---|---|
| atendente | Alteração de cliente | logradouro | Rua das Flores | Av. Brasil | 15/10/2026 — 14:35 |

## Principais alterações

| Camada | Alterações |
|---|---|
| Configuração | `SecurityConfig` reescrito com regras por perfil, CSRF e tratamento de 401/403; `MongoTransactionConfig` para transações; `DataInitializer` cria usuários e produtos iniciais; conexão via `.env` |
| Entidades | `Perfil`, `StatusPedido`, `ItemPedido`, `HistoricoStatus`, `RegistroAuditoria`, `AlteracaoCampo` e `TipoOperacao` criadas; `Pedido` e `Usuario` remodelados |
| DTOs | `NovoPedidoRequest`, `ItemPedidoRequest`, `DescontoRequest`, `CancelamentoRequest` e `FiltroAuditoria`, com Bean Validation |
| Serviços | Regras de negócio em `PedidoService`, `ClienteService`, `UsuarioService` e `ProdutoService`; novo `AuditoriaService` |
| Controladores | Novas rotas de desconto, cancelamento e auditoria; `ApiExceptionHandler` padroniza as respostas de erro |
| Interface | Tela de pedidos com itens, status, desconto e cancelamento; tela de produtos corrigida; nova tela de auditoria; menu de acordo com o perfil |

## API REST

Todas as rotas exigem autenticação. Requisições que alteram dados exigem o token CSRF no cabeçalho `X-CSRF-TOKEN`.

| Método | Rota | Perfis |
|---|---|---|
| `GET` | `/api/pedidos` e `/api/pedidos/{id}` | ADMIN, GERENTE, ATENDENTE |
| `POST` | `/api/pedidos` | GERENTE, ATENDENTE |
| `PUT` | `/api/pedidos/{id}/status?status={STATUS}` | GERENTE, ATENDENTE |
| `POST` | `/api/pedidos/{id}/desconto` | GERENTE |
| `POST` | `/api/pedidos/{id}/cancelamento` | GERENTE |
| `GET` | `/api/clientes` | GERENTE, ATENDENTE |
| `POST`, `PUT` | `/api/clientes`, `/api/clientes/{id}` | ATENDENTE |
| `DELETE` | `/api/clientes/{id}` | GERENTE |
| `GET` | `/api/produtos` | Todos |
| `POST`, `PUT`, `DELETE` | `/api/produtos`, `/api/produtos/{id}` | GERENTE |
| `GET`, `POST`, `PUT`, `DELETE` | `/api/usuarios`, `/api/usuarios/{id}` | ADMIN |
| `GET` | `/api/auditoria` | ADMIN, GERENTE |

Exemplos de corpo:

```json
POST /api/pedidos
{ "clienteId": "...", "observacao": "sem cebola", "itens": [{ "produtoId": "...", "quantidade": 2 }] }

POST /api/pedidos/{id}/desconto
{ "percentual": 10 }

POST /api/pedidos/{id}/cancelamento
{ "justificativa": "Cliente desistiu do pedido" }
```

Filtros opcionais da auditoria: `entidade` (`PEDIDO`, `CLIENTE`, `USUARIO`), `entidadeId`, `operacao`, `usuario`, `dataInicial` e `dataFinal` (formato `AAAA-MM-DD`).

## Estrutura do projeto

```
src/main/java/com/umc/pediupatrao
├── config        configuração de segurança, MongoDB e dados iniciais
├── controller    controladores web e REST
├── dto           objetos de entrada das APIs
├── entity        entidades e enums de domínio
├── exception     exceções de negócio e tratamento de erros da API
├── repository    repositórios do Spring Data MongoDB
└── service       regras de negócio, autorização e auditoria

src/main/resources
├── templates     páginas Thymeleaf
└── static        CSS, JavaScript e imagens

src/test/java     testes automatizados
```
