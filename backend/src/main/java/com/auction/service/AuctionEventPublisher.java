package com.auction.service;

import com.auction.dto.Dtos.AuctionDto;
import com.auction.dto.Dtos.AuctionEvent;
import com.auction.dto.Dtos.BidDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuctionEventPublisher {

    private final SimpMessagingTemplate messaging;

    public AuctionEventPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void bidPlaced(AuctionDto auction, BidDto bid, Long previousBidderId) {
        publish(new AuctionEvent("BID", auction, bid, previousBidderId));
    }

    public void created(AuctionDto auction) {
        publish(new AuctionEvent("CREATED", auction, null, null));
    }

    public void closed(AuctionDto auction) {
        publish(new AuctionEvent("CLOSED", auction, null, null));
    }

    public void cancelled(AuctionDto auction) {
        publish(new AuctionEvent("CANCELLED", auction, null, null));
    }

    private void publish(AuctionEvent event) {
        messaging.convertAndSend("/topic/auctions/" + event.auction().id(), event);
        messaging.convertAndSend("/topic/auctions", event);
    }
}
