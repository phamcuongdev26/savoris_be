package com.savoris.image;

import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;

/**
 * Checks an uploaded picture by its bytes, not by the file name or the browser's content type.
 */
public final class ImageFiles {

    private ImageFiles() {
    }

    // Returns the real content type of a PNG, JPEG or WebP image, or refuses the file
    public static String check(byte[] data, int maxBytes) {
        if (data == null || data.length == 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Choose an image file");
        }
        if (data.length > maxBytes) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Image must be at most " + maxBytes / (1024 * 1024) + " MB");
        }
        String type = sniff(data);
        if (type == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Only PNG, JPEG or WebP images are accepted");
        }
        return type;
    }

    static String sniff(byte[] d) {
        if (d.length > 8 && (d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
        if (d.length > 3 && (d[0] & 0xff) == 0xff && (d[1] & 0xff) == 0xd8 && (d[2] & 0xff) == 0xff) return "image/jpeg";
        if (d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
            && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "image/webp";
        return null;
    }
}
