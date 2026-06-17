import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ComboDto, ComboRelatorio, ComboResponse } from './models';

@Injectable({ providedIn: 'root' })
export class ComboService {
  private readonly base = `${environment.apiUrl}/api/combos`;

  constructor(private readonly http: HttpClient) {}

  listar(somenteAtivos = false): Observable<ComboResponse[]> {
    return this.http.get<ComboResponse[]>(`${this.base}?somenteAtivos=${somenteAtivos}`);
  }

  listarAtivos(): Observable<ComboResponse[]> {
    return this.http.get<ComboResponse[]>(`${this.base}/ativos`);
  }

  buscar(id: number): Observable<ComboResponse> {
    return this.http.get<ComboResponse>(`${this.base}/${id}`);
  }

  relatorio(): Observable<ComboRelatorio[]> {
    return this.http.get<ComboRelatorio[]>(`${this.base}/relatorio`);
  }

  criar(dto: ComboDto): Observable<ComboResponse> {
    return this.http.post<ComboResponse>(this.base, dto);
  }

  atualizar(id: number, dto: ComboDto): Observable<ComboResponse> {
    return this.http.put<ComboResponse>(`${this.base}/${id}`, dto);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
