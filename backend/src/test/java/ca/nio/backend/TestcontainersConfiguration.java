package ca.nio.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    static final int S3_PORT = 8333;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
    }

    @Bean
    GenericContainer<?> s3Container() {
        return new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.48"))
                .withCommand("server", "-dir=/data", "-ip.bind=0.0.0.0", "-s3", "-s3.port=" + S3_PORT,
                        "-s3.config=/etc/seaweedfs/s3.json")
                .withCopyFileToContainer(MountableFile.forHostPath("docker/seaweedfs/s3.json"),
                        "/etc/seaweedfs/s3.json")
                .withExposedPorts(S3_PORT)
                .waitingFor(Wait.forHttp("/healthz").forPort(S3_PORT));
    }

    @Bean
    DynamicPropertyRegistrar s3Properties(GenericContainer<?> s3Container) {
        return registry -> {
            String endpoint = "http://" + s3Container.getHost() + ":" + s3Container.getMappedPort(S3_PORT);
            registry.add("salon.media.endpoint", () -> endpoint);
            registry.add("salon.media.public-endpoint", () -> endpoint);
        };
    }

}
