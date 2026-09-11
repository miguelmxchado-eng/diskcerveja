import { Component, OnInit, computed, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ContaFinanceiraService } from '../../core/conta-financeira.service';
import {
  ContaFinanceiraDto,
  StatusContaFinanceira,
  TipoContaFinanceira,
} from '../../core/models';

@Component({
  selector: 'app-contas',
  standalone: true,
  imports: [FormsModule, MatIconModule, MatSnackBarModule, DecimalPipe],
  templateUrl: './contas.component.html',
  styleUrl: './contas.component.scss',
})
export class ContasComponent implements OnInit {
  readonly tipo = signal<TipoContaFinanceira>('PAGAR');
  readonly filtroStatus = signal<StatusContaFinanceira | 'TODAS'>('ABERTA');
  readonly contas = signal<ContaFinanceiraDto[]>([]);
  readonly loading = signal(false);
  readonly salvando = signal(false);
  readonly formAberto = signal(false);
  readonly editandoId = signal<number | null>(null);

  busca = '';
  descricao = '';
  pessoa = '';
  valor: number | null = null;
  vencimento = '';
  observacao = '';
  formaQuitar = 'PIX';

  readonly tituloAba = computed(() =>
    this.tipo() === 'PAGAR' ? 'Contas a pagar' : 'Contas a receber',
  );

  readonly totalAberto = computed(() =>
    this.contas()
      .filter((c) => c.status === 'ABERTA')
      .reduce((acc, c) => acc + Number(c.valor || 0), 0),
  );

  constructor(
    private readonly service: ContaFinanceiraService,
    private readonly snack: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  setTipo(tipo: TipoContaFinanceira): void {
    if (this.tipo() === tipo) return;
    this.tipo.set(tipo);
    this.formAberto.set(false);
    this.carregar();
  }

  setFiltroStatus(status: StatusContaFinanceira | 'TODAS'): void {
    this.filtroStatus.set(status);
    this.carregar();
  }

  carregar(): void {
    this.loading.set(true);
    const filtro = this.filtroStatus();
    const status: StatusContaFinanceira | null = filtro === 'TODAS' ? null : filtro;
    this.service.listar(this.tipo(), status, this.busca).subscribe({
      next: (list) => {
        this.contas.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Não foi possível carregar as contas.', 'OK', { duration: 3000 });
      },
    });
  }

  abrirNovo(): void {
    this.editandoId.set(null);
    this.descricao = '';
    this.pessoa = '';
    this.valor = null;
    this.vencimento = this.hojeIso();
    this.observacao = '';
    this.formAberto.set(true);
  }

  abrirEditar(c: ContaFinanceiraDto): void {
    if (c.status !== 'ABERTA') {
      this.snack.open('Só dá para editar conta em aberto.', 'OK', { duration: 2500 });
      return;
    }
    this.editandoId.set(c.id ?? null);
    this.descricao = c.descricao;
    this.pessoa = c.pessoa ?? '';
    this.valor = Number(c.valor);
    this.vencimento = (c.vencimento || '').slice(0, 10);
    this.observacao = c.observacao ?? '';
    this.formAberto.set(true);
  }

  fecharForm(): void {
    this.formAberto.set(false);
  }

  salvar(): void {
    const descricao = this.descricao.trim();
    if (!descricao) {
      this.snack.open('Informe o que é a conta (ex.: aluguel, fornecedor).', 'OK', {
        duration: 2800,
      });
      return;
    }
    if (this.valor == null || this.valor <= 0) {
      this.snack.open('Informe um valor maior que zero.', 'OK', { duration: 2500 });
      return;
    }
    if (!this.vencimento) {
      this.snack.open('Informe a data de vencimento.', 'OK', { duration: 2500 });
      return;
    }

    const dto: ContaFinanceiraDto = {
      tipo: this.tipo(),
      descricao,
      pessoa: this.pessoa.trim() || null,
      valor: this.valor,
      vencimento: this.vencimento,
      observacao: this.observacao.trim() || null,
    };

    this.salvando.set(true);
    const id = this.editandoId();
    const req$ = id != null ? this.service.atualizar(id, dto) : this.service.criar(dto);
    req$.subscribe({
      next: () => {
        this.salvando.set(false);
        this.formAberto.set(false);
        this.snack.open(id != null ? 'Conta atualizada.' : 'Conta lançada.', 'OK', {
          duration: 2200,
        });
        this.carregar();
      },
      error: (e) => {
        this.salvando.set(false);
        this.snack.open(e?.error?.erro ?? e?.error?.detail ?? 'Não foi possível salvar.', 'OK', {
          duration: 3500,
        });
      },
    });
  }

  quitar(c: ContaFinanceiraDto): void {
    if (!c.id || c.status !== 'ABERTA') return;
    const verbo = this.tipo() === 'PAGAR' ? 'marcar como paga' : 'marcar como recebida';
    if (!confirm(`Deseja ${verbo} "${c.descricao}"?`)) return;
    this.service
      .quitar(c.id, { formaPagamento: this.formaQuitar, dataPagamento: this.hojeIso() })
      .subscribe({
        next: () => {
          this.snack.open(this.tipo() === 'PAGAR' ? 'Conta paga.' : 'Conta recebida.', 'OK', {
            duration: 2200,
          });
          this.carregar();
        },
        error: (e) =>
          this.snack.open(e?.error?.erro ?? 'Não foi possível quitar.', 'OK', { duration: 3500 }),
      });
  }

  cancelar(c: ContaFinanceiraDto): void {
    if (!c.id || c.status !== 'ABERTA') return;
    if (!confirm(`Cancelar a conta "${c.descricao}"?`)) return;
    this.service.cancelar(c.id).subscribe({
      next: () => {
        this.snack.open('Conta cancelada.', 'OK', { duration: 2200 });
        this.carregar();
      },
      error: (e) =>
        this.snack.open(e?.error?.erro ?? 'Não foi possível cancelar.', 'OK', { duration: 3500 }),
    });
  }

  statusLabel(status?: string | null): string {
    if (status === 'QUITADA') return this.tipo() === 'PAGAR' ? 'Paga' : 'Recebida';
    if (status === 'CANCELADA') return 'Cancelada';
    return 'Em aberto';
  }

  vencida(c: ContaFinanceiraDto): boolean {
    if (c.status !== 'ABERTA' || !c.vencimento) return false;
    return c.vencimento.slice(0, 10) < this.hojeIso();
  }

  formatData(iso?: string | null): string {
    if (!iso) return '—';
    const d = iso.slice(0, 10);
    if (d.length !== 10) return iso;
    return `${d.slice(8, 10)}/${d.slice(5, 7)}/${d.slice(0, 4)}`;
  }

  private hojeIso(): string {
    const d = new Date();
    const m = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    return `${d.getFullYear()}-${m}-${day}`;
  }
}
