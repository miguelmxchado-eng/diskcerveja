import { Injectable, computed, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

const TOKEN_KEY = 'dcm_cliente_token';
const PERFIL_KEY = 'dcm_cliente_perfil';

export interface ContaClientePerfil {
  token: string;
  clienteId: number;
  nome: string;
  telefone: string;
  cep?: string | null;
  logradouro?: string | null;
  numero?: string | null;
  complemento?: string | null;
  bairro?: string | null;
  cidade?: string | null;
  uf?: string | null;
}

@Injectable({ providedIn: 'root' })
export class ContaClienteService {
  private readonly perfilSig = signal<ContaClientePerfil | null>(this.readPerfil());

  readonly perfil = computed(() => this.perfilSig());
  readonly logado = computed(() => {
    const t = this.perfilSig()?.token;
    if (!t) return false;
    if (this.isTokenExpired(t)) return false;
    return true;
  });
  readonly nomeCurto = computed(() => {
    const n = this.perfilSig()?.nome?.trim();
    if (!n) return '';
    return n.split(/\s+/)[0];
  });

  constructor(private readonly http: HttpClient) {
    const p = this.perfilSig();
    if (p?.token && this.isTokenExpired(p.token)) {
      this.logout();
    }
  }

  token(): string | null {
    const t = this.perfilSig()?.token ?? localStorage.getItem(TOKEN_KEY);
    if (!t) return null;
    if (this.isTokenExpired(t)) {
      this.logout();
      return null;
    }
    return t;
  }

  /** Preferir este getter no template — já invalida sessão vencida. */
  estaLogado(): boolean {
    return !!this.token();
  }

  registrar(body: {
    nome: string;
    telefone: string;
    senha: string;
    pedidoId?: number | null;
    cep?: string | null;
    logradouro?: string | null;
    numero?: string | null;
    complemento?: string | null;
    bairro?: string | null;
    cidade?: string | null;
    uf?: string | null;
  }): Observable<ContaClientePerfil> {
    return this.http
      .post<ContaClientePerfil>(`${environment.apiUrl}/api/publico/conta/registrar`, body)
      .pipe(tap((r) => this.persistir(r)));
  }

  login(telefone: string, senha: string): Observable<ContaClientePerfil> {
    return this.http
      .post<ContaClientePerfil>(`${environment.apiUrl}/api/publico/conta/login`, { telefone, senha })
      .pipe(tap((r) => this.persistir(r)));
  }

  recarregarMe(): Observable<ContaClientePerfil> {
    return this.http
      .get<ContaClientePerfil>(`${environment.apiUrl}/api/publico/conta/me`, {
        headers: this.authHeaders(),
      })
      .pipe(tap((r) => this.persistir(r)));
  }

  atualizar(body: Partial<ContaClientePerfil>): Observable<ContaClientePerfil> {
    return this.http
      .put<ContaClientePerfil>(`${environment.apiUrl}/api/publico/conta/me`, body, {
        headers: this.authHeaders(),
      })
      .pipe(tap((r) => this.persistir(r)));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(PERFIL_KEY);
    this.perfilSig.set(null);
  }

  private authHeaders(): HttpHeaders {
    const t = this.token();
    return t ? new HttpHeaders({ Authorization: `Bearer ${t}` }) : new HttpHeaders();
  }

  private persistir(r: ContaClientePerfil): void {
    localStorage.setItem(TOKEN_KEY, r.token);
    localStorage.setItem(PERFIL_KEY, JSON.stringify(r));
    this.perfilSig.set(r);
  }

  private readPerfil(): ContaClientePerfil | null {
    try {
      const raw = localStorage.getItem(PERFIL_KEY);
      if (!raw) return null;
      return JSON.parse(raw) as ContaClientePerfil;
    } catch {
      return null;
    }
  }

  private isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      if (typeof payload.exp !== 'number') return true;
      return Date.now() >= payload.exp * 1000 - 5000;
    } catch {
      return true;
    }
  }
}
