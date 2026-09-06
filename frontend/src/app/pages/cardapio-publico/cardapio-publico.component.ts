import { Component, OnInit, computed, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute } from '@angular/router';
import { environment } from '../../../environments/environment';
import { CatalogoItemPublico, CatalogoPublico, LojaConfig } from '../../core/models';
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

type Painel = 'fechado' | 'carrinho' | 'checkout' | 'sucesso';

@Component({
  selector: 'app-cardapio-publico',
  standalone: true,
  imports: [FormsModule, DecimalPipe, MatIconModule],
  templateUrl: './cardapio-publico.component.html',
  styleUrl: './cardapio-publico.component.scss',
})
export class CardapioPublicoComponent implements OnInit {
  readonly loading = signal(true);
  readonly erro = signal<string | null>(null);
  readonly data = signal<CatalogoPublico | null>(null);
  readonly categoriaAtiva = signal<string | null>(null);
  readonly menuAberto = signal(false);
  readonly busca = signal('');
  readonly carrinho = signal<CartLine[]>([]);
  readonly painel = signal<Painel>('fechado');
  readonly enviando = signal(false);
  readonly buscandoCep = signal(false);
  readonly checkoutErro = signal<string | null>(null);
  readonly pedidoOk = signal<PedidoPublicoOk | null>(null);

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
  formaPagamento: 'PIX' | 'CARTAO' = 'PIX';

  readonly lojaInfo = computed(() => this.data()?.loja ?? null);
  readonly categorias = computed(() => this.data()?.categorias ?? []);
  readonly pagamentoOnline = computed(() => !!this.lojaInfo()?.pagamentoOnline);

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

  readonly taxa = computed(() => Number(this.lojaInfo()?.taxaEntrega ?? 0));
  readonly total = computed(() => this.subtotal() + this.taxa());

  readonly faltaMinimo = computed(() => {
    const min = Number(this.lojaInfo()?.pedidoMinimo ?? 0);
    const falta = min - this.subtotal();
    return falta > 0 ? falta : 0;
  });

  constructor(
    private readonly http: HttpClient,
    private readonly route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    this.carregar();
    this.route.queryParamMap.subscribe((params) => {
      if (params.get('pago') === '1') {
        const id = params.get('pedido');
        this.pedidoOk.set({
          id: id ? Number(id) : 0,
          total: 0,
          taxaEntrega: 0,
          mensagem: id
            ? `Pagamento do pedido #${id} recebido! Em breve saímos para entrega.`
            : 'Pagamento recebido! Em breve saímos para entrega.',
          formaPagamento: 'PIX',
        });
        this.carrinho.set([]);
        this.painel.set('sucesso');
      }
    });
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
    this.menuAberto.set(false);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  abrirMenu(): void {
    this.menuAberto.set(true);
  }

  fecharMenu(): void {
    this.menuAberto.set(false);
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
    }
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
      },
      error: () => {
        this.buscandoCep.set(false);
        this.checkoutErro.set('Não foi possível consultar o CEP. Digite o endereço.');
      },
    });
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
      this.checkoutErro.set('A loja está fechada no momento.');
      this.painel.set('carrinho');
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
    this.checkoutErro.set(null);
    this.painel.set('checkout');
  }

  fecharPainel(): void {
    if (this.painel() === 'sucesso') {
      this.pedidoOk.set(null);
      this.carrinho.set([]);
    }
    this.painel.set('fechado');
    this.checkoutErro.set(null);
  }

  enviarPedido(): void {
    if (this.enviando()) return;
    const nome = this.clienteNome.trim();
    const telefone = this.telefone.trim();
    const endereco = this.montarEndereco();
    if (!nome) {
      this.checkoutErro.set('Informe seu nome.');
      return;
    }
    if (telefone.replace(/\D/g, '').length < 10) {
      this.checkoutErro.set('Informe um telefone com DDD.');
      return;
    }
    if (this.cep.replace(/\D/g, '').length !== 8) {
      this.checkoutErro.set('Informe um CEP válido.');
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
    if (this.carrinho().length === 0) {
      this.checkoutErro.set('Carrinho vazio.');
      return;
    }

    this.enviando.set(true);
    this.checkoutErro.set(null);
    const body = {
      clienteNome: nome,
      telefone,
      enderecoEntrega: endereco,
      formaPagamento: this.formaPagamento,
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
          this.pedidoOk.set(res);
          this.painel.set('sucesso');
          this.limparCheckout();
        },
        error: (err: HttpErrorResponse) => {
          this.enviando.set(false);
          const msg =
            err.error?.erro ||
            (err.status === 409
              ? 'Não foi possível registrar o pedido agora.'
              : 'Falha ao enviar. Tente de novo.');
          this.checkoutErro.set(msg);
        },
      });
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
    this.formaPagamento = 'PIX';
  }
}
