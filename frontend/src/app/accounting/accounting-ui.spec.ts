import {
  documentRoleLabel,
  formatBytes,
  invoiceTypeLabel,
  statusLabel,
  toIsoOrNull,
} from './accounting-ui';

describe('accounting UI helpers', () => {
  it('translates closed states and document roles for the interface', () => {
    expect(statusLabel('IN_PROGRESS')).toBe('En proceso');
    expect(statusLabel('SIGNED')).toBe('Firmada');
    expect(documentRoleLabel('SIGNED_ACT')).toBe('Acta firmada');
    expect(invoiceTypeLabel('RECEIPT')).toBe('Boleta');
  });

  it('formats upload sizes without exposing implementation details', () => {
    expect(formatBytes(900)).toBe('900 B');
    expect(formatBytes(2048)).toBe('2.0 KB');
  });

  it('normalizes an optional local date-time to an ISO instant', () => {
    expect(toIsoOrNull('')).toBeNull();
    // La hora local se convierte al instante UTC equivalente; el esperado se
    // calcula con la zona de la máquina para que la prueba no dependa de ella.
    expect(toIsoOrNull('2026-09-15T10:30')).toBe(new Date(2026, 8, 15, 10, 30).toISOString());
    expect(toIsoOrNull('no-es-fecha')).toBeNull();
  });
});
