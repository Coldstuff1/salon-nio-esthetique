package ca.nio.backend.repositories;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.ListCrudRepository;

import ca.nio.backend.model.Media;
import ca.nio.backend.model.MediaStatus;

public interface MediaRepository extends ListCrudRepository<Media, UUID> {

    List<Media> findByStatusAndCreatedAtBefore(MediaStatus status, Instant cutoff);

}
