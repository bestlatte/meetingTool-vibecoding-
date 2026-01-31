package com.meeting.tracker.service;

import com.meeting.tracker.config.EnvLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models";
    /** 可改為 gemini-3-flash-preview（依 Google 實際提供之模型名稱） */
    private static final String MODEL = "gemini-2.5-flash";
    private static final String PROMPT = """
        你是一位專業秘書。請將這段會議錄音內容轉錄並整理成結構化的會議記錄，需包含：
        1. 會議主題（請自行歸納）
        2. 討論重點摘要（條列式）
        3. 待辦事項（Action Items：每一項需包含負責人與期限）

        請以繁體中文輸出，語氣正式、條理清楚，適合直接作為正式會議紀錄。
        """;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String transcribeAndSummarize(byte[] audioBytes, String mimeType) {
        String apiKey = EnvLoader.get("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not set. Set it in environment or in a .env file in the project root.");
        }

        String base64Audio = Base64.getEncoder().encodeToString(audioBytes);
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "audio/mpeg";
        } else if (!mimeType.startsWith("audio/") && !"video/mp4".equals(mimeType)) {
            mimeType = "audio/mpeg";
        }

        Map<String, Object> inlineData = Map.of(
                "mimeType", mimeType,
                "data", base64Audio
        );
        Map<String, Object> textPart = Map.of("text", PROMPT);
        Map<String, Object> audioPart = Map.of("inlineData", inlineData);

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(textPart, audioPart))
                )
        );

        String url = GEMINI_BASE + "/" + MODEL + ":generateContent?key=" + apiKey;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Gemini API request failed");
        }

        return extractTextFromResponse(response.getBody());
    }

    private String extractTextFromResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode candidates = root.path("candidates");
            if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
                throw new RuntimeException("No content in Gemini response");
            }
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isEmpty()) {
                return "";
            }
            return parts.get(0).path("text").asText("");
        } catch (Exception e) {
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Failed to parse Gemini response", e);
        }
    }
}
