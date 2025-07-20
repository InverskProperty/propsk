package site.easy.to.build.crm.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Ticket;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Integer> {
    public Ticket findByTicketId(int ticketId);

    public List<Ticket> findByManagerId(int id);

    public List<Ticket> findByEmployee_Id(int id);

    List<Ticket> findByCustomerCustomerId(Integer customerId);

    List<Ticket> findByManagerIdOrderByCreatedAtDesc(int managerId, Pageable pageable);

    List<Ticket> findByEmployee_IdOrderByCreatedAtDesc(int managerId, Pageable pageable);

    List<Ticket> findByCustomerCustomerIdOrderByCreatedAtDesc(int customerId, Pageable pageable);

    // ADDED BACK: This method is now valid since Ticket entity has 'type' field and database has 'type' column
    List<Ticket> findByType(String type);
    
    // Additional filtering methods using existing fields
    List<Ticket> findByStatus(String status);
    List<Ticket> findByPriority(String priority);
    List<Ticket> findBySubjectContainingIgnoreCase(String subject);
    
    long countByStatus(String status);

    long countByEmployee_Id(int employeeId);

    long countByManagerId(int managerId);

    long countByCustomerCustomerId(int customerId);

    void deleteAllByCustomer(Customer customer);

    // Employee and type filtering
    List<Ticket> findByEmployee_IdAndType(int employeeId, String type);

    // Customer and type filtering
    List<Ticket> findByCustomerCustomerIdAndType(int customerId, String type);

    // Property-based queries (for maintenance tickets linked to properties)
    List<Ticket> findByPayPropPropertyId(String payPropPropertyId);
    
    List<Ticket> findByPayPropPropertyIdAndType(String payPropPropertyId, String type);
}