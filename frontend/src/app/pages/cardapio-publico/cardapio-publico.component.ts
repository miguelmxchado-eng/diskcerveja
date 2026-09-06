import { Component, OnInit, computed, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
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
  readonly busca = signal('');
  readonly carrinho = signal<CartLine[]>([]);
  readonly painel = signal<Painel>('fechado');
  readonly enviando = signal(false);
  readonly checkoutErro = signal<string | null>(null);
  readonly pedidoOk = signal<PedidoPublicoOk | null>(null);

  clienteNome = '';
  telefone = '';
  endereco = '';
  observacao = '';
  formaPagamento: 'PIX' | 'DINHEIRO' | 'CARTAO' = 'PIX';

  readonly lojaInfo = computed(() => this.data()?.loja ?? null);
  readonly categorias = computed(() => this.data()?.categorias ?? []);

  readonly itensVisiveis = computed(() => {
    const cats = this.categorias();
    const cat = this.categoriaAtiva();
    const q = this.busca().trim().toLowerCase();
    let itens: CatalogoItemPublico[] = [];
    for (const c of cats) {
      if (cat && c.codigo !== cat) continue;
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
    const cat = this.categoriaAtiva();
    if (!cat) return 'Todos';
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

  constructor(private readonly http: HttpClient) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.loading.set(true);
    this.erro.set(null);
    this.http.get<CatalogoPublico>(`${environment.apiUrl}/api/publico/catalogo`).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: () => {
        this.erro.set('Não foi possível carregar o cardápio. Tente de novo.');
        this.loading.set(false);
      },
    });
  }

  selecionarCategoria(codigo: string | null): void {
    this.categoriaAtiva.set(codigo);
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
      this.checkoutErro.set(
        `Falta R$ ${this.faltaMinimo().toFixed(2)} para o pedido mínimo.`,
      );
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
    const endereco = this.endereco.trim();
    if (!nome) {
      this.checkoutErro.set('Informe seu nome.');
      return;
    }
    if (telefone.replace(/\D/g, '').length < 10) {
      this.checkoutErro.set('Informe um telefone com DDD.');
      return;
    }
    if (endereco.length < 8) {
      this.checkoutErro.set('Informe o endereço completo.');
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
          this.pedidoOk.set(res);
          this.painel.set('sucesso');
          this.clienteNome = '';
          this.telefone = '';
          this.endereco = '';
          this.observacao = '';
          this.formaPagamento = 'PIX';
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
}
