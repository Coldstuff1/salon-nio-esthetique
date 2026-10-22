package ca.nio.backend.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import ca.nio.backend.model.Employee;

public interface EmployeeRepository extends ListCrudRepository<Employee, UUID> {

    List<Employee> findByActiveTrueOrderByDisplayName();

    /**
     * Takes {@code quantity} units out of stock if enough are on hand. Returns the number of rows updated (0 or 1).
     */
    @Modifying
    @Query("""
            UPDATE product_offering SET quantity_on_hand = quantity_on_hand - :quantity
            WHERE employee_id = :employeeId AND product_id = :productId AND active AND quantity_on_hand >= :quantity
            """)
    int reserveStock(@Param("employeeId") UUID employeeId, @Param("productId") UUID productId,
                     @Param("quantity") int quantity);

    @Modifying
    @Query("""
            UPDATE product_offering SET quantity_on_hand = quantity_on_hand + :quantity
            WHERE employee_id = :employeeId AND product_id = :productId
            """)
    int releaseStock(@Param("employeeId") UUID employeeId, @Param("productId") UUID productId,
                     @Param("quantity") int quantity);

    /**
     * Stock is changed outside the aggregate, so bump its version to make a concurrent save of a stale
     * {@link Employee} (which rewrites its offerings) fail instead of overwriting the new quantity.
     */
    @Modifying
    @Query("UPDATE employee SET version = version + 1 WHERE id = :employeeId")
    int touch(@Param("employeeId") UUID employeeId);

}
