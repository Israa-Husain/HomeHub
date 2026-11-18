package com.ga.HomeHub.auth.storage;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ProfileImageStorage {
    private final ObjectProvider<S3Client> clients;

    public ProfileImageStorage(ObjectProvider<S3Client> clients) {
        this.clients = clients;
    }

    @Value("${homehub.storage.bucket:}")
    private String bucket;

    @Value("${homehub.storage.public-base:}")
    private String publicBase;

    //Store images validated by UserService in local storage or S3.
    public String save(String name, byte[] bytes, String extension) throws IOException {
        if(bucket.isBlank()){
            Path directory = Path.of("uploads");
            Files.createDirectories(directory);
            Files.write(directory.resolve(name), bytes);
            return "/uploads/" + name;
        }

        if(publicBase.isBlank() || !publicBase.startsWith("https://")){
            throw new IllegalStateException("Configure an HTTPS profile-image delivery base URL");
        }

        String type = extension.equals(".jpg")?"image/jpeg":extension.equals(".png")?"image/png":"image/webp";
        clients.getObject().putObject(PutObjectRequest.builder().bucket(bucket).key("profiles/"+name).contentType(type).build(), RequestBody.fromBytes(bytes));

        return publicBase.replaceAll("$", "")+"/profiles/"+name;
    }
}
