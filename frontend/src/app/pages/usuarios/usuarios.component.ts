import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { UsuarioDto } from '../../core/models';

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './usuarios.component.html',
})
export class UsuariosComponent implements OnInit {
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);
  private api = `${environment.apiUrl}/api/usuarios`;

  usuarios = signal<UsuarioDto[]>([]);
  loading = signal(false);
  salvando = signal(false);
  erro = signal<string | null>(null);
  sucesso = signal<string | null>(null);
  editando = signal<UsuarioDto | null>(null);
  modalAberto = signal(false);

  readonly perfis = ['ADMIN', 'OPERADOR', 'ENTREGADOR'] as const;

  form = this.fb.group({
    nome: ['', [Validators.required, Validators.minLength(3)]],
    login: ['', [Validators.required, Validators.minLength(3)]],
    senha: [''],
    perfil: ['OPERADOR', Validators.required],
    ativo: [true],
  });

  ngOnInit() {
    this.carregar();
  }

  carregar() {
    this.loading.set(true);
    this.http.get<UsuarioDto[]>(this.api).subscribe({
      next: (lista) => { this.usuarios.set(lista); this.loading.set(false); },
      error: () => { this.erro.set('Erro ao carregar usuários.'); this.loading.set(false); },
    });
  }

  abrirNovo() {
    this.editando.set(null);
    this.form.reset({ nome: '', login: '', senha: '', perfil: 'OPERADOR', ativo: true });
    this.form.get('senha')?.setValidators([Validators.required, Validators.minLength(6)]);
    this.form.get('senha')?.updateValueAndValidity();
    this.erro.set(null);
    this.sucesso.set(null);
    this.modalAberto.set(true);
  }

  abrirEdicao(u: UsuarioDto) {
    this.editando.set(u);
    this.form.reset({ nome: u.nome, login: u.login, senha: '', perfil: u.perfil, ativo: u.ativo });
    this.form.get('senha')?.clearValidators();
    this.form.get('senha')?.updateValueAndValidity();
    this.erro.set(null);
    this.sucesso.set(null);
    this.modalAberto.set(true);
  }

  fecharModal() {
    this.modalAberto.set(false);
  }

  salvar() {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.salvando.set(true);
    this.erro.set(null);

    const val = this.form.value;
    const payload: UsuarioDto = {
      nome: val.nome!,
      login: val.login!,
      senha: val.senha || null,
      perfil: val.perfil as UsuarioDto['perfil'],
      ativo: val.ativo!,
    };

    const editando = this.editando();
    const req$ = editando
      ? this.http.put<UsuarioDto>(`${this.api}/${editando.id}`, payload)
      : this.http.post<UsuarioDto>(this.api, payload);

    req$.subscribe({
      next: () => {
        this.sucesso.set(editando ? 'Usuário atualizado!' : 'Usuário criado!');
        this.salvando.set(false);
        this.modalAberto.set(false);
        this.carregar();
      },
      error: (err) => {
        this.erro.set(err?.error?.message ?? 'Erro ao salvar usuário.');
        this.salvando.set(false);
      },
    });
  }

  toggleAtivo(u: UsuarioDto) {
    const payload: UsuarioDto = { ...u, ativo: !u.ativo, senha: null };
    this.http.put<UsuarioDto>(`${this.api}/${u.id}`, payload).subscribe({
      next: () => this.carregar(),
      error: () => this.erro.set('Erro ao alterar status do usuário.'),
    });
  }

  campo(name: string) {
    return this.form.get(name);
  }
}
