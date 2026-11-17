package pe.giova.task;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import pe.giova.auth.RoleCode;
import pe.giova.auth.WorkerPrincipal;
import pe.giova.auth.WorkerRepository;
import pe.giova.task.api.TaskDtos;

@SpringJUnitConfig(classes = TaskControllerAuthorizationTest.SecurityTestConfiguration.class)
class TaskControllerAuthorizationTest {
    private static final TaskDtos.Input INPUT = new TaskDtos.Input(
            "Revisar", null, 6L, null, null, TaskPriority.MEDIUM, null);

    @Autowired
    private TaskController controller;

    @Autowired
    private TaskService service;

    @BeforeEach
    void resetService() {
        reset(service);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRequiresTheExactAdminRoleAnnotation() throws NoSuchMethodException {
        PreAuthorize authorization = TaskController.class
                .getMethod("create", TaskDtos.Input.class, Authentication.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("hasRole('ADMIN')", authorization.value());
    }

    @Test
    void accountantAndSellerAreDeniedTaskCreation() {
        for (RoleCode role : List.of(RoleCode.ACCOUNTANT, RoleCode.SELLER)) {
            Authentication authentication = authenticate(role);

            assertThrows(AccessDeniedException.class,
                    () -> controller.create(INPUT, authentication), role.name());
        }
    }

    @Test
    void adminCanCreateTasks() {
        Authentication authentication = authenticate(RoleCode.ADMIN);
        when(service.create(any(TaskDtos.Input.class), any(WorkerPrincipal.class)))
                .thenReturn(view());

        assertEquals(201, assertDoesNotThrow(() -> controller.create(INPUT, authentication))
                .getStatusCode().value());
    }

    @Test
    void accountantAndSellerCanListTasks() {
        when(service.find(any(), any(), any(), anyInt(), anyInt(), any(WorkerPrincipal.class)))
                .thenReturn(new TaskDtos.Page(List.of(), 0, 0, 20, 0));

        for (RoleCode role : List.of(RoleCode.ACCOUNTANT, RoleCode.SELLER)) {
            Authentication authentication = authenticate(role);

            assertDoesNotThrow(() -> controller.list(null, null, null, 0, 20, authentication),
                    role.name());
        }
    }

    private static Authentication authenticate(RoleCode role) {
        WorkerPrincipal principal = new WorkerPrincipal(new WorkerRepository.WorkerRecord(
                role == RoleCode.SELLER ? 6L : 5L, role.name().toLowerCase() + "@test.local",
                role.label(), "hash", true, role));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    private static TaskDtos.View view() {
        Instant now = Instant.now();
        return new TaskDtos.View("new-task", "Revisar", null, 6L, "Vendedor", 4L, "Administrador",
                null, null, TaskPriority.MEDIUM, TaskStatus.OPEN, null, null, 0L, now, now);
    }

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class SecurityTestConfiguration {
        @Bean
        TaskService taskService() {
            return mock(TaskService.class);
        }

        @Bean
        TaskController taskController(TaskService taskService) {
            return new TaskController(taskService);
        }
    }
}
