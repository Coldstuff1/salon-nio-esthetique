package ca.nio.backend.services;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.nio.backend.model.Availability;
import ca.nio.backend.model.Employee;
import ca.nio.backend.model.Product;
import ca.nio.backend.model.ProductOffering;
import ca.nio.backend.model.ServiceOffering;
import ca.nio.backend.model.ServiceType;
import ca.nio.backend.model.TimeOff;
import ca.nio.backend.repositories.EmployeeRepository;
import ca.nio.backend.repositories.ProductRepository;
import ca.nio.backend.repositories.ServiceTypeRepository;

import lombok.RequiredArgsConstructor;

/**
 * Changes an employee makes to their own hours and offerings.
 */
@Service
@RequiredArgsConstructor
public class StaffService {

    private final EmployeeRepository employees;
    private final ServiceTypeRepository serviceTypes;
    private final ProductRepository products;
    private final MediaService media;

    @Transactional
    public Employee replaceAvailability(UUID employeeId, Collection<Availability> windows) {
        Employee employee = employee(employeeId);
        employee.setAvailability(new HashSet<>(windows));
        return employees.save(employee);
    }

    @Transactional
    public TimeOff addTimeOff(UUID employeeId, Instant startsAt, Instant endsAt, String reason) {
        Employee employee = employee(employeeId);
        TimeOff timeOff = new TimeOff(UUID.randomUUID(), startsAt, endsAt, reason);
        employee.getTimeOff().add(timeOff);
        employees.save(employee);
        return timeOff;
    }

    @Transactional
    public void removeTimeOff(UUID employeeId, UUID timeOffId) {
        Employee employee = employee(employeeId);
        if (!employee.getTimeOff().removeIf(t -> t.id().equals(timeOffId))) {
            throw new NotFoundException(ErrorCode.TIME_OFF_NOT_FOUND, "Time off " + timeOffId + " not found");
        }
        employees.save(employee);
    }

    @Transactional
    public Employee putServiceOffering(UUID employeeId, ServiceOffering offering) {
        serviceTypes.findById(offering.serviceTypeId())
                .filter(ServiceType::isActive)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SERVICE_NOT_FOUND,
                        "Service " + offering.serviceTypeId() + " not found"));
        Employee employee = employee(employeeId);
        employee.putServiceOffering(offering);
        return employees.save(employee);
    }

    @Transactional
    public Employee putProductOffering(UUID employeeId, ProductOffering offering) {
        products.findById(offering.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND,
                        "Product " + offering.productId() + " not found"));
        Employee employee = employee(employeeId);
        employee.putProductOffering(offering);
        return employees.save(employee);
    }

    /**
     * Attaches a photo the uploader has finished sending, replacing (and deleting) the previous one.
     * Pass a null media id to remove the photo.
     */
    @Transactional
    public Employee setPhoto(UUID employeeId, UUID uploaderId, @Nullable UUID mediaId) {
        Employee employee = employee(employeeId);
        UUID previous = employee.getPhotoId();
        if (mediaId != null && mediaId.equals(previous)) {
            return employee;
        }
        if (mediaId != null) {
            media.claimImage(mediaId, uploaderId);
        }
        employee.setPhotoId(mediaId);
        Employee saved = employees.save(employee);
        media.delete(previous);
        return saved;
    }

    private Employee employee(UUID employeeId) {
        return employees.findById(employeeId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, "Employee " + employeeId + " not found"));
    }

}
