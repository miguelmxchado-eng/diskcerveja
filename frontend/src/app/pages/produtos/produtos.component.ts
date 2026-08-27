import { Component, ElementRef, OnDestroy, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatStepper, MatStepperModule } from '@angular/material/stepper';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DecimalPipe } from '@angular/common';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { StatusLabelPipe } from '../../shared/pipes/status-label.pipe';
import { environment } from '../../../environments/environment';
import { Produto } from '../../core/models';
import { AuthService } from '../../core/auth.service';
import { BarcodeScanResult } from '../../shared/barcode/barcode-scan.types';
import { BarcodeScannerService } from '../../shared/barcode/barcode-scanner.service';
import { Subscription } from 'rxjs';
import { ProdutoCodigoService } from '../../shared/barcode/produto-codigo.service';
import { codigoPrincipal, produtoCombinaBusca } from '../../shared/barcode/produto-codigo-display';
import { formatLabel, isValidProductCode, normalizeScannedCode } from '../../shared/barcode/barcode-format.util';
import { BarcodeFeedbackService } from '../../shared/barcode/barcode-feedback.service';
import { produtoFotoUrl } from '../../shared/produto-foto';

const MAX_IMAGEM_BYTES = 1024 * 1024;

const CATEGORIAS = [
  'CERVEJAS',
  'DESTILADOS',
  'REFRIGERANTES',
  'ENERGETICOS',
  'PETISCOS',
  'COMBOS',
] as const;

type ProdutoComImagem = Produto & { imagemUrl?: string | null };

@Component({
  selector: 'app-produtos',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatProgressBarModule,
    MatDialogModule,
    MatIconModule,
    MatStepperModule,
    MatCheckboxModule,
    MatTooltipModule,
    DecimalPipe,
    StatusLabelPipe,
  ],
  templateUrl: './produtos.component.html',
  styleUrl: './produtos.component.scss',
})
export class ProdutosComponent implements OnInit, OnDestroy {
  @ViewChild('cadastroStepper') private cadastroStepper?: MatStepper;
  @ViewChild('wizardPanel') private wizardPanel?: ElementRef<HTMLElement>;

  readonly categorias = CATEGORIAS;

  produtos = signal<Produto[]>([]);
  loading = signal(false);
  salvando = signal(false);
  imagemPreview = signal<string | null>(null);
  readonly produtoEmEdicao = signal<Produto | null>(null);
  readonly formularioAberto = signal(false);
  readonly editando = computed(() => this.produtoEmEdicao() != null);

  readonly filtroTabela = signal('');
  readonly filtroSomenteComCodigo = signal(false);
  readonly filtroCategoria = signal('');
  readonly filtroStatus = signal('');
  readonly filtroBaixoEstoque = signal(false);
  readonly filtrosAvancadosAbertos = signal(false);
  readonly selectedIds = signal<Set<number>>(new Set());
  readonly page = signal(1);
  readonly pageSize = signal(20);
  readonly menuProdutoId = signal<number | null>(null);

  ultimaLeituraPreview = signal<{ code: string; format: string } | null>(null);
  codigoDuplicadoMsg = signal<string | null>(null);
  travarCodigoAposScan = false;
  codigoSomenteLeitura = false;

  totalProdutos = computed(() => this.produtos().length);
  totalAtivos = computed(() => this.produtos().filter((p) => p.ativo).length);
  totalInativos = computed(() => this.totalProdutos() - this.totalAtivos());
  totalBaixoEstoque = computed(() =>
    this.produtos().filter((p) => p.ativo && p.estoqueAtual <= p.estoqueMinimo).length,
  );

  produtosFiltrados = computed(() => {
    let list = this.produtos();
    if (this.filtroSomenteComCodigo()) {
      list = list.filter((p) => !!codigoPrincipal(p));
    }
    const cat = this.filtroCategoria();
    if (cat) {
      list = list.filter((p) => p.categoria === cat);
    }
    const status = this.filtroStatus();
    if (status === 'ativo') {
      list = list.filter((p) => p.ativo);
    } else if (status === 'inativo') {
      list = list.filter((p) => !p.ativo);
    }
    if (this.filtroBaixoEstoque()) {
      list = list.filter((p) => p.ativo && p.estoqueAtual <= p.estoqueMinimo);
    }
    const termo = this.filtroTabela().trim();
    if (termo) {
      const q = termo.toLowerCase();
      list = list.filter(
        (p) =>
          produtoCombinaBusca(p, termo) ||
          p.categoria.toLowerCase().includes(q) ||
          p.categoria.replace(/_/g, ' ').toLowerCase().includes(q),
      );
    }
    return list;
  });

  readonly totalPaginas = computed(() =>
    Math.max(1, Math.ceil(this.produtosFiltrados().length / this.pageSize())),
  );

  readonly produtosPaginados = computed(() => {
    const start = (this.page() - 1) * this.pageSize();
    return this.produtosFiltrados().slice(start, start + this.pageSize());
  });

  readonly paginationLabel = computed(() => {
    const total = this.produtosFiltrados().length;
    if (total === 0) return '0 de 0';
    const start = (this.page() - 1) * this.pageSize() + 1;
    const end = Math.min(this.page() * this.pageSize(), total);
    return `${start}–${end} de ${total}`;
  });

  readonly paginasVisiveis = computed(() => {
    const total = this.totalPaginas();
    const atual = this.page();
    const pages: number[] = [];
    const from = Math.max(1, atual - 2);
    const to = Math.min(total, from + 4);
    for (let i = from; i <= to; i++) pages.push(i);
    return pages;
  });

  readonly todosSelecionadosNaPagina = computed(() => {
    const pagina = this.produtosPaginados();
    if (!pagina.length) return false;
    const sel = this.selectedIds();
    return pagina.every((p) => sel.has(p.id));
  });

  novoNome = '';
  novoCodigoBarras = '';
  novoCodigoQr = '';
  novoCat = 'CERVEJAS';
  novoPreco: number | null = null;
  novoCusto: number | null = null;
  novoMin = 0;
  novoEst = 0;
  novoMarca = '';
  novoDescricao = '';
  codigoInternoEdicao: string | null = null;

  private scanSub?: Subscription;

  constructor(
    private readonly http: HttpClient,
    private readonly snack: MatSnackBar,
    private readonly dialog: MatDialog,
    readonly scanner: BarcodeScannerService,
    private readonly codigoService: ProdutoCodigoService,
    private readonly feedback: BarcodeFeedbackService,
    private readonly router: Router,
    readonly auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.scanSub?.unsubscribe();
    this.scanner.close();
  }

  podeAvancarDados(): boolean {
    return this.novoNome.trim().length > 0;
  }

  podeAvancarPreco(): boolean {
    const venda = Number(this.novoPreco);
    const compra = Number(this.novoCusto);
    return this.novoPreco != null
      && this.novoCusto != null
      && Number.isFinite(venda)
      && Number.isFinite(compra)
      && venda > 0
      && compra >= 0;
  }

  lucroCadastro(): number | null {
    if (!this.podeAvancarPreco()) {
      return null;
    }
    return this.toMoney(this.novoPreco) - this.toMoney(this.novoCusto);
  }

  margemCadastro(): number | null {
    const lucro = this.lucroCadastro();
    const venda = this.toMoney(this.novoPreco);
    if (lucro === null || venda <= 0) {
      return null;
    }
    return (lucro / venda) * 100;
  }

  lucroUnitario(p: Produto): number {
    return this.toMoney(p.preco) - this.toMoney(p.custo ?? 0);
  }

  margemPercent(p: Produto): number | null {
    const venda = this.toMoney(p.preco);
    if (venda <= 0) {
      return null;
    }
    return (this.lucroUnitario(p) / venda) * 100;
  }

  produtoImagem(p: Produto): string {
    const ext = p as ProdutoComImagem;
    if (ext.imagemUrl) {
      return ext.imagemUrl;
    }
    return produtoFotoUrl(p.nome, p.categoria);
  }

  estoqueBadgeClass(p: Produto): 'danger' | 'warn' | 'ok' {
    if (p.estoqueAtual < p.estoqueMinimo) return 'danger';
    if (p.estoqueAtual === p.estoqueMinimo) return 'warn';
    return 'ok';
  }

  codigoBarrasResumo(): string | null {
    const c = this.novoCodigoBarras.trim();
    return c.length ? c : null;
  }

  codigoQrResumo(): string | null {
    const c = this.novoCodigoQr.trim();
    return c.length ? c : null;
  }

  codigoExibicao(p: Produto): string | null {
    return codigoPrincipal(p);
  }

  tipoCodigoExibicao(p: Produto): string {
    if (p.codigoBarras) return 'Barras';
    if (p.codigoQr) return 'QR';
    if (p.codigoInterno) return 'Interno';
    return '';
  }

  isSelected(id: number): boolean {
    return this.selectedIds().has(id);
  }

  mostrarAcoesPrimarias(p: Produto): boolean {
    const sel = this.selectedIds();
    return sel.size === 0 || sel.has(p.id);
  }

  toggleSelect(id: number): void {
    const next = new Set(this.selectedIds());
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    this.selectedIds.set(next);
  }

  toggleSelectAllPagina(): void {
    const pagina = this.produtosPaginados();
    const next = new Set(this.selectedIds());
    const allOnPage = pagina.every((p) => next.has(p.id));
    if (allOnPage) {
      pagina.forEach((p) => next.delete(p.id));
    } else {
      pagina.forEach((p) => next.add(p.id));
    }
    this.selectedIds.set(next);
  }

  limparSelecao(): void {
    this.selectedIds.set(new Set());
  }

  resetPagina(): void {
    this.page.set(1);
  }

  irParaPagina(n: number): void {
    const clamped = Math.max(1, Math.min(n, this.totalPaginas()));
    this.page.set(clamped);
  }

  toggleFiltroBaixoEstoque(): void {
    this.filtroBaixoEstoque.update((v) => !v);
    this.resetPagina();
  }

  toggleFiltrosAvancados(): void {
    this.filtrosAvancadosAbertos.update((v) => !v);
  }

  importarProdutos(): void {
    this.snack.open('Importação em breve.', 'OK', { duration: 2500 });
  }

  /** Lista em papel: nome + preço de venda (para quem não usa o sistema). */
  imprimirListaPrecos(): void {
    const ativos = this.produtos()
      .filter((p) => p.ativo)
      .sort((a, b) => {
        const ca = (a.categoria || '').localeCompare(b.categoria || '', 'pt-BR');
        if (ca !== 0) return ca;
        return (a.nome || '').localeCompare(b.nome || '', 'pt-BR');
      });

    if (!ativos.length) {
      this.snack.open('Não há produtos ativos para imprimir.', 'OK', { duration: 2500 });
      return;
    }

    const hoje = new Date().toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });

    const categorias: Record<string, string> = {
      CERVEJAS: 'Cervejas',
      DESTILADOS: 'Destilados',
      REFRIGERANTES: 'Refrigerantes',
      ENERGETICOS: 'Energéticos',
      PETISCOS: 'Petiscos',
      COMBOS: 'Combos',
    };

    let ultimaCat = '';
    const linhas: string[] = [];
    for (const p of ativos) {
      const cat = categorias[p.categoria] ?? p.categoria ?? 'Outros';
      if (cat !== ultimaCat) {
        ultimaCat = cat;
        linhas.push(`<tr class="cat"><td colspan="2">${this.escapeHtml(cat)}</td></tr>`);
      }
      const preco = Number(p.preco).toLocaleString('pt-BR', {
        style: 'currency',
        currency: 'BRL',
      });
      linhas.push(
        `<tr><td class="nome">${this.escapeHtml(p.nome)}</td><td class="preco">${preco}</td></tr>`,
      );
    }

    const html = `<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8" />
  <title>Lista de preços — Empório Machado</title>
  <style>
    @page { margin: 14mm; }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: Arial, Helvetica, sans-serif;
      color: #111;
    }
    h1 {
      margin: 0 0 4px;
      font-size: 22pt;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }
    .sub {
      margin: 0 0 18px;
      font-size: 11pt;
      color: #444;
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th {
      text-align: left;
      font-size: 10pt;
      padding: 8px 6px;
      border-bottom: 2px solid #111;
      text-transform: uppercase;
    }
    td {
      padding: 10px 6px;
      border-bottom: 1px solid #ddd;
      font-size: 14pt;
      vertical-align: middle;
    }
    tr.cat td {
      padding-top: 16px;
      padding-bottom: 6px;
      border-bottom: 1px solid #111;
      font-size: 12pt;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      background: #f5f5f5;
    }
    td.nome { width: 72%; }
    td.preco {
      width: 28%;
      text-align: right;
      font-weight: 700;
      font-size: 16pt;
      white-space: nowrap;
    }
    .rodape {
      margin-top: 20px;
      font-size: 9pt;
      color: #666;
    }
    @media print {
      .no-print { display: none !important; }
    }
  </style>
</head>
<body>
  <h1>Empório Machado</h1>
  <p class="sub">Lista de preços · ${hoje} · ${ativos.length} produtos</p>
  <table>
    <thead>
      <tr><th>Produto</th><th style="text-align:right">Preço</th></tr>
    </thead>
    <tbody>
      ${linhas.join('')}
    </tbody>
  </table>
  <p class="rodape">Impresso pelo sistema Empório Machado. Valores sujeitos a alteração.</p>
</body>
</html>`;

    this.imprimirHtml(html);
  }

  /** Imprime sem popup (iframe oculto) — evita about:blank / bloqueio de pop-up. */
  private imprimirHtml(html: string): void {
    document.getElementById('em-print-frame')?.remove();

    const iframe = document.createElement('iframe');
    iframe.id = 'em-print-frame';
    iframe.setAttribute('aria-hidden', 'true');
    iframe.style.cssText =
      'position:fixed;right:0;bottom:0;width:0;height:0;border:0;opacity:0;pointer-events:none';
    document.body.appendChild(iframe);

    const win = iframe.contentWindow;
    const doc = iframe.contentDocument ?? win?.document;
    if (!win || !doc) {
      iframe.remove();
      this.snack.open('Não foi possível abrir a impressão. Tente de novo.', 'OK', {
        duration: 3500,
      });
      return;
    }

    doc.open();
    doc.write(html);
    doc.close();

    // Aguarda o layout do iframe antes de chamar a impressão.
    setTimeout(() => {
      try {
        win.focus();
        win.print();
      } catch {
        this.snack.open('Não foi possível abrir a impressão. Tente de novo.', 'OK', {
          duration: 3500,
        });
      } finally {
        setTimeout(() => iframe.remove(), 1000);
      }
    }, 300);
  }

  private escapeHtml(value: string): string {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
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
    this.filtroTabela.set(code);
    this.resetPagina();
    const found = this.produtos().find(
      (p) => p.codigoBarras === code || p.codigoQr === code || p.codigoInterno === code,
    );
    if (found) {
      this.feedback.success();
      this.snack.open(`Produto: ${found.nome}`, 'OK', { duration: 2000 });
    } else {
      this.feedback.warn();
      this.snack.open('Código não encontrado na lista carregada.', 'OK', { duration: 3000 });
    }
  }

  toggleMenuProduto(id: number, ev: Event): void {
    ev.stopPropagation();
    this.menuProdutoId.update((cur) => (cur === id ? null : id));
  }

  fecharMenuProduto(): void {
    this.menuProdutoId.set(null);
  }

  ajustarEstoque(p: Produto): void {
    this.fecharMenuProduto();
    void this.router.navigate(['/estoque'], { queryParams: { produto: p.id } });
    this.snack.open(`Ajuste de estoque: "${p.nome}" — abra a aba Produtos em Estoque.`, 'OK', {
      duration: 3500,
    });
  }

  executarAcaoLote(acao: string): void {
    const ids = [...this.selectedIds()];
    if (!ids.length) return;
    if (acao === 'desativar') {
      if (!this.auth.isAdmin()) return;
      this.dialog
        .open(ConfirmDialogComponent, {
          data: {
            titulo: 'Desativar produtos',
            mensagem: `Deseja desativar ${ids.length} produto(s) selecionado(s)?`,
            confirmLabel: 'Desativar',
            confirmColor: 'warn',
          },
          width: '360px',
        })
        .afterClosed()
        .subscribe((ok) => {
          if (!ok) return;
          let done = 0;
          ids.forEach((id) => {
            this.http.delete(`${environment.apiUrl}/api/produtos/${id}`).subscribe({
              next: () => {
                done++;
                if (done === ids.length) {
                  this.snack.open('Produtos desativados.', 'OK', { duration: 2000 });
                  this.limparSelecao();
                  this.reload();
                }
              },
            });
          });
        });
      return;
    }
    this.snack.open('Ação em breve.', 'OK', { duration: 2000 });
  }

  private toInt(v: unknown, fallback = 0): number {
    const n = Number(v);
    return Number.isFinite(n) ? Math.trunc(n) : fallback;
  }

  private toMoney(v: unknown): number {
    const n = Number(v);
    return Number.isFinite(n) && n >= 0 ? n : 0;
  }

  onImagemSelecionada(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (file.size > MAX_IMAGEM_BYTES) {
      this.snack.open('Imagem deve ter no máximo 1MB.', 'OK', { duration: 3000 });
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const r = reader.result;
      if (typeof r === 'string') {
        this.imagemPreview.set(r);
      }
    };
    reader.readAsDataURL(file);
  }

  abrirScanner(campo: 'barras' | 'qr'): void {
    this.scanSub?.unsubscribe();
    this.scanSub = this.scanner.scanned$.subscribe((r) => this.aplicarLeituraCadastro(r, campo));
    this.scanner.open({ mode: 'continuous' });
  }

  fecharScanner(): void {
    this.scanSub?.unsubscribe();
    this.scanner.close();
  }

  private aplicarLeituraCadastro(r: BarcodeScanResult, campo: 'barras' | 'qr'): void {
    if (r.isPix) {
      this.snack.open('QR Pix detectado — use um código de produto.', 'OK', { duration: 3500 });
      this.feedback.warn();
      return;
    }
    const code = normalizeScannedCode(r.code);
    if (!isValidProductCode(code)) {
      this.snack.open('Código inválido.', 'OK', { duration: 2500 });
      this.feedback.error();
      return;
    }
    if (campo === 'barras') {
      this.novoCodigoBarras = code;
    } else {
      this.novoCodigoQr = code;
    }
    this.ultimaLeituraPreview.set({ code, format: formatLabel(code) });
    if (this.travarCodigoAposScan) {
      this.codigoSomenteLeitura = true;
    }
    void this.validarCodigoEmTempoReal(code);
    this.feedback.success();
    // No cadastro, após preencher o campo, fecha a câmera (leitura única).
    this.fecharScanner();
  }

  validarCodigoEmTempoReal(codigo?: string): void {
    const c = normalizeScannedCode(codigo ?? (this.novoCodigoBarras || this.novoCodigoQr));
    if (!c) {
      this.codigoDuplicadoMsg.set(null);
      return;
    }
    this.codigoService.validarCodigo(c, this.produtoEmEdicao()?.id).subscribe({
      next: (v) => {
        this.codigoDuplicadoMsg.set(
          v.disponivel ? null : `Código já usado em "${v.produtoNome ?? 'outro produto'}".`,
        );
      },
    });
  }

  copiarCodigo(p: Produto, ev: Event): void {
    ev.stopPropagation();
    const code = this.codigoExibicao(p);
    if (!code) return;
    void navigator.clipboard?.writeText(code).then(() => {
      this.snack.open('Código copiado.', 'OK', { duration: 1500 });
    });
  }

  cancelarCadastro(): void {
    this.limparFormulario();
    this.formularioAberto.set(false);
    this.fecharScanner();
  }

  abrirNovoCadastro(): void {
    if (!this.auth.isAdmin()) {
      return;
    }
    this.limparFormulario();
    this.formularioAberto.set(true);
    this.scrollFormulario();
  }

  private limparFormulario(): void {
    this.produtoEmEdicao.set(null);
    this.codigoInternoEdicao = null;
    this.novoNome = '';
    this.novoCodigoBarras = '';
    this.novoCodigoQr = '';
    this.novoCat = 'CERVEJAS';
    this.novoPreco = null;
    this.novoCusto = null;
    this.novoMin = 0;
    this.novoEst = 0;
    this.novoMarca = '';
    this.novoDescricao = '';
    this.imagemPreview.set(null);
    this.ultimaLeituraPreview.set(null);
    this.codigoDuplicadoMsg.set(null);
    this.codigoSomenteLeitura = false;
    this.cadastroStepper?.reset();
  }

  private scrollFormulario(): void {
    setTimeout(() => {
      this.wizardPanel?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 50);
  }

  editarProduto(p: Produto): void {
    if (!this.auth.isAdmin()) {
      return;
    }
    this.fecharMenuProduto();
    this.produtoEmEdicao.set(p);
    this.codigoInternoEdicao = p.codigoInterno ?? null;
    this.novoNome = p.nome;
    this.novoCodigoBarras = p.codigoBarras ?? '';
    this.novoCodigoQr = p.codigoQr ?? '';
    this.novoCat = p.categoria;
    this.novoPreco = Number(p.preco);
    this.novoCusto = Number(p.custo ?? 0);
    this.novoMin = p.estoqueMinimo;
    this.novoEst = p.estoqueAtual;
    this.novoMarca = '';
    this.novoDescricao = '';
    this.imagemPreview.set(null);
    this.ultimaLeituraPreview.set(null);
    this.codigoDuplicadoMsg.set(null);
    this.codigoSomenteLeitura = false;
    this.formularioAberto.set(true);
    this.cadastroStepper?.reset();
    queueMicrotask(() => {
      if (this.cadastroStepper) {
        this.cadastroStepper.selectedIndex = 0;
      }
      this.scrollFormulario();
    });
    this.validarCodigoEmTempoReal();
  }

  reload() {
    this.loading.set(true);
    this.http.get<Produto[]>(`${environment.apiUrl}/api/produtos`).subscribe({
      next: (p) => {
        this.produtos.set(p);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  salvarProduto() {
    if (!this.auth.isAdmin()) {
      this.snack.open('Somente administrador pode cadastrar ou editar.', 'OK', { duration: 2500 });
      return;
    }
    const codigoBarras = this.codigoBarrasResumo();
    const codigoQr = this.codigoQrResumo();
    if (codigoBarras && codigoQr && codigoBarras === codigoQr) {
      this.snack.open('Código de barras e QR não podem ser iguais.', 'OK', { duration: 3500 });
      return;
    }
    if (this.codigoDuplicadoMsg()) {
      this.snack.open(this.codigoDuplicadoMsg()!, 'OK', { duration: 3500 });
      return;
    }
    if (!this.podeAvancarPreco()) {
      this.snack.open('Informe preço de compra e preço de venda.', 'OK', { duration: 3000 });
      return;
    }
    const editando = this.produtoEmEdicao();
    const body = {
      id: editando?.id ?? null,
      nome: this.novoNome.trim(),
      codigoBarras: codigoBarras ?? null,
      codigoQr: codigoQr ?? null,
      codigoInterno: this.codigoInternoEdicao,
      categoria: this.novoCat,
      preco: this.toMoney(this.novoPreco),
      custo: this.toMoney(this.novoCusto),
      estoqueAtual: this.toInt(this.novoEst, 0),
      estoqueMinimo: this.toInt(this.novoMin, 0),
      ativo: editando?.ativo ?? true,
    };
    this.salvando.set(true);
    const req = editando
      ? this.http.put<Produto>(`${environment.apiUrl}/api/produtos/${editando.id}`, body)
      : this.http.post<Produto>(`${environment.apiUrl}/api/produtos`, body);
    req.subscribe({
      next: (p) => {
        this.salvando.set(false);
        const interno = p.codigoInterno;
        const msg = editando
          ? 'Produto atualizado'
          : interno && !codigoBarras && !codigoQr
            ? `Produto criado. Código interno: ${interno}`
            : 'Produto criado';
        this.snack.open(msg, 'OK', { duration: 3500 });
        this.cancelarCadastro();
        this.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.snack.open(e?.error?.erro ?? e?.message ?? 'Erro ao salvar produto', 'OK', { duration: 3500 });
      },
    });
  }

  desativar(p: Produto) {
    if (!this.auth.isAdmin()) return;
    this.fecharMenuProduto();
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          titulo: 'Desativar produto',
          mensagem: `Deseja desativar "${p.nome}"? Ele não aparecerá mais no PDV.`,
          confirmLabel: 'Desativar',
          confirmColor: 'warn',
        },
        width: '360px',
      })
      .afterClosed()
      .subscribe((ok) => {
        if (!ok) return;
        this.http.delete(`${environment.apiUrl}/api/produtos/${p.id}`).subscribe({
          next: () => {
            this.snack.open('Produto desativado', 'OK', { duration: 2000 });
            this.reload();
          },
          error: (e) => this.snack.open(e?.error?.erro ?? 'Erro', 'OK', { duration: 3500 }),
        });
      });
  }

  editarPrecos(p: Produto): void {
    if (!this.auth.isAdmin()) {
      return;
    }
    this.fecharMenuProduto();
    this.dialog
      .open(EditarPrecosDialogComponent, {
        data: p,
        width: '400px',
      })
      .afterClosed()
      .subscribe((result: { custo: number; preco: number } | undefined) => {
        if (!result) {
          return;
        }
        this.http
          .put<Produto>(`${environment.apiUrl}/api/produtos/${p.id}`, {
            ...p,
            preco: this.toMoney(result.preco),
            custo: this.toMoney(result.custo),
          })
          .subscribe({
            next: () => {
              this.snack.open('Preços atualizados', 'OK', { duration: 2000 });
              this.reload();
            },
            error: (e) => this.snack.open(e?.error?.erro ?? 'Erro ao salvar preços', 'OK', { duration: 3500 }),
          });
      });
  }
}

@Component({
  selector: 'app-editar-precos-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Preços — {{ data.nome }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Preço de compra</mat-label>
        <span matTextPrefix>R$&nbsp;</span>
        <input matInput type="number" [(ngModel)]="custo" name="custo" min="0" step="0.01" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Preço de venda</mat-label>
        <span matTextPrefix>R$&nbsp;</span>
        <input matInput type="number" [(ngModel)]="preco" name="preco" min="0.01" step="0.01" />
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" [mat-dialog-close]="undefined">Cancelar</button>
      <button mat-flat-button type="button" color="primary" [disabled]="!valido()" (click)="salvar()">
        Salvar
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .wide { width: 100%; display: block; }
    mat-dialog-content { padding-top: 8px; }
  `,
})
export class EditarPrecosDialogComponent {
  readonly data = inject<Produto>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<EditarPrecosDialogComponent>);
  custo = this.data.custo ?? 0;
  preco = this.data.preco;

  valido(): boolean {
    return Number(this.preco) > 0 && Number(this.custo) >= 0;
  }

  salvar(): void {
    if (!this.valido()) {
      return;
    }
    this.dialogRef.close({ custo: Number(this.custo), preco: Number(this.preco) });
  }
}
