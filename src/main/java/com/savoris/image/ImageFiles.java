package com.savoris.image;

import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

/**
 * Checks an uploaded picture by its bytes, not by the file name or the browser's content type.
 */
public final class ImageFiles {

    private ImageFiles() {
    }

    // Returns the real content type of a PNG, JPEG or WebP image, or refuses the file
    public static String check(byte[] data, int maxBytes) {
        if (data == null || data.length == 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Vui lòng chọn tệp ảnh");
        }
        if (data.length > maxBytes) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Ảnh không được vượt quá " + maxBytes / (1024 * 1024) + " MB");
        }
        String type = sniff(data);
        if (type == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Chỉ nhận ảnh JPG, PNG hoặc WebP");
        }
        return type;
    }

    public static String checkComplete(byte[] data, int maxBytes) {
        String type = check(data, maxBytes);
        if ("image/webp".equals(type)) {
            long declaredSize = Integer.toUnsignedLong((data[4] & 0xff) | ((data[5] & 0xff) << 8) | ((data[6] & 0xff) << 16) | ((data[7] & 0xff) << 24));
            String chunk = new String(data, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (declaredSize + 8 != data.length || !(chunk.equals("VP8 ") || chunk.equals("VP8L") || chunk.equals("VP8X"))) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "File WebP không hợp lệ");
            }
        } else {
            try {
                if (ImageIO.read(new ByteArrayInputStream(data)) == null) throw new AppException(ErrorCode.INVALID_REQUEST, "Nội dung ảnh không hợp lệ");
            } catch (java.io.IOException e) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Nội dung ảnh không hợp lệ");
            }
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
