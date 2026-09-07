import {
  Component,
  ElementRef,
  HostListener,
  OnInit,
  ViewChild,
  computed,
  effect,
  signal,
} from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute } from '@angular/router';
import { environment } from '../../../environments/environment';
import { ContaClienteService } from '../../core/conta-cliente.service';
import { CatalogoItemPublico, CatalogoPublico, FretePublico, LojaConfig } from '../../core/models';
import { produtoFotoUrl } from '../../shared/produto-foto';

interface CartLine {
  key: string;
  tipo: 'PRODUTO' | 'COMBO';
  id: number;
  nome: string;
  preco: number;
  quantidade: number;
  vendaUnidade: boolean;
  imagemUrl?: string | null;
  categoria: string;
}

interface PedidoPublicoOk {
  id: number;
  total: number;
  taxaEntrega: number;
  mensagem: string;
  formaPagamento: string;
  checkoutUrl?: string | null;
  pagamentoOnline?: boolean;
}

interface ViaCepResponse {
  erro?: boolean;
  logradouro?: string;
  bairro?: string;
  localidade?: string;
  uf?: string;
}

type Painel =
  | 'fechado'
  | 'carrinho'
  | 'checkout'
  | 'confirmando'
  | 'aguardando'
  | 'sucesso'
  | 'conta-login'
  | 'conta-registrar';

interface PedidoAguardando {
  id: number;
  mensagem: string;
  total?: number;
}

@Component({
  selector: 'app-cardapio-publico',
  standalone: true,
  imports: [FormsModule, DecimalPipe, MatIconModule],
  templateUrl: './cardapio-publico.component.html',
  styleUrl: './cardapio-publico.component.scss',
})
export class CardapioPublicoComponent implements OnInit {
  @ViewChild('sheetTitle') sheetTitle?: ElementRef<HTMLHeadingElement>;

  readonly loading = signal(true);
  readonly erro = signal<string | null>(null);
  readonly data = signal<CatalogoPublico | null>(null);
  readonly categoriaAtiva = signal<string | null>(null);
  readonly busca = signal('');
  readonly carrinho = signal<CartLine[]>([]);
  readonly painel = signal<Painel>('fechado');
  readonly checkoutPasso = signal<1 | 2>(1);
  readonly enviando = signal(false);
  readonly buscandoCep = signal(false);
  readonly checkoutErro = signal<string | null>(null);
  readonly pedidoOk = signal<PedidoPublicoOk | null>(null);
  readonly pedidoAguardando = signal<PedidoAguardando | null>(null);
  readonly frete = signal<FretePublico | null>(null);
  readonly cotandoFrete = signal(false);
  readonly contaEnviando = signal(false);

  clienteNome = '';
  telefone = '';
  cep = '';
  logradouro = '';
  numero = '';
  complemento = '';
  bairro = '';
  cidade = '';
  uf = '';
  observacao = '';
  contaSenha = '';
  contaSenha2 = '';
  contaPedidoId = '';
  readonly modoEntrega = signal<'ENTREGA' | 'RETIRADA'>('ENTREGA');

  readonly lojaInfo = computed(() => this.data()?.loja ?? null);
  readonly categorias = computed(() => this.data()?.categorias ?? []);
  readonly pagamentoOnline = computed(() => !!this.lojaInfo()?.pagamentoOnline);
  readonly clienteLogado = computed(() => this.conta.logado());
  readonly clienteNomeCurto = computed(() => this.conta.nomeCurto());

  readonly itensVisiveis = computed(() => {
    const cats = this.categorias();
    const cat = this.categoriaAtiva();
    const q = this.busca().trim().toLowerCase();
    let itens: CatalogoItemPublico[] = [];
    for (const c of cats) {
      // Busca cobre o cardápio todo; sem busca, só a categoria ativa.
      if (!q && cat && c.codigo !== cat) continue;
      itens = itens.concat(c.itens);
    }
    if (q) {
      itens = itens.filter(
        (i) =>
          i.nome.toLowerCase().includes(q) ||
          (i.descricao ?? '').toLowerCase().includes(q),
      );
    }
    return itens;
  });

  readonly tituloSecao = computed(() => {
    const q = this.busca().trim();
    if (q) return `Resultados para “${q}”`;
    const cat = this.categoriaAtiva();
    if (!cat) return 'Cardápio';
    return this.categorias().find((c) => c.codigo === cat)?.nome ?? cat;
  });

  readonly qtdCarrinho = computed(() =>
    this.carrinho().reduce((acc, l) => acc + l.quantidade, 0),
  );

  readonly subtotal = computed(() =>
    this.carrinho().reduce((acc, l) => acc + l.preco * l.quantidade, 0),
  );

  /** Menor taxa do cardápio ("a partir de") — só estimativa até cotar o CEP. */
  readonly taxaMinima = computed(() => Number(this.lojaInfo()?.taxaEntrega ?? 0));
  /** Taxa confirmada pela cotação; 0 na retirada ou enquanto o CEP não cobrir. */
  readonly taxa = computed(() => {
    if (this.modoEntrega() === 'RETIRADA') return 0;
    const f = this.frete();
    if (f?.coberta) return Number(f.taxa ?? 0);
    return 0;
  });
  readonly freteConfirmado = computed(
    () => this.modoEntrega() === 'RETIRADA' || !!this.frete()?.coberta,
  );
  readonly total = computed(() => this.subtotal() + this.taxa());
  readonly freteBloqueado = computed(() => {
    if (this.modoEntrega() === 'RETIRADA') return false;
    const f = this.frete();
    return !!f && !f.coberta;
  });

  readonly faltaMinimo = computed(() => {
    const min = Number(this.lojaInfo()?.pedidoMinimo ?? 0);
    const falta = min - this.subtotal();
    return falta > 0 ? falta : 0;
  });

  constructor(
    private readonly http: HttpClient,
    private readonly route: ActivatedRoute,
    readonly conta: ContaClienteService,
  ) {
    effect(() => {
      const p = this.painel();
      if (p === 'fechado') return;
      queueMicrotask(() => this.sheetTitle?.nativeElement?.focus());
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.painel() !== 'fechado' && this.painel() !== 'confirmando') {
      this.fecharPainel();
    }
  }

  private abrirAguardando(id: number | string, mensagem: string, total?: number): void {
    this.pedidoAguardando.set({
      id: Number(id),
      mensagem,
      total,
    });
    this.carrinho.set([]);
    this.checkoutErro.set(null);
    this.painel.set('aguardando');
  }

  ngOnInit(): void {
    this.carregar();
    if (this.conta.logado()) {
      this.aplicarPerfilNaCheckout();
      this.conta.recarregarMe().subscribe({
        next: () => this.aplicarPerfilNaCheckout(),
        error: () => this.conta.logout(),
      });
    }
    this.route.queryParamMap.subscribe((params) => {
      if (params.get('pago') !== '1') return;
      const id = params.get('pedido') || params.get('order_nsu');
      const transactionNsu = params.get('transaction_nsu');
      const slug = params.get('slug');
      const captureMethod = params.get('capture_method');
      if (!id) {
        this.abrirAguardando(
          0,
          'Pagamento retornou sem número do pedido. Se já pagou, fale conosco no WhatsApp com o comprovante.',
        );
        return;
      }
      // InfinitePay anexa transaction_nsu + slug; confirmamos no servidor.
      if (transactionNsu && slug) {
        this.painel.set('confirmando');
        this.checkoutErro.set(`Pedido #${id} registrado. Confirmando pagamento…`);
        this.http
          .post<PedidoPublicoOk>(`${environment.apiUrl}/api/publico/pedidos/${id}/confirmar-pagamento`, {
            transactionNsu,
            slug,
            captureMethod,
          })
          .subscribe({
            next: (res) => {
              this.pedidoOk.set(res);
              this.carrinho.set([]);
              this.checkoutErro.set(null);
              this.pedidoAguardando.set(null);
              this.painel.set('sucesso');
            },
            // Confirmação falhou: pedido já existe — não reabrir checkout de pagar.
            error: (err: HttpErrorResponse) => {
              this.abrirAguardando(
                id,
                err.error?.erro ||
                  `Pedido #${id} recebido. Estamos confirmando o pagamento — não pague de novo.`,
              );
            },
          });
        return;
      }
      // Sem params: webhook confirma; fazemos polling do status.
      this.aguardarConfirmacaoPagamento(Number(id));
    });
  }

  private aguardarConfirmacaoPagamento(id: number): void {
    this.checkoutErro.set(`Pedido #${id} registrado. Confirmando pagamento…`);
    this.painel.set('confirmando');
    let tentativas = 0;
    const max = 20;
    const tick = () => {
      this.http
        .get<{
          id: number;
          pagamentoConfirmado: boolean;
          cancelado: boolean;
          total: number;
          taxaEntrega: number;
          formaPagamento: string;
          mensagem: string;
        }>(`${environment.apiUrl}/api/publico/pedidos/${id}/status-pagamento`)
        .subscribe({
          next: (s) => {
            if (s.cancelado) {
              this.abrirAguardando(
                id,
                'Este pedido foi cancelado. Se precisar, faça um novo pedido pelo cardápio.',
                Number(s.total),
              );
              return;
            }
            if (s.pagamentoConfirmado) {
              this.pedidoOk.set({
                id: s.id,
                total: Number(s.total),
                taxaEntrega: Number(s.taxaEntrega),
                mensagem: s.mensagem,
                formaPagamento: s.formaPagamento,
              });
              this.carrinho.set([]);
              this.checkoutErro.set(null);
              this.pedidoAguardando.set(null);
              this.painel.set('sucesso');
              return;
            }
            tentativas += 1;
            if (tentativas >= max) {
              this.abrirAguardando(
                id,
                `Pedido #${id} recebido. O pagamento ainda está sendo confirmado — não pague de novo. Em breve a entrega recebe automaticamente.`,
                Number(s.total),
              );
              return;
            }
            this.checkoutErro.set(`Pedido #${id} registrado. Confirmando pagamento…`);
            setTimeout(tick, 2000);
          },
          error: () => {
            this.abrirAguardando(
              id,
              `Pedido #${id} registrado. Se já pagou, aguarde — a confirmação chega em breve. Não pague de novo.`,
            );
          },
        });
    };
    tick();
  }

  carregar(): void {
    this.loading.set(true);
    this.erro.set(null);
    this.http.get<CatalogoPublico>(`${environment.apiUrl}/api/publico/catalogo`).subscribe({
      next: (d) => {
        this.data.set(d);
        const primeira = d.categorias?.[0]?.codigo ?? null;
        if (!this.categoriaAtiva() && primeira) {
          this.categoriaAtiva.set(primeira);
        }
        this.loading.set(false);
      },
      error: () => {
        this.erro.set('Não foi possível carregar o cardápio. Tente de novo.');
        this.loading.set(false);
      },
    });
  }

  selecionarCategoria(codigo: string | null): void {
    if (!codigo) return;
    this.categoriaAtiva.set(codigo);
    this.busca.set('');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  fotoUrl(item: CatalogoItemPublico | CartLine): string {
    if (item.imagemUrl) return item.imagemUrl;
    return produtoFotoUrl(item.nome, item.categoria);
  }

  whatsappLink(loja: LojaConfig): string | null {
    const raw = (loja.whatsapp || '').replace(/\D/g, '');
    if (!raw) return null;
    const num = raw.startsWith('55') ? raw : `55${raw}`;
    return `https://wa.me/${num}`;
  }

  podeUnidade(item: CatalogoItemPublico): boolean {
    return item.tipo === 'PRODUTO' && item.precoUnidade != null && Number(item.precoUnidade) > 0;
  }

  onTelefoneInput(value: string): void {
    this.telefone = this.mascaraTelefone(value);
  }

  onCepInput(value: string): void {
    const d = value.replace(/\D/g, '').slice(0, 8);
    this.cep = d.length > 5 ? `${d.slice(0, 5)}-${d.slice(5)}` : d;
    if (d.length === 8) {
      this.buscarCep(d);
      this.cotarFrete(d);
    } else {
      this.frete.set(null);
    }
  }

  cotarFrete(digits?: string): void {
    const cep = (digits ?? this.cep).replace(/\D/g, '');
    const bairro = this.bairro.trim();
    if (cep.length !== 8 && !bairro) {
      this.frete.set(null);
      return;
    }
    this.cotandoFrete.set(true);
    const params: Record<string, string> = {};
    if (cep.length === 8) params['cep'] = cep;
    if (bairro) params['bairro'] = bairro;
    this.http.get<FretePublico>(`${environment.apiUrl}/api/publico/frete`, { params }).subscribe({
      next: (r) => {
        this.cotandoFrete.set(false);
        this.frete.set(r);
        if (!r.coberta) {
          this.checkoutErro.set(r.mensagem || 'CEP fora da área de entrega.');
        } else {
          this.checkoutErro.set(null);
        }
      },
      error: () => {
        this.cotandoFrete.set(false);
        this.frete.set(null);
        this.checkoutErro.set('Não foi possível calcular a taxa de entrega.');
      },
    });
  }

  buscarCep(digits?: string): void {
    const cep = (digits ?? this.cep).replace(/\D/g, '');
    if (cep.length !== 8) return;
    this.buscandoCep.set(true);
    this.http.get<ViaCepResponse>(`https://viacep.com.br/ws/${cep}/json/`).subscribe({
      next: (r) => {
        this.buscandoCep.set(false);
        if (r.erro) {
          this.checkoutErro.set('CEP não encontrado. Confira e digite o endereço.');
          return;
        }
        this.logradouro = r.logradouro ?? '';
        this.bairro = r.bairro ?? '';
        this.cidade = r.localidade ?? '';
        this.uf = r.uf ?? '';
        this.checkoutErro.set(null);
        this.cotarFrete(cep);
      },
      error: () => {
        this.buscandoCep.set(false);
        this.checkoutErro.set('Não foi possível consultar o CEP. Digite o endereço.');
      },
    });
  }

  onBairroChange(value: string): void {
    this.bairro = value;
    this.cotarFrete();
  }

  private mascaraTelefone(value: string): string {
    const d = value.replace(/\D/g, '').slice(0, 11);
    if (d.length === 0) return '';
    if (d.length <= 2) return `(${d}`;
    if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
    if (d.length <= 10) {
      return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
    }
    return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
  }

  private montarEndereco(): string {
    const parts = [
      this.logradouro.trim(),
      this.numero.trim() ? `nº ${this.numero.trim()}` : '',
      this.complemento.trim(),
      this.bairro.trim(),
      [this.cidade.trim(), this.uf.trim()].filter(Boolean).join('/'),
      this.cep.trim() ? `CEP ${this.cep.trim()}` : '',
    ].filter(Boolean);
    return parts.join(', ');
  }

  adicionar(item: CatalogoItemPublico, vendaUnidade = false): void {
    if (!item.disponivel) return;
    if (!this.lojaInfo()?.aberta) {
      return;
    }
    const key = `${item.tipo}-${item.id}${vendaUnidade ? '-u' : ''}`;
    const preco = vendaUnidade ? Number(item.precoUnidade) : Number(item.preco);
    const nome = vendaUnidade ? `${item.nome} (unidade)` : item.nome;
    const atual = [...this.carrinho()];
    const idx = atual.findIndex((l) => l.key === key);
    if (idx >= 0) {
      atual[idx] = { ...atual[idx], quantidade: atual[idx].quantidade + 1 };
    } else {
      atual.push({
        key,
        tipo: item.tipo,
        id: item.id,
        nome,
        preco,
        quantidade: 1,
        vendaUnidade,
        imagemUrl: item.imagemUrl,
        categoria: item.categoria,
      });
    }
    this.carrinho.set(atual);
  }

  alterarQtd(key: string, delta: number): void {
    const atual = this.carrinho()
      .map((l) => (l.key === key ? { ...l, quantidade: l.quantidade + delta } : l))
      .filter((l) => l.quantidade > 0);
    this.carrinho.set(atual);
    if (atual.length === 0 && this.painel() !== 'sucesso') {
      this.painel.set('fechado');
    }
  }

  abrirCarrinho(): void {
    if (this.qtdCarrinho() === 0) return;
    this.checkoutErro.set(null);
    this.painel.set('carrinho');
  }

  irCheckout(): void {
    if (this.faltaMinimo() > 0) {
      this.checkoutErro.set(`Falta R$ ${this.faltaMinimo().toFixed(2)} para o pedido mínimo.`);
      return;
    }
    if (!this.lojaInfo()?.aberta) {
      this.checkoutErro.set('A loja está fechada no momento.');
      return;
    }
    // Renova/invalida sessão vencida antes de preencher.
    this.conta.token();
    if (this.conta.logado()) {
      this.aplicarPerfilNaCheckout();
    }
    this.checkoutErro.set(null);
    this.checkoutPasso.set(1);
    this.painel.set('checkout');
  }

  irCheckoutEndereco(): void {
    const nome = this.clienteNome.trim();
    const telefone = this.telefone.trim();
    if (!nome) {
      this.checkoutErro.set('Informe seu nome.');
      return;
    }
    if (telefone.replace(/\D/g, '').length < 10) {
      this.checkoutErro.set('Informe um telefone com DDD.');
      return;
    }
    this.checkoutErro.set(null);
    this.checkoutPasso.set(2);
  }

  voltarCheckoutDados(): void {
    this.checkoutErro.set(null);
    this.checkoutPasso.set(1);
  }

  fecharPainel(): void {
    if (this.painel() === 'confirmando') {
      return;
    }
    if (this.painel() === 'sucesso' || this.painel() === 'aguardando') {
      this.pedidoOk.set(null);
      this.pedidoAguardando.set(null);
      this.carrinho.set([]);
    }
    this.painel.set('fechado');
    this.checkoutPasso.set(1);
    this.checkoutErro.set(null);
  }

  enviarPedido(): void {
    if (this.enviando()) return;
    const nome = this.clienteNome.trim();
    const telefone = this.telefone.trim();
    const retirada = this.modoEntrega() === 'RETIRADA';
    const endereco = retirada ? 'Retirada na loja' : this.montarEndereco();
    if (!nome) {
      this.checkoutErro.set('Informe seu nome.');
      this.checkoutPasso.set(1);
      return;
    }
    if (telefone.replace(/\D/g, '').length < 10) {
      this.checkoutErro.set('Informe um telefone com DDD.');
      this.checkoutPasso.set(1);
      return;
    }
    if (!retirada) {
      if (this.cep.replace(/\D/g, '').length !== 8) {
        this.checkoutErro.set('Informe um CEP válido.');
        return;
      }
      if (this.freteBloqueado()) {
        this.checkoutErro.set(this.frete()?.mensagem || 'CEP fora da área de entrega.');
        return;
      }
      if (!this.frete()?.coberta) {
        this.cotarFrete();
        this.checkoutErro.set('Aguarde o cálculo da taxa de entrega.');
        return;
      }
      if (!this.logradouro.trim() || !this.bairro.trim() || !this.cidade.trim()) {
        this.checkoutErro.set('Complete o endereço (rua, bairro e cidade).');
        return;
      }
      if (!this.numero.trim()) {
        this.checkoutErro.set('Informe o número do endereço.');
        return;
      }
    }
    if (!this.pagamentoOnline()) {
      this.checkoutErro.set(
        'Pagamento online indisponível. A loja precisa configurar a InfinitePay.',
      );
      return;
    }
    if (this.carrinho().length === 0) {
      this.checkoutErro.set('Carrinho vazio.');
      return;
    }

    this.enviando.set(true);
    this.checkoutErro.set(null);
    const body = {
      clienteNome: nome,
      telefone,
      tipo: this.modoEntrega(),
      enderecoEntrega: endereco,
      cep: retirada ? null : this.cep.replace(/\D/g, ''),
      bairro: retirada ? null : this.bairro.trim() || null,
      logradouro: retirada ? null : this.logradouro.trim() || null,
      numero: retirada ? null : this.numero.trim() || null,
      complemento: retirada ? null : this.complemento.trim() || null,
      cidade: retirada ? null : this.cidade.trim() || null,
      uf: retirada ? null : this.uf.trim() || null,
      observacao: this.observacao.trim() || null,
      itens: this.carrinho().map((l) => ({
        tipo: l.tipo,
        id: l.id,
        quantidade: l.quantidade,
        vendaUnidade: l.vendaUnidade || null,
      })),
    };

    this.http
      .post<PedidoPublicoOk>(`${environment.apiUrl}/api/publico/pedidos`, body)
      .subscribe({
        next: (res) => {
          this.enviando.set(false);
          if (res.checkoutUrl) {
            window.location.href = res.checkoutUrl;
            return;
          }
          this.checkoutErro.set('Não foi possível abrir o pagamento. Tente de novo.');
        },
        error: (err: HttpErrorResponse) => {
          this.enviando.set(false);
          const msg =
            err.error?.erro ||
            (err.status === 429
              ? 'Muitas tentativas. Aguarde um minuto e tente de novo.'
              : err.status === 409
                ? 'Não foi possível registrar o pedido agora.'
                : 'Falha ao enviar. Tente de novo.');
          this.checkoutErro.set(msg);
        },
      });
  }

  abrirContaLogin(): void {
    this.checkoutErro.set(null);
    this.contaSenha = '';
    if (!this.telefone.trim() && this.conta.perfil()?.telefone) {
      this.telefone = this.conta.perfil()!.telefone;
    }
    this.painel.set('conta-login');
  }

  abrirContaRegistrar(): void {
    this.checkoutErro.set(null);
    this.contaSenha = '';
    this.contaSenha2 = '';
    if (!this.contaPedidoId) {
      const ok = this.pedidoOk();
      const ag = this.pedidoAguardando();
      if (ok?.id) this.contaPedidoId = String(ok.id);
      else if (ag?.id && ag.id > 0) this.contaPedidoId = String(ag.id);
    }
    this.painel.set('conta-registrar');
  }

  sairConta(): void {
    this.conta.logout();
    this.checkoutErro.set(null);
  }

  enviarLoginConta(): void {
    if (this.contaEnviando()) return;
    const telefone = this.telefone.trim();
    if (telefone.replace(/\D/g, '').length < 10) {
      this.checkoutErro.set('Informe um WhatsApp com DDD.');
      return;
    }
    if (this.contaSenha.length < 4) {
      this.checkoutErro.set('Informe sua senha.');
      return;
    }
    this.contaEnviando.set(true);
    this.checkoutErro.set(null);
    this.conta.login(telefone, this.contaSenha).subscribe({
      next: () => {
        this.contaEnviando.set(false);
        this.contaSenha = '';
        this.aplicarPerfilNaCheckout();
        this.painel.set('fechado');
      },
      error: (err: HttpErrorResponse) => {
        this.contaEnviando.set(false);
        this.checkoutErro.set(err.error?.erro || 'Não foi possível entrar. Confira WhatsApp e senha.');
      },
    });
  }

  enviarRegistrarConta(): void {
    if (this.contaEnviando()) return;
    const nome = this.clienteNome.trim();
    const telefone = this.telefone.trim();
    if (!nome) {
      this.checkoutErro.set('Informe seu nome.');
      return;
    }
    if (telefone.replace(/\D/g, '').length < 10) {
      this.checkoutErro.set('Informe um WhatsApp com DDD.');
      return;
    }
    if (this.contaSenha.length < 4) {
      this.checkoutErro.set('A senha precisa ter pelo menos 4 caracteres.');
      return;
    }
    if (this.contaSenha !== this.contaSenha2) {
      this.checkoutErro.set('As senhas não são iguais.');
      return;
    }
    this.contaEnviando.set(true);
    this.checkoutErro.set(null);
    const pedidoDigits = this.contaPedidoId.replace(/\D/g, '');
    const pedidoIdNum = pedidoDigits ? Number(pedidoDigits) : NaN;
    this.conta
      .registrar({
        nome,
        telefone,
        senha: this.contaSenha,
        pedidoId: Number.isFinite(pedidoIdNum) && pedidoIdNum > 0 ? pedidoIdNum : null,
        cep: this.cep.replace(/\D/g, '').length === 8 ? this.cep : null,
        logradouro: this.logradouro.trim() || null,
        numero: this.numero.trim() || null,
        complemento: this.complemento.trim() || null,
        bairro: this.bairro.trim() || null,
        cidade: this.cidade.trim() || null,
        uf: this.uf.trim() || null,
      })
      .subscribe({
        next: () => {
          this.contaEnviando.set(false);
          this.contaSenha = '';
          this.contaSenha2 = '';
          this.contaPedidoId = '';
          this.aplicarPerfilNaCheckout();
          this.painel.set('fechado');
        },
        error: (err: HttpErrorResponse) => {
          this.contaEnviando.set(false);
          this.checkoutErro.set(err.error?.erro || 'Não foi possível criar a conta.');
        },
      });
  }

  private aplicarPerfilNaCheckout(): void {
    const p = this.conta.perfil();
    if (!p) return;
    this.clienteNome = p.nome || this.clienteNome;
    this.telefone = p.telefone || this.telefone;
    if (p.cep) this.cep = p.cep;
    if (p.logradouro) this.logradouro = p.logradouro;
    if (p.numero) this.numero = p.numero;
    if (p.complemento) this.complemento = p.complemento;
    if (p.bairro) this.bairro = p.bairro;
    if (p.cidade) this.cidade = p.cidade;
    if (p.uf) this.uf = p.uf;
    if (this.cep.replace(/\D/g, '').length === 8) {
      this.cotarFrete();
    }
  }

  private limparCheckout(): void {
    this.clienteNome = '';
    this.telefone = '';
    this.cep = '';
    this.logradouro = '';
    this.numero = '';
    this.complemento = '';
    this.bairro = '';
    this.cidade = '';
    this.uf = '';
    this.observacao = '';
    this.modoEntrega.set('ENTREGA');
  }
}
