import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { AnotacaoService } from '../../core/anotacao.service';
import { AnotacaoDto, CorAnotacao } from '../../core/models';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../shared/confirm-dialog/confirm-dialog.component';

interface Rascunho {
  titulo: string;
  texto: string;
  cor: CorAnotacao;
}

@Component({
  selector: 'app-anotacoes',
  standalone: true,
  imports: [FormsModule, MatIconModule, MatDialogModule, MatSnackBarModule, DatePipe],
  templateUrl: './anotacoes.component.html',
  styleUrl: './anotacoes.component.scss',
})
export class AnotacoesComponent implements OnInit, OnDestroy {
  readonly cores: { id: CorAnotacao; nome: string }[] = [
    { id: 'AMARELO', nome: 'Amarelo' },
    { id: 'VERDE', nome: 'Verde' },
    { id: 'AZUL', nome: 'Azul' },
    { id: 'ROSA', nome: 'Rosa' },
    { id: 'BRANCO', nome: 'Branco' },
  ];

  readonly aba = signal<'abertas' | 'concluidas'>('abertas');
  readonly lista = signal<AnotacaoDto[]>([]);
  readonly contagem = signal({ abertas: 0, concluidas: 0 });
  readonly carregando = signal(true);
  readonly salvando = signal(false);
  readonly editandoId = signal<number | null>(null);

  busca = '';
  nova: Rascunho = { titulo: '', texto: '', cor: 'AMARELO' };
  edicao: Rascunho = { titulo: '', texto: '', cor: 'AMARELO' };

  private readonly busca$ = new Subject<string>();
  private buscaSub?: Subscription;
  private loadSub?: Subscription;

  constructor(
    private readonly service: AnotacaoService,
    private readonly dialog: MatDialog,
    private readonly snack: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.buscaSub = this.busca$.pipe(debounceTime(250), distinctUntilChanged()).subscribe(() => this.carregar());
    this.carregar();
  }

  ngOnDestroy(): void {
    this.buscaSub?.unsubscribe();
    this.loadSub?.unsubscribe();
  }

  onBusca(v: string): void {
    this.busca = v;
    this.busca$.next(v);
  }

  trocarAba(a: 'abertas' | 'concluidas'): void {
    if (this.aba() === a) return;
    this.aba.set(a);
    this.editandoId.set(null);
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.loadSub?.unsubscribe();
    this.loadSub = this.service.listar(this.aba() === 'concluidas', this.busca).subscribe({
      next: (l) => {
        this.lista.set(l);
        this.carregando.set(false);
      },
      error: (e) => {
        this.carregando.set(false);
        this.erro(e, 'Não foi possível carregar as anotações.');
      },
    });
    this.service.contagem().subscribe({ next: (c) => this.contagem.set(c), error: () => {} });
  }

  salvarNova(): void {
    if (!this.nova.texto.trim()) {
      this.snack.open('Escreva alguma coisa na anotação.', 'OK', { duration: 2200 });
      return;
    }
    this.salvando.set(true);
    this.service
      .criar({ titulo: this.nova.titulo, texto: this.nova.texto, cor: this.nova.cor })
      .subscribe({
        next: () => {
          this.salvando.set(false);
          this.nova = { titulo: '', texto: '', cor: this.nova.cor };
          this.snack.open('Anotação salva.', 'OK', { duration: 1800 });
          if (this.aba() !== 'abertas') {
            this.aba.set('abertas');
          }
          this.carregar();
        },
        error: (e) => {
          this.salvando.set(false);
          this.erro(e, 'Não foi possível salvar.');
        },
      });
  }

  onAtalhoSalvar(ev: KeyboardEvent, acao: () => void): void {
    if ((ev.ctrlKey || ev.metaKey) && ev.key === 'Enter') {
      ev.preventDefault();
      acao();
    }
  }

  editar(a: AnotacaoDto): void {
    this.edicao = { titulo: a.titulo ?? '', texto: a.texto, cor: (a.cor as CorAnotacao) ?? 'AMARELO' };
    this.editandoId.set(a.id ?? null);
  }

  cancelarEdicao(): void {
    this.editandoId.set(null);
  }

  salvarEdicao(a: AnotacaoDto): void {
    if (!this.edicao.texto.trim()) {
      this.snack.open('A anotação não pode ficar vazia.', 'OK', { duration: 2200 });
      return;
    }
    this.atualizar(a, { titulo: this.edicao.titulo, texto: this.edicao.texto, cor: this.edicao.cor }, 'Anotação atualizada.');
    this.editandoId.set(null);
  }

  alternarFixada(a: AnotacaoDto): void {
    this.atualizar(a, { fixada: !a.fixada });
  }

  alternarConcluida(a: AnotacaoDto): void {
    this.atualizar(a, { concluida: !a.concluida }, a.concluida ? 'Anotação reaberta.' : 'Marcada como concluída.');
  }

  excluir(a: AnotacaoDto): void {
    const data: ConfirmDialogData = {
      titulo: 'Excluir anotação',
      mensagem: 'Deseja excluir esta anotação? Não dá para desfazer.',
      confirmLabel: 'Excluir',
      confirmColor: 'warn',
    };
    this.dialog
      .open(ConfirmDialogComponent, { data, width: '420px' })
      .afterClosed()
      .subscribe((ok) => {
        if (!ok || a.id == null) return;
        this.service.excluir(a.id).subscribe({
          next: () => {
            this.snack.open('Anotação excluída.', 'OK', { duration: 1800 });
            this.carregar();
          },
          error: (e) => this.erro(e, 'Não foi possível excluir.'),
        });
      });
  }

  editadaDepois(a: AnotacaoDto): boolean {
    if (!a.criadoEm || !a.atualizadoEm) return false;
    return new Date(a.atualizadoEm).getTime() - new Date(a.criadoEm).getTime() > 60_000;
  }

  private atualizar(a: AnotacaoDto, mudanca: Partial<AnotacaoDto>, msg?: string): void {
    if (a.id == null) return;
    const dto: AnotacaoDto = {
      titulo: a.titulo,
      texto: a.texto,
      cor: a.cor,
      fixada: a.fixada,
      concluida: a.concluida,
      ...mudanca,
    };
    this.service.atualizar(a.id, dto).subscribe({
      next: () => {
        if (msg) this.snack.open(msg, 'OK', { duration: 1800 });
        this.carregar();
      },
      error: (e) => this.erro(e, 'Não foi possível atualizar.'),
    });
  }

  private erro(e: unknown, padrao: string): void {
    const msg = (e as { error?: { erro?: string } })?.error?.erro || padrao;
    this.snack.open(msg, 'OK', { duration: 3000 });
  }
}
