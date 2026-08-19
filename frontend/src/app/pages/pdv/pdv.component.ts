import { Component, ElementRef, OnDestroy, OnInit, ViewChild, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { DecimalPipe } from '@angular/common';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { environment } from '../../../environments/environment';
import { ComboResponse, PedidoResponse, Produto } from '../../core/models';
import { ComboService } from '../../core/combo.service';
import { BarcodeScanResult } from '../../shared/barcode/barcode-scan.types';
import { BarcodeScannerService } from '../../shared/barcode/barcode-scanner.service';
import { ProdutoCodigoService } from '../../shared/barcode/produto-codigo.service';
import { BarcodeFeedbackService } from '../../shared/barcode/barcode-feedback.service';
import { ScanDebounce } from '../../shared/barcode/scan-debounce';
import { ScanHistory, ScanHistoryEntry } from '../../shared/barcode/scan-history';
import { HidScannerService } from '../../shared/barcode/hid-scanner.service';
import { isPixQrPayload, normalizeScannedCode } from '../../shared/barcode/barcode-format.util';
import { Subscription } from 'rxjs';

const LAST_KEY = 'dcm_last_pedido';

type Linha = {
  produtoId?: number;
  comboId?: number;
  nome: string;
  qtd: number;
  preco: number;
  ultimoCodigo?: string;
  isCombo?: boolean;
  componentes?: string;
};
type AtalhoPdv = 'todos' | 'maisVendidos' | 'combos' | 'favoritos';

@Component({
  selector: 'app-pdv',
  standalone: true,
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSelectModule,
    MatCardModule,
    MatIconModule,
    MatSnackBarModule,
    MatProgressBarModule,
    MatChipsModule,
    DecimalPipe,
    StatusLabelPipe,
  ],
  templateUrl: './pdv.component.html',
  styleUrl: './pdv.component.scss',
})
export class PdvComponent implements OnInit, OnDestroy {
  @ViewChild('itensLista') private itensLista?: ElementRef<HTMLElement>;

  q = '';
  codigoBarras = '';
  produtos = signal<Produto[]>([]);
  combos = signal<ComboResponse[]>([]);
  carrinho = signal<Linha[]>([]);
  loadingProdutos = signal(false);
  loadingCombos = signal(false);
  atalhoAtivo = signal<AtalhoPdv>('todos');
  favoritos = signal<number[]>([]);
  ultimoScan = signal<{ code: string; nome: string; qtd: number; preco: number } | null>(null);
  historicoScans = signal<ScanHistoryEntry[]>([]);
  scanFlash = signal(false);

  clienteNome = '';
  telefone = '';
  tipo: 'ENTREGA' | 'RETIRADA' | 'BALCAO' = 'BALCAO';
  formaPagamento: 'PIX' | 'DINHEIRO' | 'CARTAO' = 'PIX';
  enderecoEntrega = '';
  taxaEntrega = 0;
  entregadorNome = '';

  private readonly scanDebounce = new ScanDebounce(1100, 500);
  private readonly scanHistory = new ScanHistory();
  private hidSub?: Subscription;
  private cameraSub?: Subscription;
  private barcodeIdle?: ReturnType<typeof setTimeout>;

  readonly indicePorCodigo = computed(() => {
    const map = new Map<string, Produto>();
    for (const p of this.produtos()) {
      const keys = [p.codigoBarras, p.codigoQr, p.codigoInterno].filter(Boolean) as string[];
      for (const k of keys) {
        map.set(k.trim(), p);
      }
    }
    return map;
  });

  readonly totalItens = computed(() =>
    this.carrinho().reduce((acc, l) => acc + l.qtd * l.preco, 0),
  );
  readonly totalQuantidade = computed(() => this.carrinho().reduce((acc, l) => acc + l.qtd, 0));
  readonly totalPedido = computed(() => {
    const taxa = this.tipo === 'ENTREGA' ? Number(this.taxaEntrega || 0) : 0;
    return this.totalItens() + taxa;
  });
  readonly produtosVisiveis = computed(() => {
    const produtos = [...this.produtos()];
    switch (this.atalhoAtivo()) {
      case 'maisVendidos':
        return produtos.sort((a, b) => b.estoqueAtual - a.estoqueAtual);
      case 'favoritos':
        return produtos.filter((p) => this.favoritos().includes(p.id));
      default:
        return produtos;
    }
  });

  readonly mostrandoCombos = computed(() => this.atalhoAtivo() === 'combos');

  readonly combosVisiveis = computed(() => {
    const termo = this.q.trim().toLowerCase();
    const list = this.combos();
    if (!termo) return list;
    return list.filter(
      (c) =>
        c.nome.toLowerCase().includes(termo) ||
        (c.codigo ?? '').toLowerCase().includes(termo) ||
        (c.codigoBarras ?? '').toLowerCase().includes(termo),
    );
  });

  ultimoPedidoId = signal<number | null>(null);
  readonly pedidoPendenteConfirmacao = signal<number | null>(null);
  readonly confirmando = signal(false);

  constructor(
    private readonly http: HttpClient,
    private readonly snack: MatSnackBar,
    readonly scanner: BarcodeScannerService,
    private readonly codigoService: ProdutoCodigoService,
    private readonly feedback: BarcodeFeedbackService,
    private readonly hid: HidScannerService,
    private readonly comboService: ComboService,
  ) {}

  ngOnInit(): void {
    this.buscarProdutos();
    this.carregarCombos();
    this.hid.startListening();
    this.hidSub = this.hid.scan$.subscribe((code) => this.processarCodigo(code, 'hid'));
    this.cameraSub = this.scanner.scanned$.subscribe((r) => this.onScanCamera(r));
  }

  carregarCombos(): void {
    this.loadingCombos.set(true);
    this.comboService.listarAtivos().subscribe({
      next: (list) => {
        this.combos.set(list);
        this.loadingCombos.set(false);
      },
      error: () => this.loadingCombos.set(false),
    });
  }

  ngOnDestroy(): void {
    this.hidSub?.unsubscribe();
    this.cameraSub?.unsubscribe();
    if (this.barcodeIdle) clearTimeout(this.barcodeIdle);
    this.hid.stopListening();
    this.scanner.close();
  }

  buscarProdutos() {
    this.loadingProdutos.set(true);
    const url = `${environment.apiUrl}/api/produtos` + (this.q ? `?q=${encodeURIComponent(this.q)}` : '');
    this.http.get<Produto[]>(url).subscribe({
      next: (p) => {
        this.produtos.set(p.filter((x) => x.ativo));
        this.loadingProdutos.set(false);
      },
      error: () => {
        this.loadingProdutos.set(false);
        this.snack.open('Erro ao carregar produtos.', 'OK', { duration: 3000 });
      },
    });
  }

  lerCodigoBarras() {
    if (this.barcodeIdle) {
      clearTimeout(this.barcodeIdle);
      this.barcodeIdle = undefined;
    }
    const codigo = normalizeScannedCode(this.codigoBarras);
    if (!codigo) return;
    this.processarCodigo(codigo, 'manual');
  }

  onCodigoBarrasChange(): void {
    if (this.barcodeIdle) clearTimeout(this.barcodeIdle);
    const codigo = normalizeScannedCode(this.codigoBarras);
    if (codigo.length < 8) return;
    this.barcodeIdle = setTimeout(() => this.lerCodigoBarras(), 200);
  }

  toggleScanner(): void {
    if (this.scanner.isOpen()) {
      this.fecharScanner();
    } else {
      this.abrirScanner();
    }
  }

  abrirScanner(): void {
    this.scanner.open({ mode: 'continuous' });
  }

  fecharScanner(): void {
    this.scanner.close();
  }

  private onScanCamera(r: BarcodeScanResult): void {
    this.processarCodigo(r.code, 'camera');
  }

  private processarCodigo(raw: string, origem: 'manual' | 'camera' | 'hid'): void {
    const codigo = normalizeScannedCode(raw);
    if (!codigo) return;

    if (isPixQrPayload(codigo)) {
      this.feedback.warn();
      this.scanHistory.push(codigo, false);
      this.historicoScans.set(this.scanHistory.list());
      return;
    }

    if (origem !== 'manual' && !this.scanDebounce.accept(codigo)) {
      return;
    }

    const local = this.indicePorCodigo().get(codigo);
    if (local) {
      this.registrarSucesso(local, codigo, origem !== 'manual');
      return;
    }

    const combo = this.combos().find(
      (c) => c.codigoBarras === codigo || c.codigoQr === codigo || c.codigo === codigo,
    );
    if (combo) {
      this.addCombo(combo, codigo);
      this.piscarScanOk();
      if (origem !== 'manual') this.feedback.success();
      this.scanHistory.push(codigo, true, combo.nome);
      this.historicoScans.set(this.scanHistory.list());
      this.codigoBarras = '';
      return;
    }

    this.codigoService.buscarPorCodigo(codigo).subscribe({
      next: (produto) => this.registrarSucesso(produto, codigo, origem !== 'manual'),
      error: () => {
        this.feedback.error();
        this.scanHistory.push(codigo, false);
        this.historicoScans.set(this.scanHistory.list());
        this.snack.open('Produto não encontrado para este código. Cadastre o código de barras no produto.', 'OK', {
          duration: 3200,
        });
      },
    });
  }

  private registrarSucesso(produto: Produto, codigo: string, bip: boolean): void {
    this.add(produto, codigo);
    this.codigoBarras = '';
    this.piscarScanOk();
    if (bip) {
      this.feedback.success();
    }
    this.scanHistory.push(codigo, true, produto.nome);
    this.historicoScans.set(this.scanHistory.list());
    const linha = this.carrinho().find((l) => l.produtoId === produto.id);
    this.ultimoScan.set({
      code: codigo,
      nome: produto.nome,
      qtd: linha?.qtd ?? 1,
      preco: Number(produto.preco),
    });
  }

  private piscarScanOk(): void {
    this.scanFlash.set(true);
    setTimeout(() => this.scanFlash.set(false), 280);
  }

  linhaKey(l: Linha): string {
    return l.comboId != null ? `c${l.comboId}` : `p${l.produtoId}`;
  }

  add(p: Produto, codigoLido?: string) {
    const atual = [...this.carrinho()];
    const idx = atual.findIndex((x) => !x.isCombo && x.produtoId === p.id);
    if (idx >= 0) {
      atual[idx] = {
        ...atual[idx],
        qtd: atual[idx].qtd + 1,
        ultimoCodigo: codigoLido ?? atual[idx].ultimoCodigo,
      };
    } else {
      atual.push({
        produtoId: p.id,
        nome: p.nome,
        qtd: 1,
        preco: Number(p.preco),
        ultimoCodigo: codigoLido,
      });
    }
    this.carrinho.set(atual);
    this.rolarListaItens();
  }

  addCombo(c: ComboResponse, codigoLido?: string) {
    if (c.estoqueDisponivel <= 0) {
      this.feedback.warn();
      this.snack.open(`Combo "${c.nome}" sem estoque suficiente nos produtos.`, 'OK', {
        duration: 3200,
      });
      return;
    }
    const componentes = c.itens.map((i) => `${i.quantidade}x ${i.produtoNome}`).join(', ');
    const atual = [...this.carrinho()];
    const idx = atual.findIndex((x) => x.isCombo && x.comboId === c.id);
    if (idx >= 0) {
      atual[idx] = {
        ...atual[idx],
        qtd: atual[idx].qtd + 1,
        ultimoCodigo: codigoLido ?? atual[idx].ultimoCodigo,
      };
    } else {
      atual.push({
        comboId: c.id,
        nome: c.nome,
        qtd: 1,
        preco: Number(c.precoVenda),
        isCombo: true,
        componentes,
        ultimoCodigo: codigoLido,
      });
    }
    this.carrinho.set(atual);
    this.rolarListaItens();
  }

  selecionarAtalho(atalho: AtalhoPdv) {
    this.atalhoAtivo.set(atalho);
  }

  toggleFavorito(produto: Produto, event: MouseEvent) {
    event.stopPropagation();
    const ids = this.favoritos();
    this.favoritos.set(ids.includes(produto.id) ? ids.filter((id) => id !== produto.id) : [...ids, produto.id]);
  }

  isFavorito(produto: Produto) {
    return this.favoritos().includes(produto.id);
  }

  produtoImagem(produto: Produto): string {
    switch (produto.categoria) {
      case 'CERVEJAS':
        return '🍺';
      case 'REFRIGERANTES':
        return '🥤';
      case 'ENERGETICOS':
        return '⚡';
      case 'PETISCOS':
        return '🍟';
      case 'COMBOS':
        return '🎁';
      case 'DESTILADOS':
        return '🥃';
      default:
        return '🛒';
    }
  }

  inc(i: number) {
    const atual = [...this.carrinho()];
    atual[i] = { ...atual[i], qtd: atual[i].qtd + 1 };
    this.carrinho.set(atual);
    this.rolarListaItens();
  }

  dec(i: number) {
    const atual = [...this.carrinho()];
    if (atual[i].qtd <= 1) {
      atual.splice(i, 1);
    } else {
      atual[i] = { ...atual[i], qtd: atual[i].qtd - 1 };
    }
    this.carrinho.set(atual);
  }

  private rolarListaItens(): void {
    queueMicrotask(() => {
      const el = this.itensLista?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }

  limpar() {
    this.carrinho.set([]);
    this.ultimoScan.set(null);
  }

  salvarPedido() {
    if (!this.carrinho().length) {
      this.snack.open('Adicione itens ao pedido.', 'OK', { duration: 2500 });
      return;
    }
    const body = {
      clienteNome: this.clienteNome || null,
      telefone: this.telefone || null,
      tipo: this.tipo,
      formaPagamento: this.formaPagamento,
      enderecoEntrega: this.tipo === 'ENTREGA' ? this.enderecoEntrega : null,
      taxaEntrega: this.tipo === 'ENTREGA' ? Number(this.taxaEntrega || 0) : 0,
      entregadorNome: this.entregadorNome || null,
      itens: this.carrinho().map((l) =>
        l.isCombo
          ? { comboId: l.comboId, quantidade: l.qtd }
          : { produtoId: l.produtoId, quantidade: l.qtd },
      ),
    };
    this.http.post<PedidoResponse>(`${environment.apiUrl}/api/pedidos`, body).subscribe({
      next: (p) => {
        this.ultimoPedidoId.set(p.id);
        this.pedidoPendenteConfirmacao.set(p.id);
        localStorage.setItem(LAST_KEY, JSON.stringify(body));
        this.snack.open(
          `Pedido #${p.id} salvo. Confirme a venda para baixar estoque.`,
          'OK',
          { duration: 4000 },
        );
        this.limpar();
      },
      error: (e) => {
        const msg = e?.error?.erro ?? 'Erro ao salvar pedido';
        this.snack.open(msg, 'OK', { duration: 4000 });
      },
    });
  }

  repetirUltimo() {
    const raw = localStorage.getItem(LAST_KEY);
    if (!raw) {
      this.snack.open('Não há último pedido salvo neste navegador.', 'OK', { duration: 2500 });
      return;
    }
    try {
      const o = JSON.parse(raw) as any;
      this.clienteNome = o.clienteNome ?? '';
      this.telefone = o.telefone ?? '';
      this.tipo = o.tipo ?? 'BALCAO';
      this.formaPagamento = o.formaPagamento ?? 'PIX';
      this.enderecoEntrega = o.enderecoEntrega ?? '';
      this.taxaEntrega = Number(o.taxaEntrega ?? 0);
      this.entregadorNome = o.entregadorNome ?? '';
      const linhas: Linha[] = [];
      for (const it of o.itens ?? []) {
        if (it.comboId != null) {
          const c = this.combos().find((x) => x.id === it.comboId);
          if (!c) continue;
          linhas.push({
            comboId: c.id,
            nome: c.nome,
            qtd: it.quantidade,
            preco: Number(c.precoVenda),
            isCombo: true,
            componentes: c.itens.map((i) => `${i.quantidade}x ${i.produtoNome}`).join(', '),
          });
          continue;
        }
        const p = this.produtos().find((x) => x.id === it.produtoId);
        const nome = p?.nome ?? `Produto ${it.produtoId}`;
        const preco = p ? Number(p.preco) : 0;
        linhas.push({ produtoId: it.produtoId, nome, qtd: it.quantidade, preco });
      }
      this.carrinho.set(linhas);
      this.snack.open('Último pedido carregado (revise preços/itens).', 'OK', { duration: 3000 });
    } catch {
      this.snack.open('Não foi possível repetir o último pedido.', 'OK', { duration: 2500 });
    }
  }

  whatsapp() {
    const digits = (this.telefone || '').replace(/\D/g, '');
    if (digits.length < 10) {
      this.snack.open('Informe um telefone válido para WhatsApp.', 'OK', { duration: 2500 });
      return;
    }
    const itens = this.carrinho()
      .map((l) => `- ${l.qtd}x ${l.nome}`)
      .join('\n');
    const msg = [
      this.ultimoPedidoId() ? `Pedido #${this.ultimoPedidoId()}` : 'Pedido',
      this.clienteNome ? `Cliente: ${this.clienteNome}` : '',
      'Itens:',
      itens || '(sem itens no carrinho)',
      `Total: R$ ${this.totalPedido().toFixed(2)}`,
      this.tipo === 'ENTREGA' && this.enderecoEntrega ? `Entrega: ${this.enderecoEntrega}` : '',
    ]
      .filter(Boolean)
      .join('\n');
    const url = `https://wa.me/55${digits}?text=${encodeURIComponent(msg)}`;
    window.open(url, '_blank');
  }

  confirmarVenda() {
    const id = this.pedidoPendenteConfirmacao();
    if (id == null) {
      this.snack.open('Salve um pedido antes de confirmar.', 'OK', { duration: 3000 });
      return;
    }
    this.confirmando.set(true);
    this.http
      .patch<PedidoResponse>(`${environment.apiUrl}/api/pedidos/${id}/status`, { status: 'ENTREGUE' })
      .subscribe({
        next: () => {
          this.confirmando.set(false);
          this.pedidoPendenteConfirmacao.set(null);
          this.snack.open(`Pedido #${id} confirmado.`, 'OK', { duration: 4000 });
        },
        error: (e) => {
          this.confirmando.set(false);
          const msg = e?.error?.erro ?? 'Não foi possível confirmar.';
          this.snack.open(msg, 'OK', { duration: 5000 });
        },
      });
  }
}
