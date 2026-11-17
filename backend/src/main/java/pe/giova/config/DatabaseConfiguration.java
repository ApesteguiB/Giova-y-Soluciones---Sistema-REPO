package pe.giova.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import pe.giova.admin.BrandRepository;
import pe.giova.admin.CategoryRepository;
import pe.giova.admin.ProductRepository;
import pe.giova.catalog.CatalogRepository;
import pe.giova.site.PublicSiteResolver;
import pe.giova.customer.CustomerRepository;
import pe.giova.price.PriceHistoryRepository;
import pe.giova.price.PriceReferenceRepository;
import pe.giova.quotation.QuotationRepository;
import pe.giova.invoice.InvoiceRepository;
import pe.giova.purchaseorder.PurchaseOrderRepository;
import pe.giova.delivery.DeliveryRepository;
import pe.giova.warranty.WarrantyRepository;
import pe.giova.task.TaskRepository;
import pe.giova.goal.GoalRepository;
import pe.giova.report.ReportRepository;
import pe.giova.tender.TenderRepository;
import pe.giova.tender.contract.TenderJsonCodec;

@Configuration
@ConditionalOnProperty(name = "giova.catalog.enabled", havingValue = "true")
public class DatabaseConfiguration {

    @Bean(destroyMethod = "close")
    HikariDataSource dataSource(
            @Value("${giova.datasource.url}") String url,
            @Value("${giova.datasource.username}") String username,
            @Value("${giova.datasource.password}") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(5000);
        return dataSource;
    }

    @Bean
    NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    CatalogRepository catalogRepository(NamedParameterJdbcTemplate jdbc, PublicSiteResolver siteResolver) {
        return new CatalogRepository(jdbc, siteResolver);
    }

    @Bean
    BrandRepository brandRepository(NamedParameterJdbcTemplate jdbc) {
        return new BrandRepository(jdbc);
    }

    @Bean
    CategoryRepository categoryRepository(NamedParameterJdbcTemplate jdbc) {
        return new CategoryRepository(jdbc);
    }

    @Bean
    ProductRepository productRepository(NamedParameterJdbcTemplate jdbc) {
        return new ProductRepository(jdbc);
    }

    @Bean
    CustomerRepository customerRepository(NamedParameterJdbcTemplate jdbc) {
        return new CustomerRepository(jdbc);
    }

    @Bean
    PriceReferenceRepository priceReferenceRepository(NamedParameterJdbcTemplate jdbc) {
        return new PriceReferenceRepository(jdbc);
    }

    @Bean
    PriceHistoryRepository priceHistoryRepository(NamedParameterJdbcTemplate jdbc) {
        return new PriceHistoryRepository(jdbc);
    }

    @Bean
    QuotationRepository quotationRepository(NamedParameterJdbcTemplate jdbc) {
        return new QuotationRepository(jdbc);
    }

    @Bean
    InvoiceRepository invoiceRepository(NamedParameterJdbcTemplate jdbc) {
        return new InvoiceRepository(jdbc);
    }

    @Bean
    PurchaseOrderRepository purchaseOrderRepository(NamedParameterJdbcTemplate jdbc) {
        return new PurchaseOrderRepository(jdbc);
    }

    @Bean
    DeliveryRepository deliveryRepository(NamedParameterJdbcTemplate jdbc) {
        return new DeliveryRepository(jdbc);
    }

    @Bean
    WarrantyRepository warrantyRepository(NamedParameterJdbcTemplate jdbc) {
        return new WarrantyRepository(jdbc);
    }

    @Bean
    TaskRepository taskRepository(NamedParameterJdbcTemplate jdbc) {
        return new TaskRepository(jdbc);
    }

    @Bean
    GoalRepository goalRepository(NamedParameterJdbcTemplate jdbc) {
        return new GoalRepository(jdbc);
    }

    @Bean
    ReportRepository reportRepository(NamedParameterJdbcTemplate jdbc) {
        return new ReportRepository(jdbc);
    }

    @Bean
    TenderRepository tenderRepository(NamedParameterJdbcTemplate jdbc, TenderJsonCodec jsonCodec) {
        return new TenderRepository(jdbc, jsonCodec);
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(initMethod = "migrate")
    Flyway flyway(
            @Value("${giova.flyway.url}") String url,
            @Value("${giova.flyway.username}") String username,
            @Value("${giova.flyway.password}") String password) {
        return Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }
}
