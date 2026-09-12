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
  visivelCardapio?: boolean;
  promocaoCardapio?: boolean;
  descricaoCardapio?: string | null;
  imagemUrl?: string | null;
}

export interface PedidoItemResponse {
  produtoId: number | null;
  comboId?: number | null;
  produtoNome: string;
  quantidade: number;
  precoUnitario: number;
  custoUnitario?: number;
  observacao?: string | null;
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

export interface ComboOpcaoDto {
  id?: number | null;
  rotulo: string;
  produtoId?: number | null;
  ordem?: number;
  ativo?: boolean;
}

export interface ComboOpcaoGrupoDto {
  id?: number | null;
  nome: string;
  obrigatorio: boolean;
  minimo: number;
  maximo: number;
  ordem?: number;
  opcoes: ComboOpcaoDto[];
}

export interface ComboOpcaoResponse {
  id: number;
  rotulo: string;
  produtoId?: number | null;
  ordem: number;
  ativo: boolean;
}

export interface ComboOpcaoGrupoResponse {
  id: number;
  nome: string;
  obrigatorio: boolean;
  minimo: number;
  maximo: number;
  ordem: number;
  opcoes: ComboOpcaoResponse[];
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
  visivelCardapio?: boolean;
  promocaoCardapio?: boolean;
  configuravel?: boolean;
  custoTotal: number;
  lucro: number;
  margem: number;
  quantidadeVendida: number;
  faturamento: number;
  estoqueDisponivel: number;
  itens: ComboItemResponse[];
  gruposOpcao?: ComboOpcaoGrupoResponse[];
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
  visivelCardapio?: boolean;
  promocaoCardapio?: boolean;
  configuravel?: boolean;
  itens: ComboItemDto[];
  gruposOpcao?: ComboOpcaoGrupoDto[];
}

export interface LojaConfig {
  nome: string;
  whatsapp: string;
  aberta: boolean;
  horario: string;
  taxaEntrega: number;
  pedidoMinimo: number;
  info: string;
  infinitepayHandle?: string;
  publicBaseUrl?: string;
  pagamentoOnline?: boolean;
}

export interface ZonaEntrega {
  id?: number | null;
  nome: string;
  taxa: number;
  cepPrefixos: string;
  bairros?: string;
  ativo: boolean;
  ordem: number;
}

export interface FretePublico {
  coberta: boolean;
  taxa: number;
  zona?: string | null;
  pedidoMinimo: number;
  mensagem?: string | null;
}

export interface CatalogoOpcaoPublico {
  id: number;
  rotulo: string;
}

export interface CatalogoGrupoOpcaoPublico {
  id: number;
  nome: string;
  obrigatorio: boolean;
  minimo: number;
  maximo: number;
  opcoes: CatalogoOpcaoPublico[];
}

export interface CatalogoItemPublico {
  tipo: 'PRODUTO' | 'COMBO';
  id: number;
  nome: string;
  descricao?: string | null;
  imagemUrl?: string | null;
  categoria: string;
  preco: number;
  precoUnidade?: number | null;
  unidadesPorEmbalagem?: number | null;
  disponivel: boolean;
  promocao?: boolean;
  configuravel?: boolean;
  grupos?: CatalogoGrupoOpcaoPublico[] | null;
}

export interface CatalogoPublico {
  loja: LojaConfig;
  categorias: { codigo: string; nome: string; itens: CatalogoItemPublico[] }[];
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
  formaPagamento: 'PIX' | 'DINHEIRO' | 'CARTAO' | 'MISTO';
  enderecoEntrega?: string;
  estoqueBaixado: boolean;
  itens: PedidoItemResponse[];
  pagamentos?: PedidoPagamento[];
}

export interface PedidoPagamento {
  formaPagamento: 'PIX' | 'DINHEIRO' | 'CARTAO';
  valor: number;
  valorRecebido?: number | null;
  troco?: number;
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

export type PeriodoPedido = 'DIA' | 'SEMANA' | 'MES' | 'ANO' | 'PERSONALIZADO';

export interface PedidoResumoDto {
  id: number;
  dataHora: string;
  clienteNome?: string;
  telefone?: string;
  tipo: string;
  status: string;
  total: number;
  desconto?: number | null;
  custo?: number | null;
  lucro?: number | null;
  formaPagamento: string;
  registradoNoCaixa: boolean;
  itens?: PedidoItemResponse[];
  pagamentos?: PedidoPagamento[];
}

export interface PedidoPeriodoResponse {
  periodo: PeriodoPedido;
  periodoDescricao: string;
  dataInicio: string;
  dataFim: string;
  quantidadeDiasNoPeriodo: number;
  pedidos: PedidoResumoDto[];
  totalPedidos: number;
  quantidadePedidosPeriodo?: number;
  pagina: number;
  tamanhoPagina: number;
  totalPaginas: number;
  somaTotalPedidos: number;
  somaVendasEntregues: number;
  somaCustoEntregues: number;
  somaLucroEntregues: number;
  margemPercentual: number;
  quantidadeEntreguesSemCaixa: number;
  faturamentoDiario?: { rotulo: string; total: number }[];
  formasPagamento?: { forma: string; valor: number; percentual: number }[];
  topProdutos?: { nome: string; unidades: number; valor: number }[];
  pedidosPorHora?: { rotulo: string; hora: number; quantidade: number }[];
  mediaPedidosPorHora?: number;
  horasComPedido?: number;
  pedidosPeriodoAnterior?: number;
  vendasPeriodoAnterior?: number;
  lucroPeriodoAnterior?: number;
  margemPeriodoAnterior?: number;
}

export interface ProjecaoMensalResponse {
  diasDecorridos: number;
  diasRestantes: number;
  diasNoMes: number;
  progressoMes: number;
  faturamentoAtual: number;
  lucroAtual: number;
  pedidosAtual: number;
  mediaDiaria: number;
  mediaDiaUtil: number;
  mediaFimSemana: number;
  estimativaRestante: number;
  projetadoPessimista: number;
  projetadoRealista: number;
  projetadoOtimista: number;
  lucroProjetado: number;
  pedidosProjetados: number;
  metaMensal: number;
  metaOrigem: string;
  faltaParaMeta: number;
  faltaPorDia: number;
  noRitmoDaMeta: boolean;
  mesmoMesAnoPassado: number;
  mesmoPeriodoAnoPassado: number;
  mesAnterior: number;
}

export interface ProdutoVendaRank {
  produtoId: number;
  nome: string;
  unidades: number;
  valor: number;
  estoqueAtual: number;
  estoqueMinimo: number;
  estoqueBaixo: boolean;
}

export interface PdvInsightsResponse {
  diasBase: number;
  maisVendidos: ProdutoVendaRank[];
  alertaEstoqueQuente: ProdutoVendaRank[];
}

export interface ProdutoSugestao {
  produtoId: number;
  nome: string;
  preco: number;
  vezesJunto: number;
  estoqueAtual: number;
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
  total: number;
  formaPagamento?: string;
  pagamentoConfirmado?: boolean;
  statusEntrega: string;
  statusPedido: string;
  entregadorNome?: string;
}

export type TipoContaFinanceira = 'PAGAR' | 'RECEBER';
export type StatusContaFinanceira = 'ABERTA' | 'QUITADA' | 'CANCELADA';

export interface ContaFinanceiraDto {
  id?: number | null;
  tipo: TipoContaFinanceira;
  descricao: string;
  pessoa?: string | null;
  valor: number;
  vencimento: string;
  status?: StatusContaFinanceira | null;
  dataPagamento?: string | null;
  formaPagamento?: string | null;
  observacao?: string | null;
}
