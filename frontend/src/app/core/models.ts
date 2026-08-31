export type Perfil = 'ADMIN' | 'OPERADOR' | 'ENTREGADOR';

export interface LoginResponse {
  token: string;
  nome: string;
  login: string;
  perfil: Perfil;
  usuarioId: number;
}

export interface Produto {
  id: number;
  nome: string;
  codigoBarras?: string | null;
  codigoQr?: string | null;
  codigoInterno?: string | null;
  categoria: string;
  preco: number;
  /** Preço da unidade avulsa (opcional). */
  precoUnidade?: number | null;
  /** Ex.: 6 para pack c/6. */
  unidadesPorEmbalagem?: number | null;
  /** Custo unitário (para lucro / estoque). */
  custo: number;
  /** Valor pago na caixa/pacote (como na NF), quando vende caixa e unidade. */
  custoEmbalagem?: number | null;
  estoqueAtual: number;
  estoqueMinimo: number;
  ativo: boolean;
}

export interface PedidoItemResponse {
  produtoId: number | null;
  comboId?: number | null;
  produtoNome: string;
  quantidade: number;
  precoUnitario: number;
  custoUnitario?: number;
}

export interface ComboItemDto {
  produtoId: number;
  quantidade: number;
}

export interface ComboItemResponse {
  produtoId: number;
  produtoNome: string;
  quantidade: number;
  custoUnitario: number;
  precoUnitario: number;
  estoqueDisponivel: number;
  produtoAtivo: boolean;
}

export interface ComboResponse {
  id: number;
  nome: string;
  codigo?: string | null;
  codigoBarras?: string | null;
  codigoQr?: string | null;
  categoria: string;
  descricao?: string | null;
  imagem?: string | null;
  precoVenda: number;
  ativo: boolean;
  custoTotal: number;
  lucro: number;
  margem: number;
  quantidadeVendida: number;
  faturamento: number;
  estoqueDisponivel: number;
  itens: ComboItemResponse[];
}

export interface ComboDto {
  id?: number | null;
  nome: string;
  codigoBarras?: string | null;
  codigoQr?: string | null;
  categoria: string;
  descricao?: string | null;
  imagem?: string | null;
  precoVenda: number;
  ativo: boolean;
  itens: ComboItemDto[];
}

export interface ComboRelatorio {
  comboId: number;
  nome: string;
  quantidadeVendida: number;
  faturamento: number;
  custoTotal: number;
  lucro: number;
  margem: number;
}

export interface PedidoResponse {
  id: number;
  dataHora: string;
  clienteNome?: string;
  telefone?: string;
  tipo: 'ENTREGA' | 'RETIRADA' | 'BALCAO';
  status: string;
  total: number;
  desconto?: number;
  formaPagamento: 'PIX' | 'DINHEIRO' | 'CARTAO';
  enderecoEntrega?: string;
  estoqueBaixado: boolean;
  itens: PedidoItemResponse[];
}

export interface PontoGraficoVendas {
  rotulo: string;
  rotuloCompleto: string;
  vendas: number;
  cancelamentos: number;
}

export interface DashboardResponse {
  vendasHoje: number;
  vendasOntem: number;
  cancelamentosHoje: number;
  pedidosEmAndamento: number;
  pedidosAtrasados: number;
  produtosBaixoEstoque: number;
  caixa: {
    caixaAberto: boolean;
    valorAbertura: number;
    saldoPrevisto: number;
    vendasPorFormaPagamento: Record<string, number>;
  };
  graficoDiario: PontoGraficoVendas[];
  graficoSemanal: PontoGraficoVendas[];
  graficoMensal: PontoGraficoVendas[];
}

export type PeriodoPedido = 'DIA' | 'SEMANA' | 'MES' | 'ANO';

export interface PedidoResumoDto {
  id: number;
  dataHora: string;
  clienteNome?: string;
  telefone?: string;
  tipo: string;
  status: string;
  total: number;
  custo?: number | null;
  lucro?: number | null;
  formaPagamento: string;
  registradoNoCaixa: boolean;
}

export interface PedidoPeriodoResponse {
  periodo: PeriodoPedido;
  periodoDescricao: string;
  dataInicio: string;
  dataFim: string;
  quantidadeDiasNoPeriodo: number;
  pedidos: PedidoResumoDto[];
  somaTotalPedidos: number;
  somaVendasEntregues: number;
  somaCustoEntregues: number;
  somaLucroEntregues: number;
  margemPercentual: number;
  quantidadeEntreguesSemCaixa: number;
}

export interface UsuarioDto {
  id?: number;
  nome: string;
  login: string;
  senha?: string | null;
  perfil: Perfil;
  ativo: boolean;
}

export interface ClienteDto {
  id?: number;
  nome: string;
  telefone?: string | null;
  endereco?: string | null;
  observacao?: string | null;
  ativo?: boolean;
}

export interface ConfigCaixaResponse {
  caixaObrigatorio: boolean;
}

export interface EntregaResumo {
  /** Ausente quando o pedido é ENTREGA mas ainda não há linha em `entrega` (legado ou inconsistência). */
  entregaId?: number | null;
  pedidoId: number;
  clienteNome?: string;
  telefone?: string;
  enderecoEntrega?: string;
  taxaEntrega: number;
  statusEntrega: string;
  statusPedido: string;
  entregadorNome?: string;
}
