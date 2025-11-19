package com.securesign.service;

import com.securesign.config.RabbitMQConfig;
import com.securesign.model.SigningRequest;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class SigningListener {

    private final SigningService signingService;

    public SigningListener(SigningService signingService) {
        this.signingService = signingService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void handleSigningRequest(SigningRequest request) {
        System.out.println("Received signing request for file: " + request.getFileName());
        signingService.signPdf(request.getFileName(), request.getUsername());
    }
}

