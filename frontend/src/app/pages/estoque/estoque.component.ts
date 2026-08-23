import { Component, OnDestroy, OnInit, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { DatePipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { environment } from '../../../environments/environment';
import { ComboDto, ComboResponse, Produto } from '../../core/models';
import { AuthService } from '../../core/auth.service';
import { ComboService } from '../../core/combo.service';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { codigoPrincipal, produtoCombinaBusca } from '../../shared/barcode/produto-codigo-display';
import { BarcodeScanResult } from '../../shared/barcode/barcode-scan.types';
import { BarcodeScannerService } from '../../shared/barcode/barcode-scanner.service';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../shared/confirm-dialog/confirm-dialog.component';
import { Subscription } from 'rxjs';
import { normalizeScannedCode } from '../../shared/barcode/barcode-format.util';
import { BarcodeFeedbackService } from '../../shared/barcode/barcode-feedback.service';
import { produtoFotoUrl } from '../../shared/produto-foto';

const MAX_IMAGEM_COMBO_BYTES = 1024 * 1024;
const PAGE_SIZE = 10;

type EstoqueTab = 'visao' | 'movimentos' | 'alertas' | 'ajustes' | 'combos';
type FiltroNivel = 'todos' | 'critico' | 'baixo';

interface ComboItemForm {
  produtoId: number | null;
  quantidade: number;
}

@Component({
  selector: 'app-estoque',
  standalone: true,
  imports: [
    MatSelectModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSnackBarModule,
    MatProgressBarModule,
    MatIconModule,
    MatCheckboxModule,
    MatTooltipModule,
    MatSlideToggleModule,
    MatDialogModule,
    DatePipe,
    DecimalPipe,
    LowerCasePipe,
    StatusLabelPipe,
  ],
  templateUrl: './estoque.component.html',
  styleUrl: './estoque.component.scss',
})
export class EstoqueComponent implements OnInit, OnDestroy {
  readonly PAGE_SIZE = PAGE_SIZE;

  movimentos = signal<any[]>([]);
  baixo = signal<Produto[]>([]);
  todosProdutos = signal<Produto[]>([]);
  loadingMovs = signal(false);
  loadingBaixo = signal(false);
  loadingProdutos = signal(false);

  readonly tabAtiva = signal<EstoqueTab>('visao');
  readonly filtroProdutos = signal('');
  readonly filtroSomenteComCodigo = signal(false);
  readonly filtroCategoria = signal('TODAS');
  readonly filtroNivel = signal<FiltroNivel>('todos');
  readonly mostrarFiltrosAvancados = signal(false);
  readonly paginaProdutos = signal(1);
  produtoDestacadoId = signal<number | null>(null);

  totalProdutos = computed(() => this.todosProdutos().length);
  totalMovimentos = computed(() => this.movimentos().length);
  totalAlertas = computed(() => this.baixo().length);

  valorEstoque = computed(() =>
    this.todosProdutos().reduce((acc, p) => acc + (p.custo ?? 0) * p.estoqueAtual, 0),
  );

  movimentosHoje = computed(() => {
    const hoje = new Date();
    hoje.setHours(0, 0, 0, 0);
    return this.movimentos().filter((m) => {
      if (!m.createdAt) return false;
      const d = new Date(m.createdAt);
      d.setHours(0, 0, 0, 0);
      return d.getTime() === hoje.getTime();
    }).length;
  });

  produtosFiltrados = computed(() => {
    let list = this.todosProdutos();
    if (this.filtroSomenteComCodigo()) {
      list = list.filter((p) => !!codigoPrincipal(p));
    }
    const termo = this.filtroProdutos();
    if (termo.trim()) {
      list = list.filter((p) => produtoCombinaBusca(p, termo));
    }
    return list;
  });

  produtosVisaoGeral = computed(() => {
    let list = this.produtosFiltrados();
    const cat = this.filtroCategoria();
    if (cat !== 'TODAS') {
      list = list.filter((p) => p.categoria === cat);
    }
    const nivel = this.filtroNivel();
    if (nivel === 'critico') {
      list = list.filter((p) => this.isCritico(p));
    } else if (nivel === 'baixo') {
      list = list.filter((p) => this.isBaixo(p));
    }
    return list;
  });

  produtosPaginados = computed(() => {
    const list = this.produtosVisaoGeral();
    const start = (this.paginaProdutos() - 1) * PAGE_SIZE;
    return list.slice(start, start + PAGE_SIZE);
  });

  totalPaginasProdutos = computed(() =>
    Math.max(1, Math.ceil(this.produtosVisaoGeral().length / PAGE_SIZE)),
  );

  paginasProdutos = computed(() =>
    Array.from({ length: this.totalPaginasProdutos() }, (_, i) => i + 1),
  );

  movimentosRecentes = computed(() => this.movimentos().slice(0, 3));
  alertasTop2 = computed(() => this.baixo().slice(0, 2));

  readonly categoriasProduto = computed(() => {
    const cats = new Set(this.todosProdutos().map((p) => p.categoria));
    return ['TODAS', ...Array.from(cats).sort()];
  });

  ajusteId: number | null = null;
  ajusteQtd = 0;
  ajusteMotivo = '';

  entradaId: number | null = null;
  entradaQtd = 0;
  entradaMotivo = '';

  // ----- Combos -----
  readonly categorias = [
    'CERVEJAS',
    'DESTILADOS',
    'REFRIGERANTES',
    'ENERGETICOS',
    'PETISCOS',
    'COMBOS',
  ];
  combos = signal<ComboResponse[]>([]);
  loadingCombos = signal(false);
  filtroCombos = '';

  comboEditId = signal<number | null>(null);
  comboNome = '';
  comboCodigoBarras = '';
  comboCodigoQr = '';
  comboCategoria = 'COMBOS';
  comboDescricao = '';
  comboImagem = signal<string | null>(null);
  comboPreco = signal<number>(0);
  comboAtivo = true;
  comboItens = signal<ComboItemForm[]>([]);
  comboSalvando = signal(false);
  produtoParaAdicionar: number | null = null;

  readonly combosFiltrados = computed(() => {
    const termo = this.filtroCombos.trim().toLowerCase();
    const list = this.combos();
    if (!termo) return list;
    return list.filter(
      (c) =>
        c.nome.toLowerCase().includes(termo) ||
        (c.codigo ?? '').toLowerCase().includes(termo) ||
        (c.codigoBarras ?? '').toLowerCase().includes(termo),
    );
  });

  readonly comboMaisVendidos = computed(() =>
    [...this.combos()]
      .filter((c) => c.quantidadeVendida > 0)
      .sort((a, b) => b.quantidadeVendida - a.quantidadeVendida)
      .slice(0, 5),
  );

  readonly comboCustoTotal = computed(() => {
    const mapa = new Map(this.todosProdutos().map((p) => [p.id, p]));
    return this.comboItens().reduce((acc, it) => {
      if (!it.produtoId) return acc;
      const prod = mapa.get(it.produtoId);
      const custo = prod?.custo ?? 0;
      return acc + custo * (it.quantidade || 0);
    }, 0);
  });

  readonly comboLucro = computed(() => this.comboPreco() - this.comboCustoTotal());

  readonly comboMargem = computed(() => {
    const preco = this.comboPreco();
    if (preco <= 0) return 0;
    return (this.comboLucro() / preco) * 100;
  });

  readonly produtosDisponiveisParaCombo = computed(() => {
    const usados = new Set(this.comboItens().map((i) => i.produtoId));
    return this.todosProdutos().filter((p) => !usados.has(p.id));
  });

  private scanSub?: Subscription;

  constructor(
    private readonly http: HttpClient,
    private readonly snack: MatSnackBar,
    readonly scanner: BarcodeScannerService,
    private readonly feedback: BarcodeFeedbackService,
    readonly auth: AuthService,
    private readonly comboService: ComboService,
    private readonly dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.reloadMovs();
    this.reloadBaixo();
    this.reloadProdutos();
    this.reloadCombos();
  }

  ngOnDestroy(): void {
    this.scanSub?.unsubscribe();
    this.scanner.close();
  }

  fotoProduto(p: Produto): string {
    return produtoFotoUrl(p.nome, p.categoria);
  }

  isCritico(p: Produto): boolean {
    return p.estoqueAtual === 0;
  }

  isBaixo(p: Produto): boolean {
    return p.estoqueAtual > 0 && p.estoqueAtual <= p.estoqueMinimo;
  }

  valorProdutoEstoque(p: Produto): number {
    return (p.custo ?? 0) * p.estoqueAtual;
  }

  setTab(tab: EstoqueTab): void {
    this.tabAtiva.set(tab);
  }

  setFiltroProdutos(valor: string): void {
    this.filtroProdutos.set(valor);
    this.paginaProdutos.set(1);
  }

  setFiltroCategoria(valor: string): void {
    this.filtroCategoria.set(valor);
    this.paginaProdutos.set(1);
  }

  setFiltroNivel(valor: FiltroNivel): void {
    this.filtroNivel.set(valor);
    this.paginaProdutos.set(1);
  }

  irPaginaProdutos(p: number): void {
    const max = this.totalPaginasProdutos();
    this.paginaProdutos.set(Math.min(Math.max(1, p), max));
  }

  novaMovimentacao(): void {
    this.tabAtiva.set('ajustes');
    setTimeout(() => {
      document.getElementById('entrada-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 80);
  }

  movimentarProduto(p: Produto): void {
    this.entradaId = p.id;
    this.entradaQtd = 0;
    this.entradaMotivo = '';
    this.ajusteId = p.id;
    this.ajusteQtd = p.estoqueAtual;
    this.tabAtiva.set('ajustes');
    setTimeout(() => {
      document.getElementById('ajustes-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 80);
  }

  reporAgora(p: Produto): void {
    this.entradaId = p.id;
    this.entradaQtd = Math.max(p.estoqueMinimo - p.estoqueAtual, 1);
    this.entradaMotivo = 'Reposição de estoque';
    this.tabAtiva.set('ajustes');
    setTimeout(() => {
      document.getElementById('entrada-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 80);
  }

  tipoMovimentoIcon(tipo: string): string {
    if (tipo === 'ENTRADA') return 'south';
    if (tipo === 'AJUSTE') return 'sync_alt';
    return 'north';
  }

  tipoMovimentoClass(tipo: string): string {
    if (tipo === 'ENTRADA') return 'est-mov--entrada';
    if (tipo === 'AJUSTE') return 'est-mov--ajuste';
    return 'est-mov--saida';
  }

  exportar(): void {
    const rows = this.produtosVisaoGeral();
    if (!rows.length) {
      this.snack.open('Nenhum produto para exportar.', 'OK', { duration: 2200 });
      return;
    }
    const header = ['Produto', 'Código', 'Categoria', 'Estoque', 'Mínimo', 'Custo', 'Valor em estoque'];
    const lines = rows.map((p) => [
      p.nome,
      this.codigoExibicao(p) ?? '',
      p.categoria,
      String(p.estoqueAtual),
      String(p.estoqueMinimo),
      String(p.custo ?? 0),
      String(this.valorProdutoEstoque(p)),
    ]);
    const csv = [header, ...lines]
      .map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(';'))
      .join('\n');
    const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `estoque-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    this.snack.open('Exportação concluída.', 'OK', { duration: 2000 });
  }

  codigoExibicao(p: Produto): string | null {
    return codigoPrincipal(p);
  }

  tipoCodigo(p: Produto): string {
    if (p.codigoBarras) return 'Barras';
    if (p.codigoQr) return 'QR';
    if (p.codigoInterno) return 'Interno';
    return '';
  }

  reloadProdutos(): void {
    this.loadingProdutos.set(true);
    this.http.get<Produto[]>(`${environment.apiUrl}/api/produtos`).subscribe({
      next: (p) => {
        this.todosProdutos.set(p.filter((x) => x.ativo));
        this.loadingProdutos.set(false);
      },
      error: () => this.loadingProdutos.set(false),
    });
  }

  reloadMovs() {
    this.loadingMovs.set(true);
    this.http.get<any[]>(`${environment.apiUrl}/api/estoque/movimentos`).subscribe({
      next: (m) => {
        this.movimentos.set(m);
        this.loadingMovs.set(false);
      },
      error: () => this.loadingMovs.set(false),
    });
  }

  reloadBaixo() {
    this.loadingBaixo.set(true);
    this.http.get<Produto[]>(`${environment.apiUrl}/api/estoque/baixo`).subscribe({
      next: (b) => {
        this.baixo.set(b);
        this.loadingBaixo.set(false);
      },
      error: () => this.loadingBaixo.set(false),
    });
  }

  toggleScannerBusca(): void {
    if (this.scanner.isOpen()) {
      this.scanSub?.unsubscribe();
      this.scanner.close();
      return;
    }
    this.scanSub?.unsubscribe();
    this.scanSub = this.scanner.scanned$.subscribe((r) => this.aplicarBuscaPorCodigo(r));
    this.scanner.open({ mode: 'continuous' });
  }

  private aplicarBuscaPorCodigo(r: BarcodeScanResult): void {
    if (r.isPix) {
      this.snack.open('QR Pix — informe um código de produto.', 'OK', { duration: 2800 });
      return;
    }
    const code = normalizeScannedCode(r.code);
    this.setFiltroProdutos(code);
    const found = this.todosProdutos().find(
      (p) =>
        p.codigoBarras === code ||
        p.codigoQr === code ||
        p.codigoInterno === code,
    );
    if (found) {
      this.produtoDestacadoId.set(found.id);
      this.feedback.success();
      this.snack.open(`Produto: ${found.nome}`, 'OK', { duration: 2000 });
    } else {
      this.feedback.warn();
      this.snack.open('Código não encontrado na lista carregada.', 'OK', { duration: 3000 });
    }
  }

  copiarCodigo(p: Produto, ev: Event): void {
    ev.stopPropagation();
    const code = this.codigoExibicao(p);
    if (!code) return;
    void navigator.clipboard?.writeText(code).then(() => {
      this.snack.open('Código copiado.', 'OK', { duration: 1500 });
    });
  }

  selecionarParaAjuste(p: Produto): void {
    this.ajusteId = p.id;
    this.ajusteQtd = p.estoqueAtual;
    this.snack.open(`"${p.nome}" selecionado para ajuste.`, 'OK', { duration: 1800 });
  }

  selecionarParaEntrada(p: Produto): void {
    this.entradaId = p.id;
    this.snack.open(`"${p.nome}" selecionado para entrada.`, 'OK', { duration: 1800 });
  }

  ajustar() {
    if (!this.auth.isAdmin() || !this.ajusteId) return;
    this.http
      .post(`${environment.apiUrl}/api/estoque/produto/${this.ajusteId}/ajuste`, {
        novaQuantidade: this.ajusteQtd,
        motivo: this.ajusteMotivo,
      })
      .subscribe({
        next: () => {
          this.snack.open('Ajuste registrado', 'OK', { duration: 2000 });
          this.reloadMovs();
          this.reloadBaixo();
          this.reloadProdutos();
        },
        error: (e) => this.snack.open(e?.error?.erro ?? 'Erro', 'OK', { duration: 3500 }),
      });
  }

  entrada() {
    if (!this.entradaId) return;
    this.http
      .post(`${environment.apiUrl}/api/estoque/produto/${this.entradaId}/entrada`, {
        quantidade: this.entradaQtd,
        motivo: this.entradaMotivo,
      })
      .subscribe({
        next: () => {
          this.snack.open('Entrada registrada', 'OK', { duration: 2000 });
          this.reloadMovs();
          this.reloadBaixo();
          this.reloadProdutos();
        },
        error: (e) => this.snack.open(e?.error?.erro ?? 'Erro', 'OK', { duration: 3500 }),
      });
  }

  // ===================== COMBOS =====================

  reloadCombos(): void {
    this.loadingCombos.set(true);
    this.comboService.listar(false).subscribe({
      next: (list) => {
        this.combos.set(list);
        this.loadingCombos.set(false);
      },
      error: () => this.loadingCombos.set(false),
    });
  }

  nomeProduto(id: number | null): string {
    if (!id) return '';
    return this.todosProdutos().find((p) => p.id === id)?.nome ?? `#${id}`;
  }

  novoCombo(): void {
    this.comboEditId.set(null);
    this.comboNome = '';
    this.comboCodigoBarras = '';
    this.comboCodigoQr = '';
    this.comboCategoria = 'COMBOS';
    this.comboDescricao = '';
    this.comboImagem.set(null);
    this.comboPreco.set(0);
    this.comboAtivo = true;
    this.comboItens.set([]);
    this.produtoParaAdicionar = null;
  }

  editarCombo(c: ComboResponse): void {
    this.comboEditId.set(c.id);
    this.comboNome = c.nome;
    this.comboCodigoBarras = c.codigoBarras ?? '';
    this.comboCodigoQr = c.codigoQr ?? '';
    this.comboCategoria = c.categoria ?? 'COMBOS';
    this.comboDescricao = c.descricao ?? '';
    this.comboImagem.set(c.imagem ?? null);
    this.comboPreco.set(Number(c.precoVenda) || 0);
    this.comboAtivo = c.ativo;
    this.comboItens.set(
      c.itens.map((i) => ({ produtoId: i.produtoId, quantidade: i.quantidade })),
    );
    this.produtoParaAdicionar = null;
    this.snack.open(`Editando "${c.nome}".`, 'OK', { duration: 1800 });
  }

  cancelarCombo(): void {
    this.novoCombo();
  }

  adicionarItemCombo(): void {
    if (!this.produtoParaAdicionar) return;
    const id = this.produtoParaAdicionar;
    if (this.comboItens().some((i) => i.produtoId === id)) {
      this.snack.open('Produto já adicionado.', 'OK', { duration: 1800 });
      return;
    }
    this.comboItens.set([...this.comboItens(), { produtoId: id, quantidade: 1 }]);
    this.produtoParaAdicionar = null;
  }

  removerItemCombo(produtoId: number | null): void {
    this.comboItens.set(this.comboItens().filter((i) => i.produtoId !== produtoId));
  }

  alterarQtdItem(produtoId: number | null, qtd: number): void {
    const valor = Math.max(1, Math.floor(qtd || 1));
    this.comboItens.set(
      this.comboItens().map((i) => (i.produtoId === produtoId ? { ...i, quantidade: valor } : i)),
    );
  }

  onImagemCombo(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (file.size > MAX_IMAGEM_COMBO_BYTES) {
      this.snack.open('Imagem muito grande (máx. 1MB).', 'OK', { duration: 3000 });
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        this.comboImagem.set(reader.result);
      }
    };
    reader.readAsDataURL(file);
  }

  removerImagemCombo(): void {
    this.comboImagem.set(null);
  }

  salvarCombo(): void {
    if (!this.auth.isAdmin()) return;
    if (!this.comboNome.trim()) {
      this.snack.open('Informe o nome do combo.', 'OK', { duration: 2500 });
      return;
    }
    const itens = this.comboItens().filter((i) => i.produtoId && i.quantidade > 0);
    if (itens.length === 0) {
      this.snack.open('Adicione ao menos um produto ao combo.', 'OK', { duration: 2800 });
      return;
    }
    if (this.comboPreco() <= 0) {
      this.snack.open('Informe um preço de venda válido.', 'OK', { duration: 2500 });
      return;
    }

    const dto: ComboDto = {
      id: this.comboEditId(),
      nome: this.comboNome.trim(),
      codigoBarras: this.comboCodigoBarras.trim() || null,
      codigoQr: this.comboCodigoQr.trim() || null,
      categoria: this.comboCategoria,
      descricao: this.comboDescricao.trim() || null,
      imagem: this.comboImagem(),
      precoVenda: this.comboPreco(),
      ativo: this.comboAtivo,
      itens: itens.map((i) => ({ produtoId: i.produtoId as number, quantidade: i.quantidade })),
    };

    this.comboSalvando.set(true);
    const id = this.comboEditId();
    const req$ = id ? this.comboService.atualizar(id, dto) : this.comboService.criar(dto);
    req$.subscribe({
      next: () => {
        this.comboSalvando.set(false);
        this.snack.open(id ? 'Combo atualizado.' : 'Combo criado.', 'OK', { duration: 2200 });
        this.novoCombo();
        this.reloadCombos();
      },
      error: (e) => {
        this.comboSalvando.set(false);
        this.snack.open(e?.error?.erro ?? 'Erro ao salvar combo.', 'OK', { duration: 3800 });
      },
    });
  }

  excluirCombo(c: ComboResponse): void {
    if (!this.auth.isAdmin()) return;
    const data: ConfirmDialogData = {
      titulo: 'Excluir combo',
      mensagem: `Deseja excluir o combo "${c.nome}"? Ele deixará de aparecer no PDV.`,
      confirmLabel: 'Excluir',
      confirmColor: 'warn',
    };
    this.dialog
      .open(ConfirmDialogComponent, { data, width: '420px' })
      .afterClosed()
      .subscribe((ok) => {
        if (!ok) return;
        this.comboService.excluir(c.id).subscribe({
          next: () => {
            this.snack.open('Combo excluído.', 'OK', { duration: 2200 });
            if (this.comboEditId() === c.id) this.novoCombo();
            this.reloadCombos();
          },
          error: (e) => this.snack.open(e?.error?.erro ?? 'Erro ao excluir.', 'OK', { duration: 3500 }),
        });
      });
  }
}
