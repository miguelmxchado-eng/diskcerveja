---
name: emporio-pdv-simples
description: >-
  Especialista em PDV/ERP do Empório Machado (diskcerveja): avalia e melhora o ponto de
  venda e cadastros mantendo a operação simples para usuários com pouca experiência.
  Use quando o usuário pedir melhoria do PDV, usabilidade no caixa, cadastro de cliente,
  fluxo de venda balcão/delivery, ou revisão de telas operacionais do Empório Machado.
---

# Empório Machado — PDV simples (operadores)

## Contexto do produto

- App: **Disk Cerveja / Empório Machado** — Spring Boot 3 + Angular 17 + PostgreSQL + Flyway
- Público: operadores de balcão e entrega **sem muita experiência em sistemas**
- Design system: preto `#090909`, creme `#F7F3EB`, dourado `#D2A410`, PT-BR, `R$ 1.842,30`
- Stack skill complementar: `erp-springboot-angular` (padrões técnicos)

## Regra de ouro

**Simplicidade > completude.** Preferir menos campos, menos cliques e textos claros.
Se um recurso exigir treinamento, redesenhar ou adiar.

## Princípios de UX (obrigatórios)

1. **Uma ação principal por tela** — no PDV: vender; botão dourado óbvio (“Confirmar venda”).
2. **Vocabulário do balcão** — “Cliente”, “Delivery”, “Troco”, não jargão de ERP.
3. **Poucos campos obrigatórios** — o resto opcional e escondido até precisar.
4. **Erros em português simples** — o que aconteceu + o que fazer agora.
5. **Touch-friendly** — alvos grandes; evitar menus densos no fluxo de venda.
6. **Não quebrar o fluxo** — cadastros auxiliares (cliente, produto) abrem sem perder o carrinho quando possível.
7. **Não adicionar** dashboards, filtros avançados ou configurações no caminho crítico do PDV.

## Avaliação do PDV (checklist)

Ao avaliar ou melhorar o PDV, responder nesta ordem:

### 1. Fluxo crítico (30s)
- [ ] Buscar/ler código → adicionar → total → confirmar funciona sem treinamento
- [ ] Balcão vs Delivery: Delivery só mostra campos extras quando ativo
- [ ] Carrinho rola até o fim; botão confirmar sempre visível
- [ ] Caixa fechado: mensagem clara + atalho para abrir caixa

### 2. Cliente
- [ ] Dá para escolher cliente existente **ou** digitar nome rápido
- [ ] Cadastro de cliente é mínimo: nome (+ telefone se delivery)
- [ ] Sem CPF/CNPJ/IE obrigatórios no PDV (podem existir no cadastro completo depois)

### 3. Clareza
- [ ] Labels curtos; placeholders de exemplo real (“Maria Silva”, “(11) 99999-9999”)
- [ ] Sem “Selecionar” que não seleciona nada de verdade
- [ ] Estados vazios com próxima ação (“Toque num produto…”)

### 4. Riscos
- [ ] Duplo clique não duplica venda
- [ ] Estoque zerado não adiciona (mensagem amigável)
- [ ] Scroll/overlay não “trava” a tela

## Cadastro de cliente — escopo mínimo

Quando implementar clientes, **começar simples**:

| Campo | Obrigatório | Onde |
|-------|-------------|------|
| Nome | Sim | Lista + PDV |
| Telefone | Recomendado | Lista + Delivery |
| Endereço | Opcional | Só Delivery / ficha |
| Observação | Opcional | Ficha |

**Não** incluir na v1: crédito, limite, múltiplos endereços, ranking, tags, CPF obrigatório.

### Integração no PDV

1. Campo “Cliente” com busca por nome/telefone (autocomplete).
2. Ação “+ Novo” abre formulário curto (modal ou painel) e devolve o cliente ao pedido.
3. Pedido grava `clienteId` quando houver; manter `clienteNome`/`telefone` para compatibilidade.
4. Sem cliente: venda balcão continua permitida (consumidor avulso).

### Menu

- Colocar em **ADMIN** ou **VENDER** como “Clientes” — um item só, ícone de pessoas.
- Não criar submenu profundo.

## Como entregar melhorias

1. Listar achados em 3–7 bullets (problema → impacto no operador).
2. Propor **no máximo 3 mudanças** por vez, priorizadas por impacto no caixa.
3. Implementar a mais simples primeiro; validar build Angular/Maven.
4. Textos e botões em **português do Brasil**.

## Anti-padrões (evitar)

- Wizard de 5 passos no PDV
- Obrigatoriedade de cliente em toda venda
- Telas com 10+ filtros no caminho da venda
- Termos: “entidade”, “SKU”, “payload”, “sincronizar” na UI do operador
- Copiar ERP corporativo complexo (SAP-like)

## Referência técnica rápida

- Frontend PDV: `frontend/src/app/pages/pdv/`
- Layout/menu: `frontend/src/app/layout/main-layout.component.*`
- Pedido API: `backend/.../PedidoController`, `PedidoRequest` (`clienteNome`, etc.)
- Design tokens: `frontend/src/styles/em-ds.scss`, `styles.scss`
