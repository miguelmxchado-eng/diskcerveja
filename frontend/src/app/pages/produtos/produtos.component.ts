import { Component, OnDestroy, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
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

const MAX_IMAGEM_BYTES = 1024 * 1024;

@Component({
  selector: 'app-produtos',
  standalone: true,
  imports: [
    FormsModule,
    MatTableModule,
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

  displayedColumns = ['nome', 'codigo', 'categoria', 'compra', 'venda', 'lucro', 'estoque', 'ativo', 'acoes'];
  produtos = signal<Produto[]>([]);
  loading = signal(false);
  imagemPreview = signal<string | null>(null);

  filtroTabela = '';
  filtroSomenteComCodigo = false;
  ultimaLeituraPreview = signal<{ code: string; format: string } | null>(null);
  codigoDuplicadoMsg = signal<string | null>(null);
  travarCodigoAposScan = false;
  codigoSomenteLeitura = false;

  totalProdutos = computed(() => this.produtos().length);
  totalAtivos = computed(() => this.produtos().filter((p) => p.ativo).length);
  totalBaixoEstoque = computed(() =>
    this.produtos().filter((p) => p.ativo && p.estoqueAtual <= p.estoqueMinimo).length,
  );

  produtosFiltrados = computed(() => {
    let list = this.produtos();
    if (this.filtroSomenteComCodigo) {
      list = list.filter((p) => !!codigoPrincipal(p));
    }
    if (this.filtroTabela.trim()) {
      list = list.filter((p) => produtoCombinaBusca(p, this.filtroTabela));
    }
    return list;
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

  private scanSub?: Subscription;

  constructor(
    private readonly http: HttpClient,
    private readonly snack: MatSnackBar,
    private readonly dialog: MatDialog,
    private readonly scanner: BarcodeScannerService,
    private readonly codigoService: ProdutoCodigoService,
    private readonly feedback: BarcodeFeedbackService,
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
  }

  validarCodigoEmTempoReal(codigo?: string): void {
    const c = normalizeScannedCode(codigo ?? (this.novoCodigoBarras || this.novoCodigoQr));
    if (!c) {
      this.codigoDuplicadoMsg.set(null);
      return;
    }
    this.codigoService.validarCodigo(c).subscribe({
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

  salvarNovo() {
    if (!this.auth.isAdmin()) {
      this.snack.open('Somente administrador pode cadastrar.', 'OK', { duration: 2500 });
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
    const body = {
      id: null,
      nome: this.novoNome.trim(),
      codigoBarras: codigoBarras ?? null,
      codigoQr: codigoQr ?? null,
      codigoInterno: null,
      categoria: this.novoCat,
      preco: this.toMoney(this.novoPreco),
      custo: this.toMoney(this.novoCusto),
      estoqueAtual: this.toInt(this.novoEst, 0),
      estoqueMinimo: this.toInt(this.novoMin, 0),
      ativo: true,
    };
    this.http.post<Produto>(`${environment.apiUrl}/api/produtos`, body).subscribe({
      next: (p) => {
        const interno = p.codigoInterno;
        const msg = interno && !codigoBarras && !codigoQr
          ? `Produto criado. Código interno: ${interno}`
          : 'Produto criado';
        this.snack.open(msg, 'OK', { duration: 3500 });
        this.cancelarCadastro();
        this.reload();
      },
      error: (e) =>
        this.snack.open(e?.error?.erro ?? e?.message ?? 'Erro ao criar', 'OK', { duration: 3500 }),
    });
  }

  desativar(p: Produto) {
    if (!this.auth.isAdmin()) return;
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
