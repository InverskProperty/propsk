package site.easy.to.build.crm.service.ticket;

import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Ticket;

import java.util.List;

public interface TicketService {
    public Ticket findByTicketId(int id);

    public Ticket save(Ticket ticket);

    public void delete(Ticket ticket);

    public List<Ticket> findManagerTickets(int id);

    public List<Ticket> findEmployeeTickets(int id);

    public List<Ticket> findAll();

    public List<Ticket> findCustomerTickets(int id);

    List<Ticket> getRecentTickets(int managerId, int limit);

    List<Ticket> getRecentEmployeeTickets(int employeeId, int limit);

    List<Ticket> getRecentCustomerTickets(int customerId, int limit);

    long countByEmployeeId(int employeeId);

    long countByManagerId(int managerId);

    long countByCustomerCustomerId(int customerId);

    List<Ticket> findByType(String type);
    
    long getActiveTicketCount();
    
    List<Object> findAllBids(); // Adjust type based on your bid entity

    void deleteAllByCustomer(Customer customer);

    // Employee-based ticket retrieval
    List<Ticket> getTicketsByEmployeeId(int employeeId);
    
    List<Ticket> getTicketsByEmployeeIdAndType(int employeeId, String type);

    // Customer-based ticket retrieval
    List<Ticket> getTicketsByCustomerIdAndType(int customerId, String type);

    // CORE METHODS: Property-based ticket retrieval using PayProp integration
    List<Ticket> getTicketsByPayPropPropertyId(String payPropPropertyId);
    List<Ticket> getTicketsByPayPropPropertyIdAndType(String payPropPropertyId, String type);
    
    // BRIDGE METHODS: For controller compatibility (Long propertyId -> String payPropPropertyId)
    List<Ticket> getTicketsByPropertyId(Long propertyId);
    List<Ticket> getTicketsByPropertyIdAndType(Long propertyId, String type);
}