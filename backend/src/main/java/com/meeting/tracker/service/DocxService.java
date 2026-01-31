package com.meeting.tracker.service;

import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocxService {

    private static final String HEADING1_STYLE = "Heading1";
    private static final String HEADING2_STYLE = "Heading2";

    /**
     * 將 Gemini 回傳的結構化文字轉成 Word 文件。
     * 會議主題 → Heading 1；區塊標題 → Heading 2；條列 → Bullet List。
     */
    public byte[] createMeetingMinutesDocx(String geminiText) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            ParsedContent parsed = parseGeminiContent(geminiText);

            // 會議主題（Heading 1）
            if (parsed.title != null && !parsed.title.isBlank()) {
                XWPFParagraph p = doc.createParagraph();
                p.setStyle(HEADING1_STYLE);
                p.createRun().setText(parsed.title.trim());
            }

            // 討論重點摘要（Heading 2 + 條列）
            if (!parsed.summaryItems.isEmpty()) {
                XWPFParagraph h2 = doc.createParagraph();
                h2.setStyle(HEADING2_STYLE);
                h2.createRun().setText("討論重點摘要");
                addBulletParagraphs(doc, parsed.summaryItems);
            }

            // 待辦事項（Heading 2 + 條列）
            if (!parsed.actionItems.isEmpty()) {
                XWPFParagraph h2 = doc.createParagraph();
                h2.setStyle(HEADING2_STYLE);
                h2.createRun().setText("待辦事項");
                addBulletParagraphs(doc, parsed.actionItems);
            }

            // 若解析不到結構，整段當成內文
            if (parsed.title == null && parsed.summaryItems.isEmpty() && parsed.actionItems.isEmpty()) {
                for (String line : geminiText.lines().toList()) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;
                    XWPFParagraph p = doc.createParagraph();
                    p.createRun().setText(trimmed);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private void addBulletParagraphs(XWPFDocument doc, List<String> items) {
        for (String item : items) {
            if (item.isBlank()) continue;
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("• " + item.trim());
        }
    }

    private ParsedContent parseGeminiContent(String text) {
        ParsedContent result = new ParsedContent();
        if (text == null || text.isBlank()) return result;

        String[] lines = text.split("\\r?\\n");
        List<String> currentBullets = new ArrayList<>();
        int phase = 0; // 0: 找標題, 1: 討論重點, 2: 待辦事項

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (phase == 0) {
                if (trimmed.contains("會議主題") || trimmed.contains("主題")) {
                    result.title = trimmed.replaceAll("^[\\d.、\\s]*", "").trim();
                    if (result.title.isEmpty()) result.title = "會議紀錄";
                    phase = 1;
                    continue;
                }
                if (result.title == null) {
                    result.title = trimmed;
                    phase = 1;
                }
                continue;
            }

            if (trimmed.contains("討論重點") || trimmed.contains("重點摘要")) {
                if (!currentBullets.isEmpty() && phase == 1) {
                    result.summaryItems.addAll(currentBullets);
                    currentBullets.clear();
                }
                phase = 1;
                String after = trimmed.replaceAll("^[\\d.、\\s]*討論重點[^：:]*[：:]?\\s*", "").trim();
                if (!after.isEmpty() && !after.equals(trimmed)) currentBullets.add(after);
                continue;
            }

            if (trimmed.contains("待辦事項") || trimmed.contains("Action Items") || trimmed.contains("行動項目")) {
                if (!currentBullets.isEmpty()) {
                    if (phase == 1) result.summaryItems.addAll(currentBullets);
                    else result.actionItems.addAll(currentBullets);
                    currentBullets.clear();
                }
                phase = 2;
                String after = trimmed.replaceAll("^[\\d.、\\s]*待辦事項[^：:]*[：:]?\\s*", "").trim();
                if (!after.isEmpty() && !after.equals(trimmed)) currentBullets.add(after);
                continue;
            }

            if (trimmed.matches("^[•\\-*]\\s*.+") || trimmed.matches("^[\\d.]\\s+.+")) {
                String item = trimmed.replaceFirst("^[•\\-*\\d.]\\s*", "").trim();
                if (!item.isEmpty()) currentBullets.add(item);
                continue;
            }

            if (phase == 1) currentBullets.add(trimmed);
            else if (phase == 2) result.actionItems.add(trimmed);
        }

        if (!currentBullets.isEmpty()) {
            if (phase == 1) result.summaryItems.addAll(currentBullets);
            else result.actionItems.addAll(currentBullets);
        }

        if (result.title == null) result.title = "會議紀錄";
        return result;
    }

    private static class ParsedContent {
        String title;
        List<String> summaryItems = new ArrayList<>();
        List<String> actionItems = new ArrayList<>();
    }
}
