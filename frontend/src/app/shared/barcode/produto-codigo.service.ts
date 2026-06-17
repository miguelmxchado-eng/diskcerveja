import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Produto } from '../../core/models';

export interface ValidacaoCodigoResponse {
  disponivel: boolean;
  produtoId?: number | null;
  produtoNome?: string | null;
}

@Injectable({ providedIn: 'root' })
export class ProdutoCodigoService {
  constructor(private readonly http: HttpClient) {}

  buscarPorCodigo(codigo: string): Observable<Produto> {
    return this.http.get<Produto>(
      `${environment.apiUrl}/api/produtos/codigo-barras/${encodeURIComponent(codigo.trim())}`,
    );
  }

  validarCodigo(codigo: string, excluirId?: number | null): Observable<ValidacaoCodigoResponse> {
    let url = `${environment.apiUrl}/api/produtos/validar-codigo?codigo=${encodeURIComponent(codigo.trim())}`;
    if (excluirId != null) {
      url += `&excluirId=${excluirId}`;
    }
    return this.http.get<ValidacaoCodigoResponse>(url);
  }
}
