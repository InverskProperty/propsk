//ContractorBidServiceImpl.java
package site.easy.to.build.crm.service.bid;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.ContractorBid;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Ticket;
import site.easy.to.build.crm.repository.ContractorBidRepository;

import static org.mockito.Mockito.description;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ContractorBidServiceImpl implements ContractorBidService {
    
    private final ContractorBidRepository contractorBidRepository;
    
    public ContractorBidServiceImpl(ContractorBidRepository contractorBidRepository) {
        this.contractorBidRepository = contractorBidRepository;
    }
    
    // ===== BASIC CRUD OPERATIONS =====
    
    @Override
    public ContractorBid save(ContractorBid bid) {
        return contractorBidRepository.save(bid);
    }
    
    @Override
    public Optional<ContractorBid> findById(Long id) {
        return contractorBidRepository.findById(id);
    }
    
    @Override
    public List<ContractorBid> findAll() {
        return contractorBidRepository.findAll();
    }
    
    @Override
    public void delete(ContractorBid bid) {
        contractorBidRepository.delete(bid);
    }
    
    @Override
    public void deleteById(Long id) {
        contractorBidRepository.deleteById(id);
    }
    
    // ===== BID MANAGEMENT =====
    
    @Override
    public List<ContractorBid> createBidInvitations(Ticket ticket, List<Customer> contractors, Long createdBy) {
        List<ContractorBid> invitations = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (Customer contractor : contractors) {
            // Check if bid already exists for this ticket and contractor
            Optional<ContractorBid> existingBid = contractorBidRepository
                .findByTicketAndContractor(ticket, contractor);
                
            if (existingBid.isEmpty()) {
                ContractorBid bid = new ContractorBid();
                bid.setTicket(ticket);
                bid.setContractor(contractor);
                bid.setStatus("invited");
                bid.setInvitedAt(now);
                bid.setCreatedAt(now);
                bid.setUpdatedAt(now);
                bid.setCreatedBy(createdBy);
                
                // Set priority level based on ticket urgency
                if (ticket.isEmergencyTicket()) {
                    bid.setPriorityLevel("emergency");
                } else if ("urgent".equals(ticket.getPriority()) || "critical".equals(ticket.getPriority())) {
                    bid.setPriorityLevel("express");
                } else {
                    bid.setPriorityLevel("standard");
                }
                
                ContractorBid savedBid = contractorBidRepository.save(bid);
                invitations.add(savedBid);
            }
        }
        
        return invitations;
    }
    
    @Override
    public ContractorBid submitBid(Long bidId, BigDecimal amount, String description, 
                                  Integer estimatedHours, LocalDateTime estimatedStart) {
        Optional<ContractorBid> bidOpt = contractorBidRepository.findById(bidId);
        if (bidOpt.isEmpty()) {
            throw new RuntimeException("Bid not found with id: " + bidId);
        }
        
        ContractorBid bid = bidOpt.get();
        
        // Validate bid can be submitted
        if (!"invited".equals(bid.getStatus())) {
            throw new RuntimeException("Bid cannot be submitted - current status: " + bid.getStatus());
        }
        
        // Update bid details
        bid.setBidAmount(amount);
        bid.setBidDescription(description);
        bid.setEstimatedCompletionHours(estimatedHours);
        bid.setEstimatedStartDate(estimatedStart);
        bid.setStatus("submitted");
        bid.setSubmittedAt(LocalDateTime.now());
        bid.setUpdatedAt(LocalDateTime.now());
        
        return contractorBidRepository.save(bid);
    }
    
    @Override
    public ContractorBid acceptBid(Long bidId, Long reviewedBy) {
        Optional<ContractorBid> bidOpt = contractorBidRepository.findById(bidId);
        if (bidOpt.isEmpty()) {
            throw new RuntimeException("Bid not found with id: " + bidId);
        }
        
        ContractorBid bid = bidOpt.get();
        
        // Validate bid can be accepted
        if (!"submitted".equals(bid.getStatus()) && !"under-review".equals(bid.getStatus())) {
            throw new RuntimeException("Bid cannot be accepted - current status: " + bid.getStatus());
        }
        
        LocalDateTime now = LocalDateTime.now();
        
    // Update bid details
    bid.setBidAmount(amount);
    bid.setBidDescription(description);
    bid.setEstimatedCompletionHours(estimatedHours);
    // Remove this line - setEstimatedStartDate doesn't exist
    // bid.setEstimatedStartDate(estimatedStart);
    bid.setStatus("submitted");
    bid.setSubmittedAt(LocalDateTime.now());
    bid.setUpdatedAt(LocalDateTime.now());

    return contractorBidRepository.save(bid);
    }

    @Override
    public ContractorBid acceptBid(Long bidId, Long reviewedBy) {
        Optional<ContractorBid> bidOpt = contractorBidRepository.findById(bidId);
        if (bidOpt.isEmpty()) {
            throw new RuntimeException("Bid not found with id: " + bidId);
        }
        
        ContractorBid bid = bidOpt.get();
        
        // Validate bid can be accepted
        if (!"submitted".equals(bid.getStatus()) && !"under-review".equals(bid.getStatus())) {
            throw new RuntimeException("Bid cannot be accepted - current status: " + bid.getStatus());
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // Accept this bid
        bid.setStatus("accepted");
        bid.setAcceptedAt(now);
        bid.setReviewedAt(now);
        // Remove this line - setReviewedBy doesn't exist
        // bid.setReviewedBy(reviewedBy);
        bid.setUpdatedAt(now);
        
        ContractorBid acceptedBid = contractorBidRepository.save(bid);
        
        // Reject all other bids for the same ticket
        List<ContractorBid> otherBids = contractorBidRepository.findByTicketTicketIdAndStatus(
            bid.getTicket().getTicketId(), "submitted");
        otherBids.addAll(contractorBidRepository.findByTicketTicketIdAndStatus(
            bid.getTicket().getTicketId(), "under-review"));
            
        for (ContractorBid otherBid : otherBids) {
            if (!otherBid.getId().equals(bidId)) {
                otherBid.setStatus("rejected");
                otherBid.setRejectedAt(now);
                otherBid.setReviewedAt(now);
                // Remove this line - setReviewedBy doesn't exist
                // otherBid.setReviewedBy(reviewedBy);
                otherBid.setUpdatedAt(now);
                otherBid.setAdminNotes("Automatically rejected - another bid was accepted");
                contractorBidRepository.save(otherBid);
            }
        }
        
        return acceptedBid;
    }

    @Override
    public ContractorBid rejectBid(Long bidId, Long reviewedBy, String reason) {
        Optional<ContractorBid> bidOpt = contractorBidRepository.findById(bidId);
        if (bidOpt.isEmpty()) {
            throw new RuntimeException("Bid not found with id: " + bidId);
        }
        
        ContractorBid bid = bidOpt.get();
        
        // Validate bid can be rejected
        if (!"submitted".equals(bid.getStatus()) && !"under-review".equals(bid.getStatus())) {
            throw new RuntimeException("Bid cannot be rejected - current status: " + bid.getStatus());
        }
        
        LocalDateTime now = LocalDateTime.now();
        bid.setStatus("rejected");
        bid.setRejectedAt(now);
        bid.setReviewedAt(now);
        // Remove this line - setReviewedBy doesn't exist
        // bid.setReviewedBy(reviewedBy);
        bid.setUpdatedAt(now);
        
        if (reason != null && !reason.trim().isEmpty()) {
            bid.setAdminNotes(reason);
        }
        
        return contractorBidRepository.save(bid);
    }

    @Override
    public ContractorBid withdrawBid(Long bidId) {
        Optional<ContractorBid> bidOpt = contractorBidRepository.findById(bidId);
        if (bidOpt.isEmpty()) {
            throw new RuntimeException("Bid not found with id: " + bidId);
        }
        
        ContractorBid bid = bidOpt.get();
        
        // Validate bid can be withdrawn
        if (!"invited".equals(bid.getStatus()) && !"submitted".equals(bid.getStatus())) {
            throw new RuntimeException("Bid cannot be withdrawn - current status: " + bid.getStatus());
        }
        
        bid.setStatus("withdrawn");
        bid.setUpdatedAt(LocalDateTime.now());
        
        return contractorBidRepository.save(bid);
    }

    // ===== SEARCH AND RETRIEVAL =====

    @Override
    public List<ContractorBid> findBidsForTicket(Integer ticketId) {
        return contractorBidRepository.findByTicketTicketId(ticketId);
    }

    @Override
    public List<ContractorBid> findBidsForContractor(Integer contractorId) {
        return contractorBidRepository.findByContractorCustomerId(contractorId);
    }

    @Override
    public Optional<ContractorBid> findBidByTicketAndContractor(Integer ticketId, Integer contractorId) {
        return contractorBidRepository.findByTicketTicketIdAndContractorCustomerId(ticketId, contractorId);
    }

    @Override
    public List<ContractorBid> findBidsByStatus(String status) {
        return contractorBidRepository.findByStatus(status);
    }

    @Override
    public List<ContractorBid> findPendingBids() {
        return contractorBidRepository.findPendingBids();
    }

    @Override
    public List<ContractorBid> findSubmittedBidsForTicket(Integer ticketId) {
        return contractorBidRepository.findSubmittedBidsForTicket(ticketId);
    }

    @Override
    public Optional<ContractorBid> findAcceptedBidForTicket(Integer ticketId) {
        List<ContractorBid> acceptedBids = contractorBidRepository.findByTicketTicketIdAndStatus(ticketId, "accepted");
        return acceptedBids.isEmpty() ? Optional.empty() : Optional.of(acceptedBids.get(0));
        
        // ===== ANALYTICS AND REPORTING =====
        
        @Override
        public long countBidsForTicket(Integer ticketId) {
            return contractorBidRepository.countByTicketTicketId(ticketId);
        }
        
        @Override
        public long countBidsForContractor(Integer contractorId) {
            return contractorBidRepository.countByContractorCustomerId(contractorId);
        }
        
        @Override
        public BigDecimal getAverageBidAmountForTicket(Integer ticketId) {
            BigDecimal average = contractorBidRepository.getAverageBidAmountForTicket(ticketId);
            return average != null ? average : BigDecimal.ZERO;
        }
        
        @Override
        public List<ContractorBid> getLowestBidsForTicket(Integer ticketId, int limit) {
            Pageable pageable = PageRequest.of(0, limit);
            return contractorBidRepository.findLowestBidsForTicket(ticketId, pageable);
        }
        
        @Override
        public List<ContractorBid> getHighestBidsForTicket(Integer ticketId, int limit) {
            Pageable pageable = PageRequest.of(0, limit);
            return contractorBidRepository.findHighestBidsForTicket(ticketId, pageable);
        }
    
    // ===== CONTRACTOR PERFORMANCE =====
    
    @Override
    public double getContractorWinRate(Integer contractorId) {
        Object[] result = contractorBidRepository.getContractorBidWinRate(contractorId);
        if (result != null && result.length == 2) {
            Long wonBids = (Long) result[0];
            Long totalBids = (Long) result[1];
            
            if (totalBids != null && totalBids > 0) {
                return (wonBids != null ? wonBids.doubleValue() : 0.0) / totalBids.doubleValue() * 100.0;
            }
        }
        return 0.0;
    }
    
    @Override
    public BigDecimal getContractorAverageBidAmount(Integer contractorId) {
        BigDecimal average = contractorBidRepository.getContractorAverageBidAmount(contractorId);
        return average != null ? average : BigDecimal.ZERO;
    }
    
    @Override
    public List<ContractorBid> getContractorRecentBids(Integer contractorId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return contractorBidRepository.findByContractorCustomerIdOrderByCreatedAtDesc(contractorId, pageable);
    }
    
    // ===== MAINTENANCE AND CLEANUP =====
    
    @Override
    public List<ContractorBid> findOverdueBidResponses(int daysOverdue) {
        LocalDateTime deadline = LocalDateTime.now().minusDays(daysOverdue);
        return contractorBidRepository.findOverdueBidResponses(deadline);
    }
    
    @Override
    public List<ContractorBid> findBidsNeedingFollowUp(int daysAgo) {
        LocalDateTime followUpDate = LocalDateTime.now().minusDays(daysAgo);
        return contractorBidRepository.findBidsNeedingFollowUp(followUpDate);
    }
    
    @Override
    public void cleanupBidsForTicket(Integer ticketId) {
        contractorBidRepository.deleteByTicketTicketId(ticketId);
    }
    
    @Override
    public void cleanupBidsForContractor(Integer contractorId) {
        contractorBidRepository.deleteByContractorCustomerId(contractorId);
    }
}