package com.auction.dto;

import com.auction.domain.AuctionStatus;
import com.auction.domain.Role;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class Dtos {

    private Dtos() {}

    // ---- requests ----

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "only letters, digits, . _ -") String username,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Size(min = 6, max = 100) String password) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record AuctionRequest(
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 2000) String description,
            @Size(max = 500) String imageUrl,
            @NotBlank @Size(max = 40) String category,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal startingPrice,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal minIncrement,
            @NotNull @Min(1) @Max(10080) Integer durationMinutes) {}

    public record BidRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount) {}

    // ---- responses ----

    public record UserDto(Long id, String username, String email, Role role, boolean enabled, Instant createdAt) {}

    public record AuthResponse(String token, UserDto user) {}

    public record AuctionDto(
            Long id,
            String title,
            String description,
            String imageUrl,
            String category,
            BigDecimal startingPrice,
            BigDecimal currentPrice,
            BigDecimal minIncrement,
            BigDecimal minNextBid,
            Instant startTime,
            Instant endTime,
            AuctionStatus status,
            Long sellerId,
            String sellerName,
            Long highestBidderId,
            String highestBidderName,
            int bidCount) {}

    public record BidDto(
            Long id,
            Long auctionId,
            String auctionTitle,
            Long bidderId,
            String bidderName,
            BigDecimal amount,
            Instant createdAt) {}

    /** Pushed to /topic/auctions/{id} and /topic/auctions over WebSocket. */
    public record AuctionEvent(String type, AuctionDto auction, BidDto bid, Long previousBidderId) {}

    public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    public record StatsDto(long activeAuctions, long totalBids, long totalUsers, Instant serverTime) {}

    public record ErrorResponse(int status, String message, java.util.Map<String, String> errors, Instant timestamp) {}
}
