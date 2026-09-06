import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isLogin = req.url.includes('/api/auth/login');
  const isPublico = req.url.includes('/api/publico/');
  let token = auth.token() ?? localStorage.getItem('dcm_token');

  if (token && auth.isTokenExpired(token)) {
    if (!isLogin && !isPublico) {
      auth.logout();
    }
    token = null;
  }

  if (token) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (!isLogin && !isPublico && err.status === 401) {
        auth.logout();
      }
      if (!isLogin && !isPublico && err.status === 403 && token && auth.isTokenExpired(token)) {
        auth.logout();
      }
      return throwError(() => err);
    }),
  );
};
