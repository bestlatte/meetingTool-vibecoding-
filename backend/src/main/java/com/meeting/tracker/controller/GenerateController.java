package com.meeting.tracker.controller;

import com.meeting.tracker.dto.ErrorResponse;
import com.meeting.tracker.service.DocxService;
import com.meeting.tracker.service.GeminiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
public class GenerateController {

    private static final Logger log = LoggerFactory.getLogger(GenerateController.class);
    private static final String DOCX_FILENAME = "meeting_minutes.docx";
    private static final String DOCX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final GeminiService geminiService;
    private final DocxService docxService;

    public GenerateController(GeminiService geminiService, DocxService docxService) {
        this.geminiService = geminiService;
        this.docxService = docxService;
    }

    @PostMapping("/api/generate")
    public ResponseEntity<?> generate(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ErrorResponse("No audio file uploaded"));
        }

        try {
            byte[] audioBytes = file.getBytes();
            String mimeType = file.getContentType();
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "audio/mpeg";
            } else if (!mimeType.startsWith("audio/") && !mimeType.equals("video/mp4")) {
                mimeType = "audio/mpeg";
            }

            String geminiText = geminiService.transcribeAndSummarize(audioBytes, mimeType);
            byte[] docxBytes = docxService.createMeetingMinutesDocx(geminiText);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(DOCX_MEDIA_TYPE));
            headers.setContentDispositionFormData("attachment", DOCX_FILENAME);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(docxBytes);
        } catch (IOException e) {
            log.warn("Failed to process audio file", e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ErrorResponse("Failed to process audio file"));
        } catch (IllegalStateException e) {
            log.warn("Configuration error: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("AI processing failed", e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ErrorResponse("AI processing failed. Please try again later."));
        }
    }
}
