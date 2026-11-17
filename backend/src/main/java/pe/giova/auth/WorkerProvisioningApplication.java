package pe.giova.auth;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import pe.giova.GiovaBackendApplication;

public final class WorkerProvisioningApplication {
    private WorkerProvisioningApplication() {
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(GiovaBackendApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
        try {
            String username = required("GIOVA_WORKER_USERNAME");
            String displayName = required("GIOVA_WORKER_DISPLAY_NAME");
            String password = required("GIOVA_WORKER_PASSWORD");
            String role = required("GIOVA_WORKER_ROLE");
            String tenantCode = System.getenv("GIOVA_WORKER_TENANT_CODE");
            boolean confirmSuperAdmin = "CREATE_SUPERADMIN".equals(System.getenv("GIOVA_WORKER_CONFIRM_SUPERADMIN"));
            long id = context.getBean(WorkerProvisioningService.class)
                    .create(username, displayName, password, role, tenantCode, confirmSuperAdmin);
            System.out.println("Trabajador creado: " + id);
        } finally {
            context.close();
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta la variable de entorno " + name);
        }
        return value;
    }
}
