# Fuentes técnicas consultadas

Consulta: 2026-09-06. Fuentes primarias para decisiones del borrador; verificar de nuevo solo si cambian versiones o requisitos antes de ejecutar.

| Fuente | Hecho usado | Aplicación en el plan |
|---|---|---|
| [Compatibilidad Angular](https://angular.dev/reference/versions) | Angular 21.2 admite Node 24.x, TypeScript 5.9 y RxJS 7 compatible | Familia Angular 21.2; parches fijados en P01 |
| [Releases Angular](https://angular.dev/reference/releases) | Versionado y estados de soporte | Elegir una familia estable y evitar upgrades automáticos |
| [Requisitos Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html) | La página consultada describe Boot 4.1.1, Java mínimo 17 y Maven mínimo 3.6.3 | Boot 4.1.1 y Java 17 con wrapper |
| [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html) | JdbcTemplate y NamedParameterJdbcTemplate soportan SQL parametrizado y mapeo | Persistencia explícita sin ORM para seis tablas sencillas |
| [Hibernate 6.6 dialects](https://docs.hibernate.org/orm/6.6/dialect/) y [Hibernate 7.0 dialects](https://docs.hibernate.org/orm/7.0/dialect/) | Sus tablas de compatibilidad publican mínimos MariaDB distintos: 10.4 frente a 10.5 | No asumir que cualquier stack JPA actual admite MariaDB 10.4; se propone JDBC |
| [Flyway MariaDB](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mariadb) | Driver MariaDB y soporte modular mediante flyway-mysql | Añadir módulo apropiado y comprobar ejecución en motor exacto P03 |
| [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) | Integración SPA con tokens diferidos y renovación después de autenticar/cerrar sesión | Flujo XSRF Angular/Spring por origen único, pruebas de login y logout |
| [PasswordEncoder](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/password-encoder.html) | Abstracción de transformación unidireccional de contraseñas | No almacenar ni comparar contraseñas en texto plano |
| [XAMPP Windows FAQ](https://www.apachefriends.org/faq_windows.html) | XAMPP distribuye MariaDB y se orienta a desarrollo | Decisión informada del usuario por MariaDB existente y entrega local |
| [Docker Desktop networking how-tos](https://docs.docker.com/desktop/features/networking/networking-how-tos/) | host.docker.internal resuelve el host desde contenedor | API Docker conecta a XAMPP externo |
| [Docker Compose startup order](https://docs.docker.com/compose/how-tos/startup-order/) | Orden de arranque y healthchecks son conceptos diferentes | Readiness real de API; base externa no es depends_on de Compose |

La documentación orienta compatibilidad, no prueba este entorno. No se verificó conexión autenticada a MariaDB, Docker Engine, driver/Flyway resueltos ni arranque de aplicaciones. P02/P03 son las pruebas obligatorias para esas incertidumbres.

La página de MariaDB Connector/J y la de mantenimiento MariaDB no pudieron recuperarse mediante el navegador de investigación por formato de respuesta; no se usa ese fallo como evidencia de incompatibilidad ni se afirma soporte garantizado de todos los componentes.
