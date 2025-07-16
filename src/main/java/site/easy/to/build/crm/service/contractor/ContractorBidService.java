// ContractorBidService.java
package site.easy.to.build.crm.service.bid;

import site.easy.to.build.crm.entity.ContractorBid;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Ticket;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ContractorBidService {
    
    // ===== BASIC CRUD OPERATIONS =====
    
    ContractorBid save(ContractorBid bid);
    Optional<ContractorBid> findById(Long id);
    List<ContractorBid> findAll();
    void delete(ContractorBid bid);
    void deleteById(Long id);
    
    // ===== BID MANAGEMENT =====
    
    /**
     * Create bid invitations for multiple contractors
     */
    List<ContractorBid> createBidInvitations(Ticket ticket, List<Customer> contractors, Long createdBy);
    
    /**
     * Submit a bid by contractor
     */
    ContractorBid submitBid(Long bidId, BigDecimal amount, String description, 
                           Integer estimatedHours, LocalDateTime estimatedStart);
    
    /**
     * Accept a bid
     */
    ContractorBid acceptBid(Long bidId, Long reviewedBy);
    
    /**
     * Reject a bid
     */
    ContractorBid rejectBid(Long bidId, Long reviewedBy, String reason);
    
    /**
     * Withdraw a bid (by contractor)
     */
    ContractorBid withdrawBid(Long bidId);
    
    // ===== SEARCH AND RETRIEVAL =====
    
    List<ContractorBid> findBidsForTicket(Integer ticketId);
    List<ContractorBid> findBidsForContractor(Integer contractorId);
    Optional<ContractorBid> findBidByTicketAndContractor(Integer ticketId, Integer contractorId);
    List<ContractorBid> findBidsByStatus(String status);
    List<ContractorBid> findPendingBids();
    List<ContractorBid> findSubmittedBidsForTicket(Integer ticketId);
    Optional<ContractorBid> findAcceptedBidForTicket(Integer ticketId);
    
    // ===== ANALYTICS AND REPORTING =====
    
    long countBidsForTicket(Integer ticketId);
    long countBidsForContractor(Integer contractorId);
    BigDecimal getAverageBidAmountForTicket(Integer ticketId);
    List<ContractorBid> getLowestBidsForTicket(Integer ticketId, int limit);
    List<ContractorBid> getHighestBidsForTicket(Integer ticketId, int limit);
    
    // ===== CONTRACTOR PERFORMANCE =====
    
    double getContractorWinRate(Integer contractorId);
    BigDecimal getContractorAverageBidAmount(Integer contractorId);
    List<ContractorBid> getContractorRecentBids(Integer contractorId, int limit);
    
    // ===== MAINTENANCE AND CLEANUP =====
    
    List<ContractorBid> findOverdueBidResponses(int daysOverdue);
    List<ContractorBid> findBidsNeedingFollowUp(int daysAgo);
    void cleanupBidsForTicket(Integer ticketId);
    void cleanupBidsForContractor(Integer contractorId);
}

