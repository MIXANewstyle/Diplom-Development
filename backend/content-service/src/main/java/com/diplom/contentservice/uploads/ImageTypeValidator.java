package com.diplom.contentservice.uploads;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Validates image MIME types by inspecting the leading magic bytes of an
 * {@link InputStream}.  No external library is needed; detection covers JPEG,
 * PNG, GIF and WEBP — the four accepted formats.
 *
 * <p>The caller is responsible for passing an {@link InputStream} that is
 * still positioned at the very beginning (i.e. the stream has not been read).
 * The method reads at most {@value #HEADER_SIZE} bytes; the caller should
 * <em>not</em> reuse the stream afterwards — use {@link
 * org.springframework.web.multipart.MultipartFile#getInputStream()} again if a
 * second read is needed.</p>
 */
@Component
public class ImageTypeValidator {

    private static final int HEADER_SIZE = 12;

    /** The set of MIME types that are accepted for upload. */
    public static final Set<String> ACCEPTED_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    /**
     * Detects the MIME type from magic bytes.
     *
     * @param inputStream a fresh stream positioned at offset 0.
     * @return a MIME-type string if recognised, or {@code null} if the bytes
     *         do not match any accepted image format.
     * @throws IOException on read errors.
     */
    public String detect(InputStream inputStream) throws IOException {
        byte[] header = new byte[HEADER_SIZE];
        int read = inputStream.read(header, 0, HEADER_SIZE);
        if (read < 4) {
            return null;
        }

        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF &&
            (header[1] & 0xFF) == 0xD8 &&
            (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((header[0] & 0xFF) == 0x89 &&
            (header[1] & 0xFF) == 0x50 &&  // 'P'
            (header[2] & 0xFF) == 0x4E &&  // 'N'
            (header[3] & 0xFF) == 0x47) {  // 'G'
            return "image/png";
        }

        // GIF: 47 49 46 38 (GIF8)
        if ((header[0] & 0xFF) == 0x47 &&  // 'G'
            (header[1] & 0xFF) == 0x49 &&  // 'I'
            (header[2] & 0xFF) == 0x46 &&  // 'F'
            (header[3] & 0xFF) == 0x38) {  // '8'
            return "image/gif";
        }

        // WEBP: RIFF????WEBP  (bytes 0-3 = "RIFF", bytes 8-11 = "WEBP")
        if (read >= 12 &&
            (header[0] & 0xFF) == 0x52 &&  // 'R'
            (header[1] & 0xFF) == 0x49 &&  // 'I'
            (header[2] & 0xFF) == 0x46 &&  // 'F'
            (header[3] & 0xFF) == 0x46 &&  // 'F'
            (header[8] & 0xFF) == 0x57 &&  // 'W'
            (header[9] & 0xFF) == 0x45 &&  // 'E'
            (header[10] & 0xFF) == 0x42 && // 'B'
            (header[11] & 0xFF) == 0x50) { // 'P'
            return "image/webp";
        }

        return null;
    }
}
