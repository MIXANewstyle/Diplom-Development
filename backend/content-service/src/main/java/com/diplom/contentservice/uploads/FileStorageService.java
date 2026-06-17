package com.diplom.contentservice.uploads;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
public class FileStorageService {

    private static final Map<String, String> MIME_TO_EXT = Map.of(
            "image/jpeg", ".jpg",
            "image/png",  ".png",
            "image/webp", ".webp",
            "image/gif",  ".gif"
    );

    /** Strict whitelist: only UUID-like names + dot + extension — no slashes, no dots-only, no null bytes. */
    private static final Pattern SAFE_FILENAME = Pattern.compile("^[a-zA-Z0-9._-]+$");

    @Value("${app.uploads.dir:/app/uploads}")
    private String uploadsDirPath;

    private Path uploadsDir;

    @PostConstruct
    public void init() throws IOException {
        uploadsDir = Paths.get(uploadsDirPath).toAbsolutePath().normalize();
        Files.createDirectories(uploadsDir);
        log.info("Uploads directory initialised at: {}", uploadsDir);
    }

    /**
     * Stores the given stream under a server-generated filename derived from the
     * validated MIME type.  Returns the generated filename (e.g. {@code "9f3c…e1.png"}).
     */
    public String store(InputStream inputStream, String mimeType) throws IOException {
        String ext = MIME_TO_EXT.getOrDefault(mimeType, ".bin");
        String filename = UUID.randomUUID() + ext;
        Path target = uploadsDir.resolve(filename);
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Stored upload: {}", filename);
        return filename;
    }

    /**
     * Resolves a filename to its absolute {@link Path} inside the uploads directory.
     *
     * @throws IllegalArgumentException if the filename contains illegal characters.
     * @throws java.nio.file.NoSuchFileException if the file does not exist.
     * @throws SecurityException if the resolved path escapes the uploads directory.
     */
    public Path resolve(String filename) throws IOException {
        if (!SAFE_FILENAME.matcher(filename).matches()) {
            throw new IllegalArgumentException("Invalid filename: " + filename);
        }

        Path resolved = uploadsDir.resolve(filename).normalize();

        // Traversal guard: resolved path must still be inside uploadsDir
        if (!resolved.startsWith(uploadsDir)) {
            throw new SecurityException("Path traversal attempt detected for: " + filename);
        }

        if (!Files.exists(resolved)) {
            throw new java.nio.file.NoSuchFileException(filename);
        }

        return resolved;
    }
}
