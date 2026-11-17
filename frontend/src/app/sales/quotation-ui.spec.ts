import {
  addBusinessDays,
  allowsOcam,
  businessDaysBetween,
  calculateQuotationLinePreview,
  calculateQuotationPreview,
  competitorGapPercent,
  marginPercent,
  outcomeRequiresReason,
  parseIsoDate,
  roundCurrency,
  toIsoDate,
  usesBusinessDayDeadline,
} from './quotation-ui';

describe('quotation UI calculations', () => {
  it('rounds displayed currency values to two decimals', () => {
    expect(roundCurrency(12.345)).toBe(12.35);
  });

  it('calculates tax on top of the unit price when tax is excluded', () => {
    expect(calculateQuotationLinePreview(2, 10, false, 0.18)).toEqual({
      subtotal: 20,
      tax: 3.6,
      total: 23.6,
    });
  });

  it('extracts the tax portion when the unit price includes tax', () => {
    expect(calculateQuotationLinePreview(1, 118, true, 0.18)).toEqual({
      subtotal: 100,
      tax: 18,
      total: 118,
    });
  });

  it('sums rounded lines and exposes the backend-authoritative warning boundary', () => {
    expect(
      calculateQuotationPreview(
        [
          { quantity: 1, unitPrice: 10.005 },
          { quantity: 2, unitPrice: 5 },
        ],
        false,
        0.18,
      ),
    ).toMatchObject({ subtotal: 20.01, tax: 3.6, total: 23.61 });
  });
});

describe('plazos en días hábiles (Perú Compras)', () => {
  // 2026-09-17 jueves · 18 viernes · 19 sábado · 20 domingo · 21 lunes
  const jueves = new Date(2026, 8, 17);
  const viernes = new Date(2026, 8, 18);

  it('suma dentro de la misma semana', () => {
    expect(toIsoDate(addBusinessDays(jueves, 1))).toBe('2026-09-18');
  });

  it('salta el fin de semana en vez de contarlo', () => {
    expect(toIsoDate(addBusinessDays(jueves, 2))).toBe('2026-09-21');
    expect(toIsoDate(addBusinessDays(jueves, 3))).toBe('2026-09-22');
    expect(toIsoDate(addBusinessDays(viernes, 1))).toBe('2026-09-21');
  });

  it('cuenta los días hábiles entre dos fechas', () => {
    expect(businessDaysBetween('2026-09-17', '2026-09-21')).toBe(2);
    expect(businessDaysBetween('2026-09-17', '2026-09-17')).toBe(0);
    expect(businessDaysBetween('2026-09-21', '2026-09-17')).toBe(0);
  });

  it('interpreta yyyy-MM-dd como fecha local, sin corrimiento de un día', () => {
    const parsed = parseIsoDate('2026-09-17');
    expect(parsed?.getDate()).toBe(17);
    expect(parsed?.getMonth()).toBe(8);
  });

  it('sabe qué procedimientos usan días hábiles y cuáles admiten OCAM', () => {
    expect(usesBusinessDayDeadline('PERU_COMPRAS')).toBe(true);
    expect(usesBusinessDayDeadline('DIRECTA')).toBe(false);
    expect(allowsOcam('PRIVADA')).toBe(false);
    expect(allowsOcam('PERU_COMPRAS')).toBe(true);
  });
});

describe('resultado del proceso', () => {
  it('exige motivo en todo cierre negativo', () => {
    expect(outcomeRequiresReason('NO_SELECCIONADA')).toBe(true);
    expect(outcomeRequiresReason('DESIERTA')).toBe(true);
    expect(outcomeRequiresReason('RECHAZADA')).toBe(true);
    expect(outcomeRequiresReason('ANULADA')).toBe(true);
  });

  it('no pide motivo cuando ganamos o seguimos esperando', () => {
    expect(outcomeRequiresReason('SELECCIONADA')).toBe(false);
    expect(outcomeRequiresReason('PENDIENTE')).toBe(false);
  });
});

describe('margen y competencia', () => {
  it('calcula el margen sobre el costo de compra', () => {
    expect(marginPercent(100, 72)).toBeCloseTo(28, 5);
  });

  it('devuelve null cuando no hay costo cargado, en vez de fingir 0 %', () => {
    expect(marginPercent(100, null)).toBeNull();
    expect(marginPercent(100, 0)).toBeNull();
  });

  it('marca en negativo cuando estamos por debajo de la competencia', () => {
    expect(competitorGapPercent(90, 100)).toBeCloseTo(-10, 5);
    expect(competitorGapPercent(110, 100)).toBeCloseTo(10, 5);
    expect(competitorGapPercent(100, null)).toBeNull();
  });
});
