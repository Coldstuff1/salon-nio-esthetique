package ca.nio.backend.services;

import java.net.URI;
import java.util.Optional;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import jakarta.annotation.PreDestroy;

/**
 * The only place that talks to the S3-compatible object store. Bytes never pass through the backend:
 * browsers upload and download with short-lived presigned URLs.
 */
@Component
public class MediaStorage {

    private final MediaProperties properties;
    private final S3Client client;
    private final S3Presigner presigner;

    public MediaStorage(MediaProperties properties) {
        this.properties = properties;
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.client = S3Client.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
        // signing is offline, so the presigner only needs to know the host the browser will use
        this.presigner = S3Presigner.builder()
                .endpointOverride(properties.publicEndpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
    }

    /** The browser must send exactly this content type and length. */
    public URI presignPut(String key, String contentType, long sizeBytes) {
        return URI.create(presigner.presignPutObject(r -> r
                .signatureDuration(properties.uploadUrlTtl())
                .putObjectRequest(o -> o.bucket(properties.bucket()).key(key)
                        .contentType(contentType).contentLength(sizeBytes))).url().toString());
    }

    public URI presignGet(String key) {
        return URI.create(presigner.presignGetObject(r -> r
                .signatureDuration(properties.downloadUrlTtl())
                .getObjectRequest(o -> o.bucket(properties.bucket()).key(key))).url().toString());
    }

    public Optional<HeadObjectResponse> head(String key) {
        try {
            return Optional.of(client.headObject(r -> r.bucket(properties.bucket()).key(key)));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public void delete(String key) {
        client.deleteObject(r -> r.bucket(properties.bucket()).key(key));
    }

    @EventListener(ApplicationReadyEvent.class)
    void createBucketIfConfigured() {
        if (!properties.createBucket()) {
            return;
        }
        try {
            client.headBucket(r -> r.bucket(properties.bucket()));
        } catch (NoSuchBucketException e) {
            client.createBucket(r -> r.bucket(properties.bucket()));
        }
        if (!properties.allowedOrigins().isEmpty()) {
            client.putBucketCors(r -> r.bucket(properties.bucket()).corsConfiguration(c -> c.corsRules(
                    CORSRule.builder()
                            .allowedOrigins(properties.allowedOrigins())
                            .allowedMethods("GET", "PUT", "HEAD")
                            .allowedHeaders("*")
                            .maxAgeSeconds(3600)
                            .build())));
        }
    }

    @PreDestroy
    void close() {
        presigner.close();
        client.close();
    }

}
