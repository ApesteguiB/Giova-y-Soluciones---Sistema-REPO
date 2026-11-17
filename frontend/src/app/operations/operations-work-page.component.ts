import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import {
  AuditView,
  GoalInput,
  GoalType,
  GoalView,
  OperationsApiService,
  TaskAssignee,
  TaskInput,
  TaskPriority,
  TaskStatus,
  TaskView,
} from '../core/operations-api.service';
import { goalTypeLabel, percent, taskPriorityLabel, taskStatusLabel } from './operations-ui';

export type OperationsSection = 'tasks' | 'goals' | 'audit' | 'reports';

interface SectionLink {
  key: OperationsSection;
  label: string;
  path: string;
}

@Component({
  selector: 'app-operations-work-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule, ReactiveFormsModule, RouterLink],
  templateUrl: './operations-work-page.component.html',
  styleUrls: ['../admin/admin-resource-page.css', './operations-work-page.component.css'],
})
export class OperationsWorkPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(OperationsApiService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly section = signal<OperationsSection>('tasks');
  readonly role = computed(() => this.auth.session()?.roleCode ?? null);
  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  readonly canReports = computed(() => this.role() === 'ADMIN' || this.role() === 'ACCOUNTANT');
  readonly links: readonly SectionLink[] = [
    { key: 'tasks', label: 'Tareas', path: '/operaciones/trabajo/tareas' },
    { key: 'goals', label: 'Metas', path: '/operaciones/trabajo/metas' },
    { key: 'audit', label: 'Auditoría', path: '/operaciones/trabajo/auditoria' },
    { key: 'reports', label: 'Reportes', path: '/operaciones/trabajo/reportes' },
  ];

  readonly tasks = signal<TaskView[]>([]);
  readonly goals = signal<GoalView[]>([]);
  readonly auditEvents = signal<AuditView[]>([]);
  readonly assignees = signal<TaskAssignee[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly formOpen = signal(false);
  readonly editingTask = signal<TaskView | null>(null);
  readonly editingGoal = signal<GoalView | null>(null);
  readonly busyAction = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalItems = signal(0);
  readonly totalPages = signal(0);
  readonly pageSize = 20;

  query = '';
  statusFilter: 'ALL' | TaskStatus = 'ALL';
  goalActiveFilter: 'ALL' | 'true' | 'false' = 'ALL';
  auditResult: 'ALL' | 'SUCCESS' | 'FAILURE' = 'ALL';
  reportFrom = '';
  reportTo = '';
  reportStatus = '';
  reportCurrency = '';

  readonly taskForm = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(180)]],
    description: ['', Validators.maxLength(1500)],
    assignedToWorkerId: [0, Validators.min(1)],
    relatedEntityType: ['', Validators.maxLength(60)],
    relatedEntityId: ['', Validators.maxLength(80)],
    priority: ['MEDIUM' as TaskPriority, Validators.required],
    dueAt: [''],
  });

  readonly goalForm = this.fb.nonNullable.group({
    workerId: [0, [Validators.required, Validators.min(1)]],
    goalType: ['QUOTATIONS_ISSUED' as GoalType, Validators.required],
    periodStart: ['', Validators.required],
    periodEnd: ['', Validators.required],
    targetValue: [1, [Validators.required, Validators.min(0.0001)]],
    notes: ['', Validators.maxLength(1000)],
    active: [true],
  });

  ngOnInit(): void {
    const configured = this.route.snapshot.data['operationsSection'] as
      OperationsSection | undefined;
    this.section.set(configured ?? 'tasks');
    this.resetPeriodDefaults();
    if (this.isAdmin()) {
      this.api.taskAssignees().subscribe({ next: (items) => this.assignees.set(items) });
    }
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    if (this.section() === 'tasks') this.loadTasks();
    else if (this.section() === 'goals') this.loadGoals();
    else if (this.section() === 'audit') this.loadAudit();
    else this.loading.set(false);
  }

  search(): void {
    this.page.set(0);
    this.load();
  }

  previousPage(): void {
    if (this.page() === 0) return;
    this.page.update((value) => value - 1);
    this.load();
  }

  nextPage(): void {
    if (this.page() + 1 >= this.totalPages()) return;
    this.page.update((value) => value + 1);
    this.load();
  }

  openTask(task?: TaskView): void {
    this.notice.set('');
    this.error.set('');
    this.editingTask.set(task ?? null);
    this.editingGoal.set(null);
    this.taskForm.reset(
      task
        ? {
            title: task.title,
            description: task.description ?? '',
            assignedToWorkerId: task.assignedToWorkerId,
            relatedEntityType: task.relatedEntityType ?? '',
            relatedEntityId: task.relatedEntityId ?? '',
            priority: task.priority,
            dueAt: this.toDateTimeLocal(task.dueAt),
          }
        : {
            title: '',
            description: '',
            assignedToWorkerId: this.auth.session()?.id ?? 0,
            relatedEntityType: '',
            relatedEntityId: '',
            priority: 'MEDIUM',
            dueAt: '',
          },
    );
    this.formOpen.set(true);
  }

  closeForm(): void {
    if (this.saving()) return;
    this.formOpen.set(false);
    this.editingTask.set(null);
    this.editingGoal.set(null);
  }

  submitTask(): void {
    if (this.taskForm.invalid) {
      this.taskForm.markAllAsTouched();
      return;
    }
    const value = this.taskForm.getRawValue();
    const input: TaskInput = {
      title: value.title,
      description: value.description,
      assignedToWorkerId: value.assignedToWorkerId || null,
      relatedEntityType: value.relatedEntityType,
      relatedEntityId: value.relatedEntityId,
      priority: value.priority,
      dueAt: this.toIso(value.dueAt),
    };
    this.saving.set(true);
    const task = this.editingTask();
    const request = task
      ? this.api.updateTask(task.publicId, { ...input, version: task.version })
      : this.api.createTask(input);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.notice.set(task ? 'Tarea actualizada.' : 'Tarea creada.');
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(this.apiError(error, 'No se pudo guardar la tarea.'));
      },
    });
  }

  changeTaskStatus(task: TaskView, targetStatus: TaskStatus): void {
    if (this.busyAction()) return;
    const label = taskStatusLabel(targetStatus).toLowerCase();
    if (!window.confirm(`¿Cambiar la tarea a ${label}?`)) return;
    this.busyAction.set(task.publicId);
    this.api.changeTaskStatus(task.publicId, targetStatus, task.version).subscribe({
      next: () => {
        this.busyAction.set(null);
        this.notice.set('Estado de tarea actualizado.');
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        this.busyAction.set(null);
        this.error.set(this.apiError(error, 'No se pudo cambiar el estado.'));
      },
    });
  }

  openGoal(goal?: GoalView): void {
    if (!this.isAdmin()) return;
    this.notice.set('');
    this.error.set('');
    this.editingGoal.set(goal ?? null);
    this.editingTask.set(null);
    this.goalForm.reset(
      goal
        ? {
            workerId: goal.workerId,
            goalType: goal.goalType,
            periodStart: goal.periodStart,
            periodEnd: goal.periodEnd,
            targetValue: Number(goal.targetValue),
            notes: goal.notes ?? '',
            active: goal.active,
          }
        : {
            workerId: this.auth.session()?.id ?? 0,
            goalType: 'QUOTATIONS_ISSUED',
            periodStart: this.dateToday(),
            periodEnd: this.dateToday(),
            targetValue: 1,
            notes: '',
            active: true,
          },
    );
    this.formOpen.set(true);
  }

  submitGoal(): void {
    if (!this.isAdmin()) return;
    if (this.goalForm.invalid) {
      this.goalForm.markAllAsTouched();
      return;
    }
    const value = this.goalForm.getRawValue();
    const input: GoalInput = {
      workerId: value.workerId,
      goalType: value.goalType,
      periodStart: value.periodStart,
      periodEnd: value.periodEnd,
      targetValue: value.targetValue,
      notes: value.notes,
      active: value.active,
    };
    this.saving.set(true);
    const goal = this.editingGoal();
    const request = goal
      ? this.api.updateGoal(goal.publicId, { ...input, version: goal.version })
      : this.api.createGoal(input);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.notice.set(goal ? 'Meta actualizada.' : 'Meta creada.');
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(this.apiError(error, 'No se pudo guardar la meta.'));
      },
    });
  }

  downloadReport(kind: 'quotations' | 'accounting'): void {
    this.error.set('');
    this.notice.set('');
    this.busyAction.set(kind);
    this.api
      .downloadReport(kind, {
        from: this.reportFrom || undefined,
        to: this.reportTo || undefined,
        status: this.reportStatus || undefined,
        currency: this.reportCurrency || undefined,
      })
      .subscribe({
        next: (response: HttpResponse<Blob>) => {
          this.busyAction.set(null);
          if (!response.body) {
            this.error.set('El servidor no devolvió un archivo.');
            return;
          }
          const url = URL.createObjectURL(response.body);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download =
            kind === 'quotations' ? 'giova-cotizaciones.xlsx' : 'giova-contabilidad.xlsx';
          anchor.click();
          URL.revokeObjectURL(url);
          this.notice.set('Reporte descargado.');
        },
        error: (error: HttpErrorResponse) => {
          this.busyAction.set(null);
          this.error.set(this.apiError(error, 'No se pudo generar el reporte.'));
        },
      });
  }

  taskPriorityLabel = taskPriorityLabel;
  taskStatusLabel = taskStatusLabel;
  goalTypeLabel = goalTypeLabel;
  percent = percent;

  private loadTasks(): void {
    this.api
      .tasks({
        q: this.query,
        status: this.statusFilter === 'ALL' ? undefined : this.statusFilter,
        page: this.page(),
        size: this.pageSize,
      })
      .subscribe({
        next: (page) => this.setPage(page.items, page.totalItems, page.totalPages),
        error: (error: HttpErrorResponse) => this.fail(error, 'No se pudieron cargar las tareas.'),
      });
  }

  private loadGoals(): void {
    this.api
      .goals({
        active: this.goalActiveFilter === 'ALL' ? undefined : this.goalActiveFilter === 'true',
        page: this.page(),
        size: this.pageSize,
      })
      .subscribe({
        next: (page) => this.setPage(page.items, page.totalItems, page.totalPages),
        error: (error: HttpErrorResponse) => this.fail(error, 'No se pudieron cargar las metas.'),
      });
  }

  private loadAudit(): void {
    this.api
      .auditEvents({
        q: this.query,
        result: this.auditResult === 'ALL' ? undefined : this.auditResult,
        page: this.page(),
        size: this.pageSize,
      })
      .subscribe({
        next: (page) => {
          this.auditEvents.set(page.items);
          this.totalItems.set(page.totalItems);
          this.totalPages.set(page.totalPages);
          this.loading.set(false);
        },
        error: (error: HttpErrorResponse) => this.fail(error, 'No se pudo cargar la auditoría.'),
      });
  }

  private setPage<T>(items: T[], total: number, pages: number): void {
    if (this.section() === 'tasks') this.tasks.set(items as TaskView[]);
    if (this.section() === 'goals') this.goals.set(items as GoalView[]);
    this.totalItems.set(total);
    this.totalPages.set(pages);
    this.loading.set(false);
  }

  private fail(error: HttpErrorResponse, fallback: string): void {
    this.error.set(this.apiError(error, fallback));
    this.loading.set(false);
  }

  private apiError(error: HttpErrorResponse, fallback: string): string {
    const body = error.error as { detail?: string; message?: string } | null;
    return body?.detail || body?.message || fallback;
  }

  private resetPeriodDefaults(): void {
    const today = this.dateToday();
    this.goalForm.patchValue({ periodStart: today, periodEnd: today });
  }

  private dateToday(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private toDateTimeLocal(value: string | null): string {
    return value ? value.slice(0, 16) : '';
  }

  private toIso(value: string): string | null {
    return value ? new Date(value).toISOString() : null;
  }
}
