import { GoalType, TaskPriority, TaskStatus } from '../core/operations-api.service';

export function taskPriorityLabel(value: TaskPriority): string {
  return { LOW: 'Baja', MEDIUM: 'Media', HIGH: 'Alta' }[value];
}

export function taskStatusLabel(value: TaskStatus): string {
  return {
    OPEN: 'Pendiente',
    IN_PROGRESS: 'En curso',
    COMPLETED: 'Completada',
    CANCELLED: 'Cancelada',
  }[value];
}

export function goalTypeLabel(value: GoalType): string {
  return {
    QUOTATIONS_ISSUED: 'Cotizaciones emitidas',
    QUOTATION_AMOUNT: 'Importe cotizado',
    TENDERS_FOLLOWED: 'Licitaciones seguidas',
    TASKS_COMPLETED: 'Tareas completadas',
  }[value];
}

export function percent(value: number): number {
  return Math.max(0, Math.min(100, Number(value) || 0));
}
