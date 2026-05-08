package com.bounswe2026group1.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class S3MediaService {

    private static final Logger log = LoggerFactory.getLogger(S3MediaService.class);

    private final S3Client s3Client;
    private final String bucketName;

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg", "image/png", "image/jpg", "video/mp4");

    public S3MediaService(S3Client s3Client, @Value("${aws.s3.bucket}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    /**
     * Throws IllegalArgumentException if the file is empty or has an unsupported MIME type.
     * Useful when callers need to validate a batch of uploads before any I/O happens.
     */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Invalid file type.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Invalid file type.");
        }
    }

    public String uploadFile(MultipartFile file) {
        validate(file);

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

    /**
     * Deletes the S3 object referenced by {@code url}. The URL must point to this service's
     * configured bucket; URLs from other buckets are rejected so a malicious caller can't
     * trick the app into deleting arbitrary objects.
     */
    public void deleteFile(String url) {
        if (url == null || url.isBlank()) return;

        String prefix = "https://" + bucketName + ".s3.amazonaws.com/";
        if (!url.startsWith(prefix)) {
            throw new IllegalArgumentException("URL does not belong to this bucket: " + url);
        }
        String key = url.substring(prefix.length());
        if (key.isBlank()) {
            throw new IllegalArgumentException("URL has no S3 key");
        }

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
    }
}