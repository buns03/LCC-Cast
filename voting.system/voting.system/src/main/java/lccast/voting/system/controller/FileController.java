package lccast.voting.system.controller;

import lccast.voting.system.service.SupabaseStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileController {

    private final SupabaseStorageService supabaseStorageService;

    public FileController(SupabaseStorageService supabaseStorageService) {
        this.supabaseStorageService = supabaseStorageService;
    }

    // Serves any storage-bucket file (candidate photos, posters, logos)
    // through the backend, since the bucket is not public and paths
    // are stored WITHOUT a host — see SupabaseStorageService.uploadFile.
    @GetMapping("/files/**")
    public ResponseEntity<byte[]> serveFile(
            jakarta.servlet.http.HttpServletRequest request) {

        String fullPath = request.getRequestURI();
        String storagePath = fullPath.substring("/files/".length());

        return supabaseStorageService.getFile(storagePath);
    }
}