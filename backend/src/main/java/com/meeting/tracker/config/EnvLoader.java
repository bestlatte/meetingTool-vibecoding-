package com.meeting.tracker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 啟動時從專案根目錄或 backend 目錄載入 .env，供 GEMINI_API_KEY 等使用。
 * Spring Boot 不會自動讀取 .env，此類在環境變數未設定時從檔案補齊。
 */
@Component
@Order(1)
public class EnvLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnvLoader.class);
    private static final Map<String, String> envFromFile = new HashMap<>();

    @Override
    public void run(ApplicationArguments args) {
        Path backendDir = Path.of(System.getProperty("user.dir"));
        Path rootEnv = backendDir.getParent() != null ? backendDir.getParent().resolve(".env") : null;
        Path localEnv = backendDir.resolve(".env");

        for (Path p : new Path[]{rootEnv, localEnv}) {
            if (p == null) continue;
            if (Files.isRegularFile(p)) {
                try {
                    loadEnv(p);
                    log.info("Loaded .env from {}", p.toAbsolutePath());
                    return;
                } catch (Exception e) {
                    log.warn("Failed to load .env from {}: {}", p, e.getMessage());
                }
            }
        }
    }

    private void loadEnv(Path path) throws Exception {
        for (String line : Files.readAllLines(path)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            if (value.startsWith("'") && value.endsWith("'")) value = value.substring(1, value.length() - 1);
            if (!value.isBlank() && (System.getenv(key) == null || System.getenv(key).isBlank())) {
                envFromFile.put(key, value);
            }
        }
    }

    public static String get(String key) {
        String fromSystem = System.getenv(key);
        if (fromSystem != null && !fromSystem.isBlank()) return fromSystem;
        return envFromFile.get(key);
    }
}
