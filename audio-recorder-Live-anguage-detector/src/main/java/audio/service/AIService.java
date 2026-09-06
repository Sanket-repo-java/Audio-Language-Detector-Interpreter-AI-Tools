
/**
 * 
 */
package audio.service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;

/**
 * 
 */
@Service
public class AIService {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String saveMedia(MultipartFile file, String mediaType) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("No audio or video file uploaded.");
        }

        String folder = "video".equalsIgnoreCase(mediaType) ? "video" : "audio";
        Path recordingsDir = Paths.get(System.getProperty("user.dir"), "recordings", folder)
            .toAbsolutePath()
            .normalize();
        Files.createDirectories(recordingsDir);

        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName, folder.equals("video") ? "webm" : "webm");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        String fileName = "recorded-" + timestamp + "." + extension;
        Path filePath = recordingsDir.resolve(fileName).normalize();
        file.transferTo(filePath);

        return filePath.toString();
    }

    private String getExtension(String originalName, String defaultExtension) {
        if (originalName == null || !originalName.contains(".")) {
            return defaultExtension;
        }

        String extension = originalName.substring(originalName.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        return extension.isBlank() ? defaultExtension : extension;
    }

    public String transcribeAudio(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("No RO file uploaded.");
        }

        String originalName = file.getOriginalFilename();
        return "Transcription for RO file: " + (originalName != null ? originalName : "uploaded-audio");
    }

    public String translateText(String text, String targetLang) throws IOException {
        String normalizedLang = targetLang == null ? "en" : targetLang.toLowerCase();
        Map<String, String> regionalLabels = new HashMap<>();
        regionalLabels.put("en", "English");
        regionalLabels.put("hi", "Hindi");
        regionalLabels.put("mr", "Marathi");
        regionalLabels.put("kn", "Kannada");
        regionalLabels.put("te", "Telugu");
        regionalLabels.put("ml", "Malayalam");
        regionalLabels.put("ta", "Tamil");
        regionalLabels.put("bn", "Bengali");
        regionalLabels.put("gu", "Gujarati");
        regionalLabels.put("pa", "Punjabi");
        regionalLabels.put("or", "Odia");
        regionalLabels.put("es", "Spanish");
        regionalLabels.put("fr", "French");
        regionalLabels.put("de", "German");

        String label = regionalLabels.getOrDefault(normalizedLang, "English");
        if ("en".equals(normalizedLang)) {
            return text;
        }

        try {
            return translateWithGoogle(text, normalizedLang);
        } catch (IOException primaryFailure) {
            return translateWithMemory(text, normalizedLang, label);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Translation request was interrupted", e);
        }
    }

    private String translateWithGoogle(String text, String targetLang) throws IOException, InterruptedException {
        String endpoint = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
                + URLEncoder.encode(targetLang, java.nio.charset.StandardCharsets.UTF_8)
                + "&dt=t&q=" + URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(java.time.Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Google translation service returned HTTP " + response.statusCode());
        }
        JsonNode translations = objectMapper.readTree(response.body());
        StringBuilder translatedText = new StringBuilder();
        for (JsonNode item : translations.get(0)) {
            translatedText.append(item.get(0).asText());
        }
        if (translatedText.isEmpty()) {
            throw new IOException("Google translation returned no text");
        }
        return translatedText.toString();
    }

    private String translateWithMemory(String text, String targetLang, String label) throws IOException {
        String endpoint = "https://api.mymemory.translated.net/get?q="
                + URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8)
            + "&langpair=" + URLEncoder.encode("en|" + targetLang, java.nio.charset.StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(java.time.Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Fallback translation request was interrupted", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Fallback translation service returned HTTP " + response.statusCode());
        }
        JsonNode result = objectMapper.readTree(response.body()).path("responseData").path("translatedText");
        if (result.isMissingNode() || result.asText().isBlank()) {
            throw new IOException("Translation services returned no translated text for " + label);
        }
        return result.asText();
    }

    public String summarizeText(String text) {
        return "Summary: The RO recording was successfully captured, processed, and prepared for report generation.";
    }

    public String generateReport(String transcription, String translation, String summary) throws IOException {
        Path reportsDir = Paths.get("generated-reports");
        Files.createDirectories(reportsDir);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String fileName = "ro-report-" + timestamp + ".pdf";
        Path filePath = reportsDir.resolve(fileName);

        Document document = new Document();
        try {
            PdfWriter.getInstance(document, new FileOutputStream(filePath.toFile()));
            document.open();
            Font reportFont = getUnicodeFont();
            document.add(new Paragraph("RO File Report", reportFont));
            document.add(new Paragraph("Transcription: " + transcription, reportFont));
            document.add(new Paragraph("Translation: " + translation, reportFont));
            document.add(new Paragraph("Summary: " + summary, reportFont));
            document.close();
        } catch (DocumentException e) {
            throw new IOException("Failed to generate report PDF", e);
        }

        return filePath.toString();
    }

    private Font getUnicodeFont() throws IOException {
        String[] fontPaths = {
                "C:/Windows/Fonts/Nirmala.ttf",
                "C:/Windows/Fonts/Nirmala UI.ttf",
                "/usr/share/fonts/truetype/noto/NotoSansDevanagari-Regular.ttf"
        };
        for (String fontPath : fontPaths) {
            if (Files.exists(Paths.get(fontPath))) {
                try {
                    BaseFont baseFont = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    return new Font(baseFont, 11);
                } catch (DocumentException e) {
                    throw new IOException("Unable to load report font", e);
                }
            }
        }
        return new Font(Font.FontFamily.HELVETICA, 11);
    }
}
