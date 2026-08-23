import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { MainLayoutComponent } from './layout/main-layout.component';
import { LoginComponent } from './pages/login/login.component';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { PdvComponent } from './pages/pdv/pdv.component';
import { ProdutosComponent } from './pages/produtos/produtos.component';
import { EstoqueComponent } from './pages/estoque/estoque.component';
import { CaixaComponent } from './pages/caixa/caixa.component';
import { EntregasComponent } from './pages/entregas/entregas.component';
import { RelatorioPedidosComponent } from './pages/relatorio-pedidos/relatorio-pedidos.component';
import { ClientesComponent } from './pages/clientes/clientes.component';
import { UsuariosComponent } from './pages/usuarios/usuarios.component';
import { ConfigComponent } from './pages/config/config.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: DashboardComponent },
      { path: 'pdv', component: PdvComponent },
      { path: 'relatorio-pedidos', component: RelatorioPedidosComponent },
      { path: 'clientes', component: ClientesComponent },
      { path: 'produtos', component: ProdutosComponent },
      { path: 'estoque', component: EstoqueComponent },
      { path: 'caixa', component: CaixaComponent },
      { path: 'entregas', component: EntregasComponent },
      { path: 'usuarios', component: UsuariosComponent },
      { path: 'config', component: ConfigComponent },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
