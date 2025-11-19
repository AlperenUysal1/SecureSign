package com.securesign.service;

import com.securesign.util.CertificateUtils;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Service
public class SigningService {

    private final MinioStorageService minioStorageService;
    private final MinioClient minioClient;
    private final CertificateUtils certificateUtils;

    @Value("${minio.bucket-name}")
    private String bucketName;

    public SigningService(MinioStorageService minioStorageService, MinioClient minioClient, CertificateUtils certificateUtils) {
        this.minioStorageService = minioStorageService;
        this.minioClient = minioClient;
        this.certificateUtils = certificateUtils;
    }

    public void signPdf(String fileName, String username) {
        try {
            System.out.println("Starting signing process for: " + fileName);
            
            // 1. Dosyayı MinIO'dan indir
            InputStream fileStream = minioStorageService.downloadFile(fileName);
            byte[] originalBytes = fileStream.readAllBytes();
            byte[] stampedBytes;

            // 2. ADIM: Önce "Görsel Mühür" (Kırmızı Yazı) Ekle ve Kaydet
            try (PDDocument document = Loader.loadPDF(originalBytes);
                 ByteArrayOutputStream stampedOutputStream = new ByteArrayOutputStream()) {
                
                var page = document.getPage(0); // İlk sayfa
                // AppendMode.APPEND kullanarak mevcut içeriğin üzerine ekle
                try (var contentStream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page, org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true)) {
                    
                    // Yazı tipi ve rengi
                    contentStream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                    contentStream.setNonStrokingColor(java.awt.Color.RED);
                    
                    // Koordinatları biraz daha yukarı alalım (garanti olsun)
                    contentStream.beginText();
                    contentStream.newLineAtOffset(50, 100); 
                    contentStream.showText("DIGITALLY SIGNED BY " + username.toUpperCase());
                    contentStream.endText();
                    
                    contentStream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 10);
                    contentStream.setNonStrokingColor(java.awt.Color.BLACK);
                    contentStream.beginText();
                    contentStream.newLineAtOffset(50, 85);
                    contentStream.showText("Date: " + new Date().toString());
                    contentStream.endText();
                }
                
                // Değişiklikleri kaydet (Mühür Kurusun)
                document.save(stampedOutputStream);
                stampedBytes = stampedOutputStream.toByteArray();
            }

            // 3. ADIM: Mühürlü Dosyayı Tekrar Yükle ve Dijital İmza At
            try (PDDocument document = Loader.loadPDF(stampedBytes);
                 ByteArrayOutputStream signedOutputStream = new ByteArrayOutputStream()) {

                // Keystore hazırla
                KeyStore keyStore = certificateUtils.createTestKeyStore();
                PrivateKey privateKey = (PrivateKey) keyStore.getKey("alias", "password".toCharArray());
                Certificate[] chain = keyStore.getCertificateChain("alias");

                PDSignature signature = new PDSignature();
                signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
                signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
                signature.setName(username);
                signature.setLocation("SecureSign System");
                signature.setReason("Digital Signature by SecureSign");
                signature.setSignDate(Calendar.getInstance());

                document.addSignature(signature, new SignatureInterface() {
                    @Override
                    public byte[] sign(InputStream content) throws IOException {
                        try {
                            List<Certificate> certList = new ArrayList<>(List.of(chain));
                            JcaCertStore certs = new JcaCertStore(certList);
                            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
                            ContentSigner sha1Signer = new JcaContentSignerBuilder("SHA256WithRSA").build(privateKey);
                            
                            gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                                    new JcaDigestCalculatorProviderBuilder().build()).build(sha1Signer, (X509Certificate) chain[0]));
                            gen.addCertificates(certs);
                            
                            CMSProcessableByteArray msg = new CMSProcessableByteArray(content.readAllBytes());
                            CMSSignedData signedData = gen.generate(msg, false);
                            return signedData.getEncoded();
                        } catch (Exception e) {
                            throw new IOException(e);
                        }
                    }
                });

                document.saveIncremental(signedOutputStream);
                
                // 4. İmzalı dosyayı MinIO'ya geri yükle
                String signedFileName = "signed_" + fileName;
                byte[] signedBytes = signedOutputStream.toByteArray();
                
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(signedFileName)
                                .stream(new ByteArrayInputStream(signedBytes), signedBytes.length, -1)
                                .contentType("application/pdf")
                                .build()
                );
                
                System.out.println("File signed successfully with visual stamp: " + signedFileName);
            }

        } catch (Exception e) {
            System.err.println("Error during signing process: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
