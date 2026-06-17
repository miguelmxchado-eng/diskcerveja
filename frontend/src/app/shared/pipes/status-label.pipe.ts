import { Pipe, PipeTransform } from '@angular/core';

const LABELS: Record<string, string> = {
  // Status pedido
  ABERTO:        'Aberto',
  EM_PREPARO:    'Em preparo',
  SAIU_ENTREGA:  'Saiu p/ entrega',
  ENTREGUE:      'Entregue',
  CANCELADO:     'Cancelado',
  // Tipo pedido
  BALCAO:        'Balcão',
  RETIRADA:      'Retirada',
  ENTREGA:       'Entrega',
  // Pagamento
  PIX:           'PIX',
  DINHEIRO:      'Dinheiro',
  CARTAO:        'Cartão',
  // Movimentos estoque/caixa
  ENTRADA:       'Entrada',
  SAIDA:         'Saída',
  AJUSTE:        'Ajuste',
  BAIXA_PEDIDO:  'Baixa (pedido)',
  VENDA:         'Venda',
  SAIDA_TROCO:   'Troco',
  SAIDA_DESPESA: 'Despesa',
  // Status entrega
  PENDENTE:      'Pendente',
  EM_ROTA:       'Em rota',
  // Perfis
  ADMIN:         'Administrador',
  OPERADOR:      'Operador',
  ENTREGADOR:    'Entregador',
  // Categorias
  CERVEJAS:      'Cervejas',
  DESTILADOS:    'Destilados',
  REFRIGERANTES: 'Refrigerantes',
  ENERGETICOS:   'Energéticos',
  PETISCOS:      'Petiscos',
  COMBOS:        'Combos',
};

@Pipe({ name: 'statusLabel', standalone: true, pure: true })
export class StatusLabelPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) return '—';
    return LABELS[value] ?? value.replace(/_/g, ' ').toLowerCase().replace(/^\w/, c => c.toUpperCase());
  }
}
