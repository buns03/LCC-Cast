package lccast.voting.system.controller;

import lccast.voting.system.service.SupabaseStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/storage")
public class StorageController {

    private final SupabaseStorageService storageService;

    public StorageController(SupabaseStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/file")
    public ResponseEntity<byte[]> getFile(@RequestParam String path) {
        return storageService.getFile(path);
    }
}