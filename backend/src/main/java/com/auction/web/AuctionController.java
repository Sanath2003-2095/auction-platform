package com.auction.web;

import com.auction.dto.Dtos.AuctionDto;
import com.auction.dto.Dtos.AuctionRequest;
import com.auction.dto.Dtos.BidDto;
import com.auction.dto.Dtos.BidRequest;
import com.auction.dto.Dtos.PageDto;
import com.auction.security.UserPrincipal;
import com.auction.service.AuctionService;
import com.auction.service.BidProcessor;
import com.auction.service.BidService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auctions")
public class AuctionController {

    private final AuctionService auctions;
    private final BidService bids;

    public AuctionController(AuctionService auctions, BidService bids) {
        this.auctions = auctions;
        this.bids = bids;
    }

    @GetMapping
    public PageDto<AuctionDto> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return auctions.list(status, q, category, sort, page, size);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return auctions.categories();
    }

    @GetMapping("/{id}")
    public AuctionDto get(@PathVariable Long id) {
        return auctions.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuctionDto create(@Valid @RequestBody AuctionRequest req, @AuthenticationPrincipal UserPrincipal user) {
        return auctions.create(req, user.id());
    }

    @DeleteMapping("/{id}")
    public AuctionDto cancel(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal user) {
        return auctions.cancel(id, user);
    }

    @GetMapping("/{id}/bids")
    public PageDto<BidDto> history(
            @PathVariable Long id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return bids.historyForAuction(id, page, size);
    }

    @PostMapping("/{id}/bids")
    @ResponseStatus(HttpStatus.CREATED)
    public BidDto placeBid(
            @PathVariable Long id, @Valid @RequestBody BidRequest req, @AuthenticationPrincipal UserPrincipal user) {
        BidProcessor.Result result = bids.placeBid(id, user.id(), req.amount());
        return result.bid();
    }
}
