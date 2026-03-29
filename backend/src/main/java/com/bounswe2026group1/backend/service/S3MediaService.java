package com.bounswe2026group1.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class S3MediaService {

    private final S3Client s3Client;        // The object(bean) is in the AwsConfig
    private final String bucketName;        // AWS S3 bucket name(injected from .env)

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg", "image/png", "image/jpg", "video/mp4");

    public S3MediaService(S3Client s3Client, @Value("${aws.s3.bucket}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public String uploadFile(MultipartFile file) {

        String contentType = file.getContentType();

        // Prevents unsupported formats (like .exe or .pdf)
        if (file.isEmpty() || contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Invalid file type.");
        }

        try {
            // Generate UUID prefix to prevent overwrite the same file (names the file uniquely)
            String uniqueFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

            // Builds the object to send it to the AWS
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(uniqueFileName)
                    .contentType(file.getContentType())     // This provides displaying without download
                    .build();

            // Sends the created object
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));

            // Returns a public URL
            return "https://" + bucketName + ".s3.amazonaws.com/" + uniqueFileName;

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload to S3", e);
        }
    }
}