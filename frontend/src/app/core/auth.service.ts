import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { environment } from '../../environments/environment';
import { LoginResponse, Perfil } from './models';

const TOKEN_KEY = 'dcm_token';
const USER_KEY = 'dcm_user';

export interface StoredUser {
  nome: string;
  login: string;
  perfil: Perfil;
  usuarioId: number;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenSig = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  private readonly userSig = signal<StoredUser | null>(this.readUser());

  readonly token = computed(() => this.tokenSig());
  readonly user = computed(() => this.userSig());
  readonly isAdmin = computed(() => this.userSig()?.perfil === 'ADMIN');

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {}

  login(login: string, senha: string) {
    return this.http.post<LoginResponse>(`${environment.apiUrl}/api/auth/login`, { login, senha });
  }

  persistSession(resp: LoginResponse) {
    localStorage.setItem(TOKEN_KEY, resp.token);
    localStorage.setItem(
      USER_KEY,
      JSON.stringify({
        nome: resp.nome,
        login: resp.login,
        perfil: resp.perfil,
        usuarioId: resp.usuarioId,
      } satisfies StoredUser),
    );
    this.tokenSig.set(resp.token);
    this.userSig.set(JSON.parse(localStorage.getItem(USER_KEY)!));
  }

  logout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.tokenSig.set(null);
    this.userSig.set(null);
    void this.router.navigateByUrl('/login');
  }

  private readUser(): StoredUser | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as StoredUser;
    } catch {
      return null;
    }
  }
}
