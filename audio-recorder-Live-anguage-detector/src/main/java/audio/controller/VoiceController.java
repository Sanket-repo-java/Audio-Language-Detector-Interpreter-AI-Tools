/**
 * 
 */
package audio.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import audio.service.AIService;

/**
 * 
 */
@RestController
@RequestMapping("/api/audio/voice")
public class VoiceController {
	private final AIService aiService;

    public VoiceController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadAudio(@RequestParam("file") MultipartFile file,
                                                           @RequestParam(value = "targetLang", required = false, defaultValue = "en") String targetLang,
                                                           @RequestParam(value = "mediaType", required = false, defaultValue = "audio") String mediaType) {
        try {
            String safeTargetLang = (targetLang == null || targetLang.isBlank()) ? "en" : targetLang;
            String safeMediaType = (mediaType == null || mediaType.isBlank()) ? "audio" : mediaType.toLowerCase();

            String savedMediaPath = aiService.saveMedia(file, safeMediaType);
            String transcription = aiService.transcribeAudio(file);
            String translation = aiService.translateText(transcription, safeTargetLang);
            String summary = aiService.summarizeText(translation);
            String reportPath = aiService.generateReport(transcription, translation, summary);

            Map<String, Object> response = Map.of(
                    "transcription", transcription,
                    "translation", translation,
                    "summary", summary,
                    "reportPath", reportPath,
                        "savedMediaPath", savedMediaPath,
                    "mediaType", safeMediaType
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
