package com.diplom.contentservice.uploads;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5 MB

    private static final Map<String, String> EXT_TO_MIME = Map.of(
            ".jpg",  "image/jpeg",
            ".png",  "image/png",
            ".webp", "image/webp",
            ".gif",  "image/gif"
    );

    private final FileStorageService fileStorageService;
    private final ImageTypeValidator  imageTypeValidator;

    // -------------------------------------------------------------------------
    // POST /api/v1/uploads/image
    // AUTHOR or ADMIN only (role hierarchy: ADMIN > AUTHOR)
    // -------------------------------------------------------------------------
    @PostMapping("/image")
    @PreAuthorize("hasRole('AUTHOR')")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file) throws IOException {

        // 1. Size check (multipart config also enforces this at the container level)
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE,
                    "File too large. Maximum allowed size is 5 MB.");
        }

        // 2. Magic-byte validation — open a first stream just for detection
        String detectedMime;
        try (InputStream detectStream = file.getInputStream()) {
            detectedMime = imageTypeValidator.detect(detectStream);
        }

        if (detectedMime == null) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Unsupported file type. Only JPEG, PNG, WEBP and GIF images are accepted.");
        }

        // 3. Store — open a second fresh stream for the actual write
        String filename;
        try (InputStream storeStream = file.getInputStream()) {
            filename = fileStorageService.store(storeStream, detectedMime);
        }

        log.info("Image uploaded: {} ({})", filename, detectedMime);

        String url = "/api/v1/uploads/files/" + filename;
        return ResponseEntity.ok(Map.of("url", url));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/uploads/files/{filename}
    // Public — no authentication required (permitted in SecurityConfig)
    // -------------------------------------------------------------------------
    @GetMapping("/files/{filename}")
    public void serveFile(
            @PathVariable String filename,
            HttpServletResponse response) throws IOException {

        Path filePath;
        try {
            filePath = fileStorageService.resolve(filename);
        } catch (NoSuchFileException | IllegalArgumentException | SecurityException e) {
            log.debug("File not found or invalid: {}", filename);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
            return;
        }

        // Derive Content-Type from extension
        String contentType = resolveContentType(filename);
        response.setContentType(contentType);
        response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        response.setContentLengthLong(Files.size(filePath));

        Files.copy(filePath, response.getOutputStream());
        response.getOutputStream().flush();
    }

    private String resolveContentType(String filename) {
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx >= 0) {
            String ext = filename.substring(dotIdx).toLowerCase();
            String mime = EXT_TO_MIME.get(ext);
            if (mime != null) return mime;
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
