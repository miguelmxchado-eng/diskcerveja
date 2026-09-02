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
  ) {
    // Se o token já estiver vencido ao abrir o app, limpa a sessão.
    if (this.tokenSig() && this.isTokenExpired(this.tokenSig()!)) {
      this.clearSession();
    }
  }

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

  /** Token presente e ainda válido (não expirado). */
  isAuthenticated(): boolean {
    const t = this.tokenSig();
    if (!t) return false;
    if (this.isTokenExpired(t)) {
      this.clearSession();
      return false;
    }
    return true;
  }

  isTokenExpired(token: string): boolean {
    const exp = this.readJwtExp(token);
    if (exp == null) return true;
    // 5s de folga para clock skew
    return Date.now() >= exp * 1000 - 5000;
  }

  logout() {
    this.clearSession();
    void this.router.navigateByUrl('/login');
  }

  private clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.tokenSig.set(null);
    this.userSig.set(null);
  }

  private readJwtExp(token: string): number | null {
    try {
      const parts = token.split('.');
      if (parts.length < 2) return null;
      const json = this.base64UrlDecode(parts[1]);
      const payload = JSON.parse(json) as { exp?: number };
      return typeof payload.exp === 'number' ? payload.exp : null;
    } catch {
      return null;
    }
  }

  private base64UrlDecode(segment: string): string {
    const padded = segment.replace(/-/g, '+').replace(/_/g, '/');
    const pad = padded.length % 4 === 0 ? '' : '='.repeat(4 - (padded.length % 4));
    return atob(padded + pad);
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
