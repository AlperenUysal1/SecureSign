package com.securesign.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SigningRequest implements Serializable {
    private String fileName;
    private String username;
    // İleride imza sertifikası bilgileri vs. eklenebilir
}

