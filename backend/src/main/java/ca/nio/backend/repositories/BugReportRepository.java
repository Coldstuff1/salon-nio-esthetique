package ca.nio.backend.repositories;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.repository.ListCrudRepository;

import ca.nio.backend.model.BugReport;

public interface BugReportRepository extends ListCrudRepository<BugReport, UUID> {

    long countByReporterIdAndCreatedAtAfter(UUID reporterId, Instant after);

}
