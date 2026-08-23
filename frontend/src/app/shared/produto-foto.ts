/** URLs de foto de produto (Unsplash) por família — alinhado ao PDV. */
const IMG = {
  cerveja:
    'https://images.unsplash.com/photo-1608270586620-248524c67de9?auto=format&fit=crop&w=200&q=80',
  cervejaPack:
    'https://images.unsplash.com/photo-1618885472179-5e474019f2a9?auto=format&fit=crop&w=200&q=80',
  destilado:
    'https://images.unsplash.com/photo-1569529465841-dfecdab7503b?auto=format&fit=crop&w=200&q=80',
  whisky:
    'https://images.unsplash.com/photo-1527281400683-1aae777175f8?auto=format&fit=crop&w=200&q=80',
  licor:
    'https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1a?auto=format&fit=crop&w=200&q=80',
  petisco:
    'https://images.unsplash.com/photo-1621939514649-280e2ee25f60?auto=format&fit=crop&w=200&q=80',
  refrigerante:
    'https://images.unsplash.com/photo-1622483767028-3f66f32aef97?auto=format&fit=crop&w=200&q=80',
  energetico:
    'https://images.unsplash.com/photo-1622543925227-4804d6c5da76?auto=format&fit=crop&w=200&q=80',
  generico:
    'https://images.unsplash.com/photo-1586995930424-bf275ccd02ad?auto=format&fit=crop&w=200&q=80',
} as const;

export function produtoFotoUrl(nome: string, categoria?: string | null): string {
  const n = (nome || '').toLowerCase();
  if (n.includes('whisky') || n.includes('whiskey') || n.includes('51')) return IMG.whisky;
  if (n.includes('amarula') || n.includes('licor')) return IMG.licor;
  if (n.includes('amstel') || n.includes('heineken') || n.includes('skol') || n.includes('brahma')) {
    return IMG.cervejaPack;
  }
  if (n.includes('bacon') || n.includes('trident') || n.includes('salg')) return IMG.petisco;
  switch (categoria) {
    case 'CERVEJAS':
      return IMG.cerveja;
    case 'DESTILADOS':
      return IMG.destilado;
    case 'PETISCOS':
      return IMG.petisco;
    case 'REFRIGERANTES':
      return IMG.refrigerante;
    case 'ENERGETICOS':
      return IMG.energetico;
    default:
      return IMG.generico;
  }
}
