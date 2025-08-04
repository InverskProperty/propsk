package site.easy.to.build.crm.service.ticket;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.repository.TicketRepository;
import site.easy.to.build.crm.entity.Ticket;
import site.easy.to.build.crm.service.property.PropertyService;
import org.springframework.beans.factory.annotation.Autowired;
import site.easy.to.build.crm.service.payprop.PayPropRealTimeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Service
public class TicketServiceImpl implements TicketService{

    private static final Logger log = LoggerFactory.getLogger(TicketServiceImpl.class);

    private final TicketRepository ticketRepository;
    private final PropertyService propertyService;

    @Autowired(required = false)
    private PayPropRealTimeSyncService realTimeSyncService;

    public TicketServiceImpl(TicketRepository ticketRepository, PropertyService propertyService) {
        this.ticketRepository = ticketRepository;
        this.propertyService = propertyService;
    }

    @Override
    public Ticket findByTicketId(int id) {
        // FIXED: Use the new method that eagerly loads relationships
        Ticket ticket = ticketRepository.findByTicketIdWithRelations(id);
        if (ticket == null) {
            // Fallback to original method if the new one doesn't work
            ticket = ticketRepository.findByTicketId(id);
        }
        return ticket;
    }

    @Override
    public Ticket save(Ticket ticket) {
        // Always save to CRM first (source of truth)
        Ticket savedTicket = ticketRepository.save(ticket);
        
        // Optional real-time sync for critical updates
        if (realTimeSyncService != null && shouldPushImmediately(savedTicket)) {
            log.info("⚡ Triggering real-time sync for ticket {} ({})", 
                savedTicket.getTicketId(), savedTicket.getStatus());
            
            // Async push - doesn't block the save operation
            realTimeSyncService.pushUpdateAsync(savedTicket);
        }
        
        return savedTicket;
    }

    @Override
    public void delete(Ticket ticket) {
        ticketRepository.delete(ticket);
    }

    @Override
    public List<Ticket> findManagerTickets(int id) {
        return ticketRepository.findByManagerId(id);
    }

    @Override
    public List<Ticket> findEmployeeTickets(int id) {
        return ticketRepository.findByEmployee_Id(id);
    }

    @Override
    public List<Ticket> findAll() {
        return ticketRepository.findAll();
    }

    @Override
    public List<Ticket> findCustomerTickets(int id) {
        return ticketRepository.findByCustomerCustomerId(id);
    }

    @Override
    public List<Ticket> getRecentTickets(int managerId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return ticketRepository.findByManagerIdOrderByCreatedAtDesc(managerId, pageable);
    }

    @Override
    public List<Ticket> getRecentEmployeeTickets(int employeeId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return ticketRepository.findByEmployee_IdOrderByCreatedAtDesc(employeeId, pageable);
    }

    @Override
    public List<Ticket> getRecentCustomerTickets(int customerId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return ticketRepository.findByCustomerCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    @Override
    public long countByEmployeeId(int employeeId) {
        return ticketRepository.countByEmployee_Id(employeeId);
    }

    @Override
    public long countByManagerId(int managerId) {
        return ticketRepository.countByManagerId(managerId);
    }

    @Override
    public List<Ticket> findByType(String type) {
        return ticketRepository.findByType(type);
    }

    @Override
    public long getActiveTicketCount() {
        return ticketRepository.countByStatus("OPEN");
    }

    @Override
    public List<Object> findAllBids() {
        // This depends on your bid system implementation
        // For now, return empty list or implement based on your bid entity
        return new ArrayList<>();
    }

    @Override
    public long countByCustomerCustomerId(int customerId) {
        return ticketRepository.countByCustomerCustomerId(customerId);
    }

    @Override
    public void deleteAllByCustomer(Customer customer) {
        ticketRepository.deleteAllByCustomer(customer);
    }

    @Override
    public List<Ticket> getTicketsByEmployeeId(int employeeId) {
        return ticketRepository.findByEmployee_Id(employeeId);
    }

    @Override
    public List<Ticket> getTicketsByEmployeeIdAndType(int employeeId, String type) {
        return ticketRepository.findByEmployee_IdAndType(employeeId, type);
    }

    @Override
    public List<Ticket> getTicketsByCustomerIdAndType(int customerId, String type) {
        return ticketRepository.findByCustomerCustomerIdAndType(customerId, type);
    }

    @Override
    public List<Ticket> getTicketsByPayPropPropertyId(String payPropPropertyId) {
        return ticketRepository.findByPayPropPropertyId(payPropPropertyId);
    }

    @Override
    public List<Ticket> getTicketsByPayPropPropertyIdAndType(String payPropPropertyId, String type) {
        return ticketRepository.findByPayPropPropertyIdAndType(payPropPropertyId, type);
    }

    // ===== BRIDGE METHODS FOR CONTROLLER COMPATIBILITY =====
    @Override
    public List<Ticket> getTicketsByPropertyId(Long propertyId) {
        // Convert Long propertyId to String payPropPropertyId
        String payPropPropertyId = getPayPropPropertyIdFromPropertyId(propertyId);
        if (payPropPropertyId != null && !payPropPropertyId.trim().isEmpty()) {
            return ticketRepository.findByPayPropPropertyId(payPropPropertyId);
        }
        return new ArrayList<>();
    }

    @Override
    public Ticket findByPayPropTicketId(String payPropTicketId) {
        return ticketRepository.findByPayPropTicketId(payPropTicketId);
    }

    @Override
    public List<Ticket> getTicketsByPropertyIdAndType(Long propertyId, String type) {
        // Convert Long propertyId to String payPropPropertyId
        String payPropPropertyId = getPayPropPropertyIdFromPropertyId(propertyId);
        if (payPropPropertyId != null && !payPropPropertyId.trim().isEmpty()) {
            return ticketRepository.findByPayPropPropertyIdAndType(payPropPropertyId, type);
        }
        return new ArrayList<>();
    }

    /**
     * Helper method to convert Property database ID to PayProp Property ID
     */
    private String getPayPropPropertyIdFromPropertyId(Long propertyId) {
        if (propertyId == null) {
            return null;
        }
        try {
            Property property = propertyService.findById(propertyId);
            if (property != null) {
                return property.getPayPropPropertyId();
            }
        } catch (Exception e) {
            System.err.println("Error looking up property " + propertyId + ": " + e.getMessage());
        }
        return null;
    }

    private boolean shouldPushImmediately(Ticket ticket) {
        if (realTimeSyncService == null) {
            return false;
        }
        
        return realTimeSyncService.shouldPushImmediately(ticket);
    }
}