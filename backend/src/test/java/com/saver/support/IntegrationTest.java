package com.saver.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests de integración contra el PostgreSQL de {@code docker compose} (base {@code saver_test},
 * se crea sola). Si la base no está disponible, los tests se <b>omiten</b> en lugar de fallar.
 *
 * <p>Conexión, en orden de prioridad: variables TEST_DB_HOST/PORT/USER/PASSWORD, luego
 * DB_HOST/PORT/USER/PASSWORD (variables de entorno o el {@code .env} del backend) y por último
 * los valores por defecto de docker compose (localhost:5433, saver/saver).
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    private static final Properties DOT_ENV = loadDotEnv();
    private static final String HOST = setting("HOST", "localhost");
    private static final String PORT = setting("PORT", "5433");
    private static final String USER = setting("USER", "saver");
    private static final String PASSWORD = setting("PASSWORD", "saver");
    private static final String DB = "saver_test";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    @BeforeAll
    static void requireDatabase() {
        Assumptions.assumeTrue(ensureDatabase(), "PostgreSQL no disponible: se omiten los tests de integración");
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> url(DB));
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("app.storage.environment", () -> "test");
        registry.add("app.inflation.enabled", () -> "false");
    }

    private static boolean ensureDatabase() {
        try (Connection c = DriverManager.getConnection(url("postgres"), USER, PASSWORD);
             Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + DB + "'")) {
                if (!rs.next()) st.executeUpdate("CREATE DATABASE " + DB);
            }
            return true;
        } catch (SQLException e) {
            System.err.println("[IntegrationTest] PostgreSQL no disponible en " + url("postgres") + ": " + e.getMessage());
            return false;
        }
    }

    private static String url(String db) {
        return "jdbc:postgresql://" + HOST + ":" + PORT + "/" + db;
    }

    /** TEST_DB_x → DB_x (entorno) → DB_x (.env) → valor por defecto. */
    private static String setting(String name, String fallback) {
        for (String key : new String[] {"TEST_DB_" + name, "DB_" + name}) {
            String v = System.getenv(key);
            if (v != null && !v.isBlank()) return v.trim();
        }
        String v = DOT_ENV.getProperty("DB_" + name);
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    private static Properties loadDotEnv() {
        Properties p = new Properties();
        Path file = Path.of(".env");
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException ignored) {
                // sin .env se usan las variables de entorno o los valores por defecto
            }
        }
        return p;
    }

    /** Registra un usuario nuevo y devuelve su access token. */
    protected String register() throws Exception {
        String email = "it-" + UUID.randomUUID() + "@saver.test";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email, "password", "password123", "displayName", "IT", "locale", "es"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    protected JsonNode read(String body) throws Exception {
        return json.readTree(body);
    }

    protected String toJson(Object o) throws Exception {
        return json.writeValueAsString(o);
    }
}
