package kr.drivelog.phone;

import android.net.Uri;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.HttpsURLConnection;

final class ClipDownload {
    private static final long CACHE_MS = 5 * 60 * 1000L;
    private static final long MAX_BYTES = 256L * 1024 * 1024;
    private static final String ENDPOINT = "https://35.216.33.209/api/handoffs/redeem";

    // Serialized by StoryActivity so activity recreation can reuse a completed redemption.
    static File get(File cache, String id, String code) throws IOException {
        File clips = new File(cache, "story");
        Files.createDirectories(clips.toPath());
        long cutoff = System.currentTimeMillis() - CACHE_MS;
        File[] previous = clips.listFiles();
        if (previous != null) {
            for (File file : previous) {
                if (file.lastModified() <= cutoff) Files.deleteIfExists(file.toPath());
            }
        }
        File target = new File(clips, cacheKey(id, code) + ".mp4");
        if (target.isFile()) return target;

        HttpsURLConnection conn = (HttpsURLConnection) new URL(ENDPOINT).openConnection();
        File temp = File.createTempFile("download-", ".part", cache);
        try {
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            byte[] body = new Uri.Builder().appendQueryParameter("id", id)
                    .appendQueryParameter("code", code).build().getEncodedQuery().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = conn.getOutputStream()) { out.write(body); }
            int status = conn.getResponseCode();
            if (status == 410) throw new IOException("사용했거나 만료된 QR입니다. 새 QR을 발급하세요.");
            if (status == 403 || status == 409) throw new IOException("공유 동의와 차량의 편집 상태를 확인하세요.");
            if (status != 200 || !"video/mp4".equalsIgnoreCase(conn.getContentType()))
                throw new IOException("영상을 받을 수 없습니다. QR을 확인하세요.");
            long expected = conn.getContentLengthLong();
            long count = 0;
            try (InputStream in = conn.getInputStream(); OutputStream out = Files.newOutputStream(temp.toPath())) {
                byte[] chunk = new byte[65536];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    count += n;
                    if (count > MAX_BYTES) throw new IOException("공유할 수 있는 영상 크기를 초과했습니다.");
                    out.write(chunk, 0, n);
                }
            }
            if (count == 0 || (expected >= 0 && count != expected)) throw new IOException("영상 수신이 중단됐습니다. 새 QR을 발급하세요.");
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
            return target;
        } finally {
            conn.disconnect();
            Files.deleteIfExists(temp.toPath());
        }
    }

    private static String cacheKey(String id, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((id + "." + code).getBytes(StandardCharsets.US_ASCII));
            return android.util.Base64.encodeToString(digest, android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
