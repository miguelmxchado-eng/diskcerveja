import {
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { DecimalPipe } from '@angular/common';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { environment } from '../../../environments/environment';
import { ComboResponse, ClienteDto, ConfigCaixaResponse, PedidoResponse, Produto } from '../../core/models';
import { ComboService } from '../../core/combo.service';
import { ClienteService } from '../../core/cliente.service';
import { BarcodeScanResult } from '../../shared/barcode/barcode-scan.types';
import { BarcodeScannerService } from '../../shared/barcode/barcode-scanner.service';
import { ProdutoCodigoService } from '../../shared/barcode/produto-codigo.service';
import { BarcodeFeedbackService } from '../../shared/barcode/barcode-feedback.service';
import { ScanDebounce } from '../../shared/barcode/scan-debounce';
import { ScanHistory, ScanHistoryEntry } from '../../shared/barcode/scan-history';
import { HidScannerService } from '../../shared/barcode/hid-scanner.service';
import { isPixQrPayload, normalizeScannedCode } from '../../shared/barcode/barcode-format.util';
import { codigoPrincipal } from '../../shared/barcode/produto-codigo-display';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../shared/confirm-dialog/confirm-dialog.component';
import { Subscription, timeout, finalize } from 'rxjs';

const LAST_KEY = 'dcm_last_pedido';
const FAV_KEY = 'dcm_pdv_favoritos';

/** Fotos de produto (estoque Unsplash) por família — sem emoji. */
const IMG = {
  cerveja:
    'https://images.unsplash.com/photo-1608270586620-248524c67de9?auto=format&fit=crop&w=400&q=80',
  cervejaPack:
    'https://images.unsplash.com/photo-1618885472179-5e474019f2a9?auto=format&fit=crop&w=400&q=80',
  destilado:
    'https://images.unsplash.com/photo-1569529465841-dfecdab7503b?auto=format&fit=crop&w=400&q=80',
  whisky:
    'https://images.unsplash.com/photo-1527281400683-1aae777175f8?auto=format&fit=crop&w=400&q=80',
  licor:
    'https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1a?auto=format&fit=crop&w=400&q=80',
  petisco:
    'https://images.unsplash.com/photo-1621939514649-280e2ee25f60?auto=format&fit=crop&w=400&q=80',
  refrigerante:
    'https://images.unsplash.com/photo-1622483767028-3f66f32aef97?auto=format&fit=crop&w=400&q=80',
  energetico:
    'https://images.unsplash.com/photo-1622543925227-4804d6c5da76?auto=format&fit=crop&w=400&q=80',
  combo:
    'https://images.unsplash.com/photo-1608270586620-248524c67de9?auto=format&fit=crop&w=400&q=80',
  generico:
    'https://images.unsplash.com/photo-1586995930424-bf275ccd02ad?auto=format&fit=crop&w=400&q=80',
} as const;

type Linha = {
  produtoId?: number;
  comboId?: number;
  nome: string;
  qtd: number;
  preco: number;
  vendaUnidade?: boolean;
  ultimoCodigo?: string;
  isCombo?: boolean;
  componentes?: string;
  categoria?: string;
  imagemUrl?: string;
};

type AtalhoPdv =
  | 'todos'
  | 'maisVendidos'
  | 'cervejas'
  | 'destilados'
  | 'petiscos'
  | 'combos'
  | 'favoritos';

type Ordenacao = 'maisVendidos' | 'nome' | 'precoAsc' | 'precoDesc' | 'estoque';

@Component({
  selector: 'app-pdv',
  standalone: true,
  imports: [
    FormsModule,
    MatIconModule,
    MatSnackBarModule,
    MatDialogModule,
    DecimalPipe,
    StatusLabelPipe,
  ],
  templateUrl: './pdv.component.html',
  styleUrl: './pdv.component.scss',
})
export class PdvComponent implements OnInit, OnDestroy {
  @ViewChild('itensLista') private itensLista?: ElementRef<HTMLElement>;
  @ViewChild('buscaInput') private buscaInput?: ElementRef<HTMLInputElement>;
  @ViewChild('clienteInput') private clienteInput?: ElementRef<HTMLInputElement>;

  readonly filtros: { id: AtalhoPdv; label: string }[] = [
    { id: 'todos', label: 'Todos' },
    { id: 'maisVendidos', label: 'Mais vendidos' },
    { id: 'cervejas', label: 'Cervejas' },
    { id: 'destilados', label: 'Destilados' },
    { id: 'petiscos', label: 'Petiscos' },
    { id: 'combos', label: 'Combos' },
    { id: 'favoritos', label: 'Favoritos' },
  ];

  q = signal('');
  codigoBarras = '';
  ordenacao = signal<Ordenacao>('maisVendidos');
  observacao = '';

  produtos = signal<Produto[]>([]);
  combos = signal<ComboResponse[]>([]);
  carrinho = signal<Linha[]>([]);
  loadingProdutos = signal(false);
  loadingCombos = signal(false);
  erroCatalogo = signal<string | null>(null);
  atalhoAtivo = signal<AtalhoPdv>('todos');
  /** Evita travar o browser renderizando 100+ cards de uma vez. */
  pageSize = 24;
  pagina = signal(1);
  favoritos = signal<number[]>(this.lerFavoritos());
  ultimoScan = signal<{ code: string; nome: string; qtd: number; preco: number } | null>(null);
  historicoScans = signal<ScanHistoryEntry[]>([]);
  scanFlash = signal(false);
  buscaFocada = signal(false);
  clienteAberto = signal(false);
  clienteId = signal<number | null>(null);
  clienteSugestoes = signal<ClienteDto[]>([]);
  clienteBuscando = signal(false);
  novoClienteAberto = signal(false);
  novoClienteNome = '';
  novoClienteTelefone = '';
  private clienteBuscaIdle?: ReturnType<typeof setTimeout>;
  obsAberta = signal(false);
  descontoAberto = signal(false);
  descontoModo = signal<'valor' | 'percent'>('valor');
  descontoValor = signal(0);
  descontoInput = 0;

  clienteNome = '';
  telefone = '';
  tipo = signal<'ENTREGA' | 'RETIRADA' | 'BALCAO'>('BALCAO');
  formaPagamento: 'PIX' | 'DINHEIRO' | 'CARTAO' = 'PIX';
  enderecoEntrega = '';
  taxaEntrega = signal(0);
  entregadorNome = '';

  private readonly scanDebounce = new ScanDebounce(1100, 500);
  private readonly scanHistory = new ScanHistory();
  private hidSub?: Subscription;
  private cameraSub?: Subscription;
  private barcodeIdle?: ReturnType<typeof setTimeout>;
  private buscaIdle?: ReturnType<typeof setTimeout>;

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

  readonly desconto = computed(() => this.descontoValor());

  readonly totalItens = computed(() =>
    this.carrinho().reduce((acc, l) => acc + l.qtd * l.preco, 0),
  );
  readonly totalQuantidade = computed(() => this.carrinho().reduce((acc, l) => acc + l.qtd, 0));
  readonly totalPedido = computed(() => {
    const taxa = this.tipo() === 'ENTREGA' ? Number(this.taxaEntrega() || 0) : 0;
    return Math.max(0, this.totalItens() - this.desconto() + taxa);
  });

  readonly produtosFiltrados = computed(() => {
    let list = [...this.produtos()];
    const atalho = this.atalhoAtivo();
    const termo = this.q().trim().toLowerCase();
    const ord = this.ordenacao();

    if (atalho === 'cervejas') list = list.filter((p) => p.categoria === 'CERVEJAS');
    else if (atalho === 'destilados') list = list.filter((p) => p.categoria === 'DESTILADOS');
    else if (atalho === 'petiscos') list = list.filter((p) => p.categoria === 'PETISCOS');
    else if (atalho === 'favoritos') {
      list = list.filter((p) => this.favoritos().includes(p.id));
    }

    if (termo) {
      list = list.filter(
        (p) =>
          (p.nome || '').toLowerCase().includes(termo) ||
          (p.categoria || '').toLowerCase().includes(termo) ||
          (p.codigoBarras || '').toLowerCase().includes(termo) ||
          (p.codigoInterno || '').toLowerCase().includes(termo) ||
          (p.codigoQr || '').toLowerCase().includes(termo),
      );
    }

    switch (ord) {
      case 'nome':
        list.sort((a, b) => (a.nome || '').localeCompare(b.nome || '', 'pt-BR'));
        break;
      case 'precoAsc':
        list.sort((a, b) => Number(a.preco) - Number(b.preco));
        break;
      case 'precoDesc':
        list.sort((a, b) => Number(b.preco) - Number(a.preco));
        break;
      case 'estoque':
        list.sort((a, b) => b.estoqueAtual - a.estoqueAtual);
        break;
      default:
        list.sort((a, b) => b.estoqueAtual - a.estoqueAtual);
    }
    return list;
  });

  readonly produtosVisiveis = computed(() => {
    const end = this.pagina() * this.pageSize;
    return this.produtosFiltrados().slice(0, end);
  });

  readonly temMaisProdutos = computed(
    () => this.produtosVisiveis().length < this.produtosFiltrados().length,
  );

  readonly mostrandoCombos = computed(() => this.atalhoAtivo() === 'combos');

  readonly combosVisiveis = computed(() => {
    const termo = this.q().trim().toLowerCase();
    let list = [...this.combos()];
    if (termo) {
      list = list.filter(
        (c) =>
          (c.nome || '').toLowerCase().includes(termo) ||
          (c.codigo ?? '').toLowerCase().includes(termo) ||
          (c.codigoBarras ?? '').toLowerCase().includes(termo),
      );
    }
    return list;
  });

  readonly catalogoCarregando = computed(() =>
    this.mostrandoCombos() ? this.loadingCombos() : this.loadingProdutos(),
  );

  ultimoPedidoId = signal<number | null>(null);
  readonly pedidoPendenteConfirmacao = signal<number | null>(null);
  readonly confirmando = signal(false);
  readonly salvandoPedido = signal(false);
  /** Total do pedido pendente de confirmação (após limpar o carrinho). */
  readonly totalConfirmacao = signal<number | null>(null);
  readonly caixaObrigatorio = signal(false);
  readonly caixaAberto = signal(true);
  readonly caixaVerificando = signal(false);

  readonly precisaAbrirCaixa = computed(
    () => this.caixaObrigatorio() && !this.caixaAberto() && !this.caixaVerificando(),
  );

  constructor(
    private readonly http: HttpClient,
    private readonly snack: MatSnackBar,
    private readonly dialog: MatDialog,
    private readonly router: Router,
    readonly scanner: BarcodeScannerService,
    private readonly codigoService: ProdutoCodigoService,
    private readonly feedback: BarcodeFeedbackService,
    private readonly hid: HidScannerService,
    private readonly comboService: ComboService,
    private readonly clienteService: ClienteService,
  ) {}

  ngOnInit(): void {
    this.buscarProdutos();
    this.carregarCombos();
    this.verificarCaixa();
    this.hid.startListening();
    this.hidSub = this.hid.scan$.subscribe((code) => this.processarCodigo(code, 'hid'));
    this.cameraSub = this.scanner.scanned$.subscribe((r) => this.onScanCamera(r));
  }

  ngOnDestroy(): void {
    this.hidSub?.unsubscribe();
    this.cameraSub?.unsubscribe();
    if (this.barcodeIdle) clearTimeout(this.barcodeIdle);
    if (this.buscaIdle) clearTimeout(this.buscaIdle);
    if (this.clienteBuscaIdle) clearTimeout(this.clienteBuscaIdle);
    this.hid.stopListening();
    this.scanner.close();
  }

  @HostListener('window:keydown', ['$event'])
  onGlobalKey(ev: KeyboardEvent): void {
    if (ev.key === 'F2') {
      ev.preventDefault();
      this.buscaInput?.nativeElement.focus();
      this.buscaInput?.nativeElement.select();
      return;
    }
    if (ev.key === 'F4') {
      ev.preventDefault();
      this.abrirScanner();
    }
  }

  carregarCombos(): void {
    this.loadingCombos.set(true);
    this.comboService
      .listarAtivos()
      .pipe(
        timeout(15000),
        finalize(() => this.loadingCombos.set(false)),
      )
      .subscribe({
        next: (list) => this.combos.set(Array.isArray(list) ? list : []),
        error: () => this.combos.set([]),
      });
  }

  buscarProdutos() {
    this.loadingProdutos.set(true);
    this.erroCatalogo.set(null);
    this.http
      .get<Produto[]>(`${environment.apiUrl}/api/produtos`)
      .pipe(
        timeout(15000),
        finalize(() => this.loadingProdutos.set(false)),
      )
      .subscribe({
        next: (p) => {
          const list = Array.isArray(p) ? p : [];
          this.produtos.set(list.filter((x) => x?.ativo));
          this.pagina.set(1);
        },
        error: () => {
          this.produtos.set([]);
          this.erroCatalogo.set('Não foi possível carregar o catálogo. Verifique a conexão e tente de novo.');
          this.snack.open('Erro ao carregar produtos.', 'OK', { duration: 3000 });
        },
      });
  }

  carregarMaisProdutos(): void {
    this.pagina.update((n) => n + 1);
  }

  onBuscaChange(): void {
    this.pagina.set(1);
    if (this.buscaIdle) clearTimeout(this.buscaIdle);
    const raw = this.q().trim();
    if (/^\d{8,}$/.test(raw)) {
      this.buscaIdle = setTimeout(() => {
        this.codigoBarras = raw;
        this.processarCodigo(raw, 'manual');
        this.q.set('');
      }, 220);
    }
  }

  onOrdenacaoChange(): void {
    /* signal atualizado via ngModelChange no template */
  }

  selecionarAtalho(atalho: AtalhoPdv) {
    this.atalhoAtivo.set(atalho);
    this.pagina.set(1);
  }

  setTipo(t: 'BALCAO' | 'ENTREGA') {
    this.tipo.set(t);
    if (t === 'ENTREGA') {
      this.clienteAberto.set(true);
    }
  }

  taxaEntregaExibida(): number {
    return this.tipo() === 'ENTREGA' ? Number(this.taxaEntrega() || 0) : 0;
  }

  focarCliente(): void {
    this.clienteAberto.set(true);
    setTimeout(() => this.clienteInput?.nativeElement.focus(), 50);
  }

  onClienteNomeChange(): void {
    this.clienteId.set(null);
    if (this.clienteBuscaIdle) clearTimeout(this.clienteBuscaIdle);
    const q = this.clienteNome.trim();
    if (q.length < 2) {
      this.clienteSugestoes.set([]);
      return;
    }
    this.clienteBuscaIdle = setTimeout(() => {
      this.clienteBuscando.set(true);
      this.clienteService.listar(q).subscribe({
        next: (list) => {
          this.clienteSugestoes.set(list.slice(0, 8));
          this.clienteBuscando.set(false);
        },
        error: () => {
          this.clienteSugestoes.set([]);
          this.clienteBuscando.set(false);
        },
      });
    }, 280);
  }

  selecionarCliente(c: ClienteDto): void {
    this.clienteId.set(c.id ?? null);
    this.clienteNome = c.nome;
    this.telefone = c.telefone ?? '';
    if (c.endereco) {
      this.enderecoEntrega = c.endereco;
    }
    this.clienteSugestoes.set([]);
    this.clienteAberto.set(true);
  }

  limparCliente(): void {
    this.clienteId.set(null);
    this.clienteNome = '';
    this.telefone = '';
    this.clienteSugestoes.set([]);
  }

  abrirNovoCliente(): void {
    this.novoClienteNome = this.clienteNome.trim();
    this.novoClienteTelefone = this.telefone.trim();
    this.novoClienteAberto.set(true);
  }

  fecharNovoCliente(): void {
    this.novoClienteAberto.set(false);
  }

  salvarNovoCliente(): void {
    const nome = this.novoClienteNome.trim();
    if (!nome) {
      this.snack.open('Informe o nome do cliente.', 'OK', { duration: 2500 });
      return;
    }
    this.clienteService
      .criar({
        nome,
        telefone: this.novoClienteTelefone.trim() || null,
        endereco: this.enderecoEntrega.trim() || null,
        ativo: true,
      })
      .subscribe({
        next: (c) => {
          this.selecionarCliente(c);
          this.novoClienteAberto.set(false);
          this.snack.open('Cliente cadastrado.', 'OK', { duration: 2000 });
        },
        error: (e) => {
          this.snack.open(e?.error?.detail ?? e?.error?.erro ?? 'Não foi possível cadastrar.', 'OK', {
            duration: 3500,
          });
        },
      });
  }

  formaPagamentoLabel(): string {
    if (this.formaPagamento === 'DINHEIRO') return 'Dinheiro';
    if (this.formaPagamento === 'CARTAO') return 'Cartão';
    return 'PIX';
  }

  irParaProdutos(): void {
    void this.router.navigateByUrl('/produtos');
  }

  irParaCaixa(): void {
    void this.router.navigateByUrl('/caixa');
  }

  verificarCaixa(): void {
    this.caixaVerificando.set(true);
    this.http.get<ConfigCaixaResponse>(`${environment.apiUrl}/api/config/caixa`).subscribe({
      next: (cfg) => {
        this.caixaObrigatorio.set(!!cfg.caixaObrigatorio);
        if (!cfg.caixaObrigatorio) {
          this.caixaAberto.set(true);
          this.caixaVerificando.set(false);
          return;
        }
        this.http.get(`${environment.apiUrl}/api/caixa/sessao`, { observe: 'response' }).subscribe({
          next: (r) => {
            this.caixaAberto.set(r.status !== 204 && r.body != null);
            this.caixaVerificando.set(false);
          },
          error: () => {
            this.caixaAberto.set(false);
            this.caixaVerificando.set(false);
          },
        });
      },
      error: () => {
        /* Se não der para ler a config, não bloqueia a tela. */
        this.caixaVerificando.set(false);
      },
    });
  }

  codigoExibicao(p: Produto): string | null {
    return codigoPrincipal(p);
  }

  produtoImagemUrl(p: Produto): string {
    const n = (p.nome || '').toLowerCase();
    if (n.includes('whisky') || n.includes('whiskey') || n.includes('51')) return IMG.whisky;
    if (n.includes('amarula') || n.includes('licor')) return IMG.licor;
    if (n.includes('amstel') || n.includes('heineken') || n.includes('skol') || n.includes('brahma')) {
      return IMG.cervejaPack;
    }
    if (n.includes('bacon') || n.includes('trident') || n.includes('salg')) return IMG.petisco;
    switch (p.categoria) {
      case 'CERVEJAS':
        return IMG.cerveja;
      case 'DESTILADOS':
        return IMG.destilado;
      case 'PETISCOS':
        return IMG.petisco;
      case 'REFRIGERANTES':
        return IMG.refrigerante;
      case 'ENERGETICOS':
        return IMG.energetico;
      case 'COMBOS':
        return IMG.combo;
      default:
        return IMG.generico;
    }
  }

  comboImagemUrl(c: ComboResponse): string {
    return c.imagem?.trim() || IMG.combo;
  }

  linhaImagemUrl(l: Linha): string {
    if (l.imagemUrl) return l.imagemUrl;
    if (l.isCombo) return IMG.combo;
    const p = this.produtos().find((x) => x.id === l.produtoId);
    return p ? this.produtoImagemUrl(p) : IMG.generico;
  }

  toggleScanner(): void {
    if (this.scanner.isOpen()) this.fecharScanner();
    else this.abrirScanner();
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
    const finalizar = (vendaUnidade: boolean): void => {
      this.add(produto, codigo, vendaUnidade);
      this.codigoBarras = '';
      this.piscarScanOk();
      if (bip) this.feedback.success();
      this.scanHistory.push(codigo, true, produto.nome);
      this.historicoScans.set(this.scanHistory.list());
      const linha = this.carrinho().find(
        (l) => l.produtoId === produto.id && !!l.vendaUnidade === vendaUnidade,
      );
      this.ultimoScan.set({
        code: codigo,
        nome: vendaUnidade ? `${produto.nome} (unidade)` : produto.nome,
        qtd: linha?.qtd ?? 1,
        preco: vendaUnidade ? Number(produto.precoUnidade) : Number(produto.preco),
      });
    };

    if (this.podeVenderUnidade(produto)) {
      this.dialog
        .open(EscolherVendaDialogComponent, {
          data: produto,
          width: '360px',
        })
        .afterClosed()
        .subscribe((modo: 'pacote' | 'unidade' | undefined) => {
          if (modo === 'pacote') finalizar(false);
          if (modo === 'unidade') finalizar(true);
        });
      return;
    }
    finalizar(false);
  }

  private piscarScanOk(): void {
    this.scanFlash.set(true);
    setTimeout(() => this.scanFlash.set(false), 280);
  }

  linhaKey(l: Linha): string {
    if (l.comboId != null) return `c${l.comboId}`;
    return `p${l.produtoId}${l.vendaUnidade ? '-u' : ''}`;
  }

  podeVenderUnidade(p: Produto): boolean {
    return (
      p.precoUnidade != null
      && Number(p.precoUnidade) > 0
      && p.unidadesPorEmbalagem != null
      && Number(p.unidadesPorEmbalagem) > 1
    );
  }

  /** Toque no card: se tem preço de unidade, pergunta pacote ou unidade. */
  escolherEAdicionar(p: Produto): void {
    if (p.estoqueAtual <= 0) {
      return;
    }
    if (!this.podeVenderUnidade(p)) {
      this.add(p);
      return;
    }
    this.dialog
      .open(EscolherVendaDialogComponent, {
        data: p,
        width: '360px',
      })
      .afterClosed()
      .subscribe((modo: 'pacote' | 'unidade' | undefined) => {
        if (modo === 'pacote') this.add(p, undefined, false);
        if (modo === 'unidade') this.add(p, undefined, true);
      });
  }

  /**
   * Estoque do produto é sempre em unidades (garrafa/lata).
   * Venda de caixa reserva N unidades; venda avulsa reserva 1.
   */
  private unidadesReservadas(produtoId: number): number {
    return this.carrinho()
      .filter((l) => !l.isCombo && l.produtoId === produtoId)
      .reduce((soma, l) => soma + this.unidadesDaLinha(l), 0);
  }

  private unidadesDaLinha(l: Linha): number {
    if (l.vendaUnidade === true) {
      return l.qtd;
    }
    const p = l.produtoId != null ? this.produtos().find((x) => x.id === l.produtoId) : undefined;
    const upe =
      p?.unidadesPorEmbalagem != null && Number(p.unidadesPorEmbalagem) > 1
        ? Number(p.unidadesPorEmbalagem)
        : 1;
    return l.qtd * upe;
  }

  private unidadesPorVenda(p: Produto, vendaUnidade: boolean): number {
    if (vendaUnidade) return 1;
    if (p.unidadesPorEmbalagem != null && Number(p.unidadesPorEmbalagem) > 1) {
      return Number(p.unidadesPorEmbalagem);
    }
    return 1;
  }

  private cabeMaisUma(p: Produto, vendaUnidade: boolean): boolean {
    const reservado = this.unidadesReservadas(p.id);
    const preciso = this.unidadesPorVenda(p, vendaUnidade);
    return Number(p.estoqueAtual) >= reservado + preciso;
  }

  /** Texto de estoque no card: sempre em unidades; se tem caixa, mostra também. */
  estoqueLabel(p: Produto): string {
    const est = Number(p.estoqueAtual) || 0;
    if (!this.podeVenderUnidade(p)) {
      return `Estoque: ${est}`;
    }
    const upe = Number(p.unidadesPorEmbalagem);
    const caixas = Math.floor(est / upe);
    const resto = est % upe;
    if (caixas <= 0) {
      return `Estoque: ${est} un.`;
    }
    if (resto === 0) {
      return `Estoque: ${est} un. (${caixas} cx)`;
    }
    return `Estoque: ${est} un. (${caixas} cx + ${resto})`;
  }

  add(p: Produto, codigoLido?: string, vendaUnidade = false) {
    if (p.estoqueAtual <= 0) {
      this.feedback.warn();
      this.snack.open(`"${p.nome}" sem estoque.`, 'OK', { duration: 2500 });
      return;
    }
    if (vendaUnidade && !this.podeVenderUnidade(p)) {
      this.snack.open('Este produto não tem preço de unidade.', 'OK', { duration: 2500 });
      return;
    }
    if (!this.cabeMaisUma(p, vendaUnidade)) {
      this.feedback.warn();
      this.snack.open(`Estoque insuficiente para "${p.nome}".`, 'OK', { duration: 2500 });
      return;
    }
    const atual = [...this.carrinho()];
    const idx = atual.findIndex(
      (x) => !x.isCombo && x.produtoId === p.id && !!x.vendaUnidade === vendaUnidade,
    );
    if (idx >= 0) {
      atual[idx] = {
        ...atual[idx],
        qtd: atual[idx].qtd + 1,
        ultimoCodigo: codigoLido ?? atual[idx].ultimoCodigo,
      };
    } else {
      atual.push({
        produtoId: p.id,
        nome: vendaUnidade ? `${p.nome} (unidade)` : p.nome,
        qtd: 1,
        preco: vendaUnidade ? Number(p.precoUnidade) : Number(p.preco),
        vendaUnidade,
        ultimoCodigo: codigoLido,
        categoria: p.categoria,
        imagemUrl: this.produtoImagemUrl(p),
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
        imagemUrl: this.comboImagemUrl(c),
      });
    }
    this.carrinho.set(atual);
    this.rolarListaItens();
  }

  toggleFavorito(produto: Produto, event: MouseEvent) {
    event.stopPropagation();
    const ids = this.favoritos();
    const next = ids.includes(produto.id)
      ? ids.filter((id) => id !== produto.id)
      : [...ids, produto.id];
    this.favoritos.set(next);
    localStorage.setItem(FAV_KEY, JSON.stringify(next));
  }

  isFavorito(produto: Produto) {
    return this.favoritos().includes(produto.id);
  }

  private lerFavoritos(): number[] {
    try {
      const raw = localStorage.getItem(FAV_KEY);
      if (!raw) return [];
      const parsed = JSON.parse(raw) as number[];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  inc(i: number) {
    const atual = [...this.carrinho()];
    const linha = atual[i];
    if (linha?.produtoId != null && !linha.isCombo) {
      const p = this.produtos().find((x) => x.id === linha.produtoId);
      const vendaUnidade = linha.vendaUnidade === true;
      if (p && !this.cabeMaisUma(p, vendaUnidade)) {
        const upe = this.unidadesPorVenda(p, false);
        const msg = vendaUnidade
          ? `Estoque insuficiente para "${p.nome}". Disponível: ${p.estoqueAtual} unidade(s).`
          : `Estoque insuficiente para "${p.nome}". Cada caixa usa ${upe} un. (há ${p.estoqueAtual}).`;
        this.snack.open(msg, 'OK', { duration: 3500 });
        return;
      }
    }
    atual[i] = { ...atual[i], qtd: atual[i].qtd + 1, vendaUnidade: linha.vendaUnidade === true };
    this.carrinho.set(atual);
    this.rolarListaItens();
  }

  dec(i: number) {
    const atual = [...this.carrinho()];
    if (atual[i].qtd <= 1) {
      this.confirmarRemocao(i);
      return;
    }
    atual[i] = { ...atual[i], qtd: atual[i].qtd - 1 };
    this.carrinho.set(atual);
  }

  removerLinha(i: number): void {
    this.confirmarRemocao(i);
  }

  private confirmarRemocao(i: number): void {
    const nome = this.carrinho()[i]?.nome ?? 'item';
    this.dialog
      .open(ConfirmDialogComponent, {
        width: '360px',
        data: {
          titulo: 'Remover item',
          mensagem: `Remover "${nome}" do carrinho?`,
          confirmLabel: 'Remover',
          confirmColor: 'warn',
        } satisfies ConfirmDialogData,
      })
      .afterClosed()
      .subscribe((ok) => {
        if (!ok) return;
        const atual = [...this.carrinho()];
        atual.splice(i, 1);
        this.carrinho.set(atual);
      });
  }

  private rolarListaItens(): void {
    queueMicrotask(() => {
      const el = this.itensLista?.nativeElement;
      if (!el) return;
      /* Mantém o topo dos itens visível; não força o fim do scroll (quebrava o uso do formulário). */
      const firstLine = el.querySelector('.pdv-line:last-child') as HTMLElement | null;
      firstLine?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    });
  }

  limpar() {
    this.carrinho.set([]);
    this.ultimoScan.set(null);
    this.descontoValor.set(0);
    this.observacao = '';
    this.limparCliente();
    this.enderecoEntrega = '';
    this.entregadorNome = '';
    this.taxaEntrega.set(0);
    this.clienteAberto.set(false);
  }

  /** Total exibido no botão confirmar (carrinho ou pedido pendente). */
  totalConfirmacaoExibido(): number {
    if (this.pedidoPendenteConfirmacao() != null && this.totalConfirmacao() != null) {
      return this.totalConfirmacao()!;
    }
    return this.totalPedido();
  }

  abrirDesconto(): void {
    this.descontoInput = this.descontoModo() === 'valor' ? this.descontoValor() : 0;
    this.descontoAberto.set(true);
  }

  fecharDesconto(): void {
    this.descontoAberto.set(false);
  }

  aplicarDesconto(): void {
    const sub = this.totalItens();
    let valor = Number(this.descontoInput) || 0;
    if (this.descontoModo() === 'percent') {
      valor = (sub * Math.min(100, Math.max(0, valor))) / 100;
    }
    valor = Math.min(sub, Math.max(0, valor));
    this.descontoValor.set(Math.round(valor * 100) / 100);
    this.descontoAberto.set(false);
  }

  acaoConfirmar(): void {
    if (this.precisaAbrirCaixa()) {
      this.snack.open('Abra o caixa antes de vender.', 'Abrir caixa', { duration: 4500 }).onAction().subscribe(() => {
        this.irParaCaixa();
      });
      return;
    }
    if (this.salvandoPedido() || this.confirmando()) {
      return;
    }
    if (this.pedidoPendenteConfirmacao() != null) {
      this.confirmarVenda();
      return;
    }
    this.salvarPedido(true);
  }

  salvarPedido(confirmarDepois = false) {
    if (this.salvandoPedido() || this.confirmando()) {
      return;
    }
    if (!this.carrinho().length) {
      this.snack.open('Adicione itens ao pedido.', 'OK', { duration: 2500 });
      return;
    }
    const totalAtual = this.totalPedido();
    const descontoAtual = this.desconto();
    const body = {
      clienteId: this.clienteId(),
      clienteNome: this.clienteNome || null,
      telefone: this.tipo() === 'ENTREGA' ? this.telefone || null : this.telefone || null,
      tipo: this.tipo(),
      formaPagamento: this.formaPagamento,
      enderecoEntrega: this.tipo() === 'ENTREGA' ? this.enderecoEntrega : null,
      taxaEntrega: this.tipo() === 'ENTREGA' ? Number(this.taxaEntrega() || 0) : 0,
      desconto: descontoAtual,
      entregadorNome: this.entregadorNome || null,
      itens: this.carrinho().map((l) =>
        l.isCombo
          ? { comboId: l.comboId, quantidade: l.qtd }
          : { produtoId: l.produtoId, quantidade: l.qtd, vendaUnidade: !!l.vendaUnidade },
      ),
    };
    this.salvandoPedido.set(true);
    this.http.post<PedidoResponse>(`${environment.apiUrl}/api/pedidos`, body).subscribe({
      next: (p) => {
        this.salvandoPedido.set(false);
        this.ultimoPedidoId.set(p.id);
        this.pedidoPendenteConfirmacao.set(p.id);
        this.totalConfirmacao.set(Number(p.total ?? totalAtual));
        localStorage.setItem(LAST_KEY, JSON.stringify(body));
        this.snack.open(
          confirmarDepois
            ? `Pedido #${p.id} salvo. Confirmando venda…`
            : `Pedido #${p.id} salvo. Confirme a venda para baixar estoque.`,
          'OK',
          { duration: 4000 },
        );
        this.limpar();
        if (confirmarDepois) {
          this.confirmarVenda();
        }
      },
      error: (e) => {
        this.salvandoPedido.set(false);
        const msg = e?.error?.erro ?? e?.error?.detail ?? 'Erro ao salvar pedido';
        const sobreCaixa = /caixa/i.test(String(msg));
        if (sobreCaixa) {
          this.verificarCaixa();
        }
        this.snack.open(msg, sobreCaixa ? 'Abrir caixa' : 'OK', { duration: 4500 }).onAction().subscribe(() => {
          if (sobreCaixa) this.irParaCaixa();
        });
      },
    });
  }

  confirmarVenda() {
    const id = this.pedidoPendenteConfirmacao();
    if (id == null) {
      this.snack.open('Salve um pedido antes de confirmar.', 'OK', { duration: 3000 });
      return;
    }
    if (this.confirmando()) {
      return;
    }
    this.confirmando.set(true);
    this.http
      .patch<PedidoResponse>(`${environment.apiUrl}/api/pedidos/${id}/status`, { status: 'ENTREGUE' })
      .subscribe({
        next: () => {
          this.confirmando.set(false);
          this.pedidoPendenteConfirmacao.set(null);
          this.totalConfirmacao.set(null);
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

@Component({
  selector: 'app-escolher-venda-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule, DecimalPipe],
  template: `
    <h2 mat-dialog-title>Como vender?</h2>
    <mat-dialog-content>
      <p class="escolha-nome">{{ data.nome }}</p>
      <div class="escolha-acoes">
        <button type="button" class="escolha-btn" (click)="dialogRef.close('pacote')">
          <mat-icon>inventory_2</mat-icon>
          <span>Pacote</span>
          <strong>R$ {{ data.preco | number: '1.2-2' }}</strong>
        </button>
        <button type="button" class="escolha-btn escolha-btn--gold" (click)="dialogRef.close('unidade')">
          <mat-icon>liquor</mat-icon>
          <span>Unidade</span>
          <strong>R$ {{ data.precoUnidade | number: '1.2-2' }}</strong>
        </button>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" [mat-dialog-close]="undefined">Cancelar</button>
    </mat-dialog-actions>
  `,
  styles: `
    .escolha-nome {
      margin: 0 0 14px;
      color: #667085;
      font-size: 0.9rem;
    }
    .escolha-acoes {
      display: grid;
      gap: 10px;
    }
    .escolha-btn {
      display: flex;
      align-items: center;
      gap: 12px;
      width: 100%;
      padding: 14px 16px;
      border: 1.5px solid #e4e7ec;
      border-radius: 10px;
      background: #fff;
      cursor: pointer;
      text-align: left;
      font: 600 0.95rem/1.2 Inter, sans-serif;
      color: #171717;
    }
    .escolha-btn mat-icon {
      color: #98a2b3;
    }
    .escolha-btn span {
      flex: 1;
    }
    .escolha-btn strong {
      font-size: 1.05rem;
    }
    .escolha-btn--gold {
      border-color: #d2a410;
      background: #fff8e1;
    }
    .escolha-btn--gold mat-icon {
      color: #b98d00;
    }
    .escolha-btn:hover {
      border-color: #d2a410;
    }
  `,
})
export class EscolherVendaDialogComponent {
  readonly data = inject<Produto>(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<EscolherVendaDialogComponent, 'pacote' | 'unidade'>);
}
