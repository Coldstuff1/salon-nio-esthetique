package ca.nio.backend.services;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param endpoint       S3 endpoint the backend talks to
 * @param publicEndpoint S3 endpoint browsers can reach; presigned URLs are signed for this host
 * @param createBucket   create the bucket and its CORS rule on startup (dev and tests only)
 * @param allowedOrigins browser origins allowed to upload and download directly, used when creating the bucket
 */
@ConfigurationProperties("salon.media")
public record MediaProperties(URI endpoint,
                              URI publicEndpoint,
                              @DefaultValue("us-east-1") String region,
                              String bucket,
                              String accessKey,
                              String secretKey,
                              @DefaultValue("false") boolean createBucket,
                              @DefaultValue List<String> allowedOrigins,
                              @DefaultValue("10m") Duration uploadUrlTtl,
                              @DefaultValue("1h") Duration downloadUrlTtl,
                              @DefaultValue("10MB") DataSize maxImageSize,
                              @DefaultValue("500MB") DataSize maxVideoSize) {
}
