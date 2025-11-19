package com.securesign.controller;

import com.securesign.config.RabbitMQConfig;
import com.securesign.model.SigningRequest;
import com.securesign.service.MinioStorageService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final MinioStorageService storageService;
    private final RabbitTemplate rabbitTemplate;

    public DocumentController(MinioStorageService storageService, RabbitTemplate rabbitTemplate) {
        this.storageService = storageService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file, Authentication authentication) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }
        
        // 1. Dosyayı MinIO'ya yükle
        String fileName = storageService.uploadFile(file);
        String username = authentication.getName();

        // 2. İmzalama isteğini kuyruğa at
        SigningRequest request = new SigningRequest(fileName, username);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY, request);

        return ResponseEntity.ok("File uploaded and signing process started. ID: " + fileName);
    }
}
