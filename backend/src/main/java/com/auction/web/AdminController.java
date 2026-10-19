package com.auction.web;

import com.auction.dto.Dtos.PageDto;
import com.auction.dto.Dtos.UserDto;
import com.auction.security.UserPrincipal;
import com.auction.service.AdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/users")
    public PageDto<UserDto> users(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return admin.listUsers(page, size);
    }

    @PatchMapping("/users/{id}/enabled")
    public UserDto setEnabled(
            @PathVariable Long id, @RequestParam boolean value, @AuthenticationPrincipal UserPrincipal acting) {
        return admin.setEnabled(id, acting.id(), value);
    }
}
