package lccast.voting.system.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class SupabaseStorageService {

    private final RestClient restClient;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    public SupabaseStorageService() {
        this.restClient = RestClient.builder().build();
    }

    // =========================================================
    // UPLOAD
    // =========================================================

    public String uploadFile(
            MultipartFile file,
            String type
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required.");
        }

        String originalName =
                file.getOriginalFilename();

        String extension = "";

        if (
                originalName != null &&
                        originalName.contains(".")
        ) {
            extension =
                    originalName.substring(
                            originalName.lastIndexOf(".")
                    );
        }

        String folder = switch (type.toLowerCase()) {
            case "logo" ->
                    "logos";

            case "poster" ->
                    "posters";

            case "campaign" ->
                    "members/campaign";

            case "background" ->
                    "members/background";

            case "photo" ->
                    "members/photo";

            case "profile" -> "profiles";

            default ->
                    throw new IllegalArgumentException(
                            "Invalid file type."
                    );
        };

        String fileName =
                UUID.randomUUID() + extension;

        String storagePath =
                folder + "/" + fileName;

        String uploadUrl =
                supabaseUrl +
                        "/storage/v1/object/" +
                        bucket +
                        "/" +
                        storagePath;

        restClient.post()
                .uri(uploadUrl)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + serviceRoleKey
                )
                .header(
                        "apikey",
                        serviceRoleKey
                )
                .contentType(
                        MediaType.parseMediaType(
                                file.getContentType() != null
                                        ? file.getContentType()
                                        : MediaType.APPLICATION_OCTET_STREAM_VALUE
                        )
                )
                .body(file.getBytes())
                .retrieve()
                .toBodilessEntity();

        /*
         * IMPORTANT:
         *
         * Return ONLY the storage path.
         *
         * Do NOT return a Supabase URL.
         */
        return storagePath;
    }

    // =========================================================
    // DOWNLOAD / PROXY
    // =========================================================

    public ResponseEntity<byte[]> downloadFile(
            String storagePath
    ) {

        if (storagePath == null || storagePath.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        storagePath = normalizeStoragePath(storagePath);

        System.out.println(
                "SUPABASE DOWNLOAD PATH: " + storagePath
        );

        System.out.println(
                "SUPABASE BUCKET: " + bucket
        );

        String fileUrl =
                supabaseUrl +
                        "/storage/v1/object/" +
                        bucket +
                        "/" +
                        storagePath;

        try {

            var response =
                    restClient.get()
                            .uri(fileUrl)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + serviceRoleKey
                            )
                            .header(
                                    "apikey",
                                    serviceRoleKey
                            )
                            .retrieve()
                            .toEntity(byte[].class);

            MediaType contentType =
                    response.getHeaders().getContentType();

            if (contentType == null) {
                contentType = guessContentType(storagePath);
            }

            return ResponseEntity
                    .status(response.getStatusCode())
                    .contentType(contentType)
                    .header(
                            HttpHeaders.CACHE_CONTROL,
                            "private, max-age=300"
                    )
                    .body(response.getBody());

        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {

            System.err.println(
                    "SUPABASE FILE NOT FOUND: " + storagePath
            );

            return ResponseEntity.notFound().build();

        } catch (org.springframework.web.client.HttpClientErrorException e) {

            System.err.println(
                    "SUPABASE STORAGE ERROR [" +
                            e.getStatusCode() +
                            "] PATH: " +
                            storagePath
            );

            System.err.println(
                    "RESPONSE: " +
                            e.getResponseBodyAsString()
            );

            return ResponseEntity
                    .status(e.getStatusCode())
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .internalServerError()
                    .build();
        }
    }

    // =========================================================
    // NORMALIZE STORAGE PATH
    // =========================================================

    private String normalizeStoragePath(String value) {

        if (value == null) {
            return null;
        }

        value = value.trim();

        /*
         * Already a storage path.
         */
        if (!value.startsWith("http://") &&
                !value.startsWith("https://")) {

            String bucketPrefix = bucket + "/";

            if (value.startsWith(bucketPrefix)) {
                value = value.substring(bucketPrefix.length());
            }

            return value;
        }

        /*
         * Supabase storage URL:
         *
         * /storage/v1/object/sign/{bucket}/{path}
         * /storage/v1/object/public/{bucket}/{path}
         * /storage/v1/object/authenticated/{bucket}/{path}
         * /storage/v1/object/{bucket}/{path}
         */
        String marker = "/storage/v1/object/";

        int index = value.indexOf(marker);

        if (index < 0) {
            throw new IllegalArgumentException(
                    "Invalid Supabase storage URL."
            );
        }

        String path =
                value.substring(
                        index + marker.length()
                );

        /*
         * Remove query parameters.
         */
        int queryIndex = path.indexOf("?");

        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }

        /*
         * Remove access type.
         */
        if (path.startsWith("sign/")) {

            path = path.substring(
                    "sign/".length()
            );

        } else if (path.startsWith("public/")) {

            path = path.substring(
                    "public/".length()
            );

        } else if (path.startsWith("authenticated/")) {

            path = path.substring(
                    "authenticated/".length()
            );
        }

        /*
         * Remove bucket name.
         */
        String bucketPrefix = bucket + "/";

        if (path.startsWith(bucketPrefix)) {

            path = path.substring(
                    bucketPrefix.length()
            );
        }

        return path;
    }

    // =========================================================
    // CONTENT TYPE
    // =========================================================

    private MediaType guessContentType(
            String storagePath
    ) {

        String lower =
                storagePath.toLowerCase();

        if (lower.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }

        if (
                lower.endsWith(".jpg") ||
                        lower.endsWith(".jpeg")
        ) {
            return MediaType.IMAGE_JPEG;
        }

        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }

        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }

        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType(
                    "image/webp"
            );
        }

        return MediaType.APPLICATION_OCTET_STREAM;
    }

    public String createSignedUrl(String storagePath, int expiresInSeconds) {
        if (storagePath == null || storagePath.isBlank()) return null;

        storagePath = normalizeStoragePath(storagePath);

        String signUrl = supabaseUrl + "/storage/v1/object/sign/" + bucket + "/" + storagePath;

        try {
            Map<String, Object> body = Map.of("expiresIn", expiresInSeconds);

            Map<?, ?> response = restClient.post()
                    .uri(signUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("signedURL") == null) return null;

            return supabaseUrl + "/storage/v1" + response.get("signedURL").toString();

        } catch (Exception e) {
            System.err.println("SUPABASE SIGN URL ERROR path=" + storagePath + " msg=" + e.getMessage());
            return null;
        }
    }


    public ResponseEntity<byte[]> getFile(String storagePath) {
        return downloadFile(storagePath);
    }
}