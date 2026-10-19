package com.auction.service;

import com.auction.domain.Auction;
import com.auction.domain.AuctionStatus;
import com.auction.domain.User;
import com.auction.dto.Dtos.AuctionDto;
import com.auction.dto.Dtos.AuctionRequest;
import com.auction.dto.Dtos.PageDto;
import com.auction.dto.Mappers;
import com.auction.exception.ApiException;
import com.auction.repository.AuctionRepository;
import com.auction.repository.UserRepository;
import com.auction.security.UserPrincipal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuctionService {

    private final AuctionRepository auctions;
    private final UserRepository users;
    private final AuctionEventPublisher events;

    public AuctionService(AuctionRepository auctions, UserRepository users, AuctionEventPublisher events) {
        this.auctions = auctions;
        this.users = users;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageDto<AuctionDto> list(String status, String q, String category, String sort, int page, int size) {
        AuctionStatus st = parseStatus(status);
        Specification<Auction> spec =
                Specification.where(AuctionSpecs.status(st)).and(AuctionSpecs.category(category)).and(AuctionSpecs.search(q));
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), sortFor(sort, st));
        return toPage(auctions.findAll(spec, pr));
    }

    @Transactional(readOnly = true)
    public AuctionDto get(Long id) {
        return Mappers.toDto(auctions.findById(id).orElseThrow(() -> ApiException.notFound("Auction not found")));
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return auctions.findDistinctCategories();
    }

    @Transactional(readOnly = true)
    public PageDto<AuctionDto> mine(Long userId, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "id"));
        return toPage(auctions.findBySellerId(userId, pr));
    }

    @Transactional
    public AuctionDto create(AuctionRequest req, Long sellerId) {
        User seller = users.findById(sellerId).orElseThrow(() -> ApiException.notFound("User not found"));
        AuctionDto dto = Mappers.toDto(auctions.save(build(req, seller)));
        events.created(dto);
        return dto;
    }

    /** Package-visible so the demo seeder can create auctions without going through HTTP. */
    Auction build(AuctionRequest req, User seller) {
        Instant now = Instant.now();
        Auction a = new Auction();
        a.setTitle(req.title().trim());
        a.setDescription(req.description().trim());
        a.setImageUrl(req.imageUrl() == null || req.imageUrl().isBlank() ? null : req.imageUrl().trim());
        a.setCategory(req.category().trim());
        a.setStartingPrice(req.startingPrice());
        a.setCurrentPrice(req.startingPrice());
        a.setMinIncrement(req.minIncrement());
        a.setStartTime(now);
        a.setEndTime(now.plus(req.durationMinutes(), ChronoUnit.MINUTES));
        a.setStatus(AuctionStatus.ACTIVE);
        a.setSeller(seller);
        return a;
    }

    @Transactional
    public AuctionDto cancel(Long id, UserPrincipal principal) {
        Auction a = auctions.findById(id).orElseThrow(() -> ApiException.notFound("Auction not found"));
        boolean owner = a.getSeller().getId().equals(principal.id());
        if (!owner && !principal.isAdmin()) {
            throw ApiException.forbidden("Only the seller can cancel this auction");
        }
        if (a.getStatus() != AuctionStatus.ACTIVE) {
            throw ApiException.badRequest("Only active auctions can be cancelled");
        }
        if (a.getBidCount() > 0 && !principal.isAdmin()) {
            throw ApiException.badRequest("An auction that already has bids cannot be cancelled");
        }
        a.setStatus(AuctionStatus.CANCELLED);
        AuctionDto dto = Mappers.toDto(auctions.saveAndFlush(a));
        events.cancelled(dto);
        return dto;
    }

    private static AuctionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return AuctionStatus.ACTIVE;
        }
        if (status.equalsIgnoreCase("ALL")) {
            return null;
        }
        try {
            return AuctionStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown status: " + status);
        }
    }

    private static Sort sortFor(String sort, AuctionStatus status) {
        String s = sort == null ? "ending" : sort;
        return switch (s) {
            case "newest" -> Sort.by(Sort.Direction.DESC, "id");
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "currentPrice");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "currentPrice");
            case "popular" -> Sort.by(Sort.Direction.DESC, "bidCount").and(Sort.by(Sort.Direction.ASC, "endTime"));
            default -> status == AuctionStatus.ACTIVE || status == null
                    ? Sort.by(Sort.Direction.ASC, "endTime")
                    : Sort.by(Sort.Direction.DESC, "endTime");
        };
    }

    private static PageDto<AuctionDto> toPage(Page<Auction> p) {
        return new PageDto<>(
                p.getContent().stream().map(Mappers::toDto).toList(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages());
    }
}
