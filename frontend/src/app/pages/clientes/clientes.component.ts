import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ClienteDto } from '../../core/models';
import { ClienteService } from '../../core/cliente.service';

@Component({
  selector: 'app-clientes',
  standalone: true,
  imports: [FormsModule, MatIconModule, MatSnackBarModule],
  templateUrl: './clientes.component.html',
  styleUrl: './clientes.component.scss',
})
export class ClientesComponent implements OnInit {
  readonly clientes = signal<ClienteDto[]>([]);
  readonly loading = signal(false);
  readonly salvando = signal(false);
  readonly formAberto = signal(false);
  readonly editandoId = signal<number | null>(null);

  busca = '';
  nome = '';
  telefone = '';
  endereco = '';
  observacao = '';

  constructor(
    private readonly clienteService: ClienteService,
    private readonly snack: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.loading.set(true);
    this.clienteService.listar(this.busca).subscribe({
      next: (list) => {
        this.clientes.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Não foi possível carregar os clientes.', 'OK', { duration: 3000 });
      },
    });
  }

  abrirNovo(): void {
    this.editandoId.set(null);
    this.nome = '';
    this.telefone = '';
    this.endereco = '';
    this.observacao = '';
    this.formAberto.set(true);
  }

  abrirEditar(c: ClienteDto): void {
    this.editandoId.set(c.id ?? null);
    this.nome = c.nome;
    this.telefone = c.telefone ?? '';
    this.endereco = c.endereco ?? '';
    this.observacao = c.observacao ?? '';
    this.formAberto.set(true);
  }

  fecharForm(): void {
    this.formAberto.set(false);
  }

  salvar(): void {
    const nome = this.nome.trim();
    if (!nome) {
      this.snack.open('Informe o nome do cliente.', 'OK', { duration: 2500 });
      return;
    }
    this.salvando.set(true);
    const dto: ClienteDto = {
      nome,
      telefone: this.telefone.trim() || null,
      endereco: this.endereco.trim() || null,
      observacao: this.observacao.trim() || null,
      ativo: true,
    };
    const id = this.editandoId();
    const req = id != null ? this.clienteService.atualizar(id, dto) : this.clienteService.criar(dto);
    req.subscribe({
      next: () => {
        this.salvando.set(false);
        this.formAberto.set(false);
        this.snack.open(id != null ? 'Cliente atualizado.' : 'Cliente cadastrado.', 'OK', { duration: 2000 });
        this.carregar();
      },
      error: (e) => {
        this.salvando.set(false);
        this.snack.open(e?.error?.detail ?? e?.error?.erro ?? 'Não foi possível salvar.', 'OK', {
          duration: 3500,
        });
      },
    });
  }
}
