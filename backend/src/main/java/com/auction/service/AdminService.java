package com.auction.service;

import com.auction.domain.Role;
import com.auction.domain.User;
import com.auction.dto.Dtos.PageDto;
import com.auction.dto.Dtos.UserDto;
import com.auction.dto.Mappers;
import com.auction.exception.ApiException;
import com.auction.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository users;

    public AdminService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageDto<UserDto> listUsers(int page, int size) {
        Page<User> p = users.findAll(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("id")));
        return new PageDto<>(
                p.getContent().stream().map(Mappers::toDto).toList(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages());
    }

    @Transactional
    public UserDto setEnabled(Long targetId, Long actingAdminId, boolean enabled) {
        if (targetId.equals(actingAdminId)) {
            throw ApiException.badRequest("You cannot change your own status");
        }
        User u = users.findById(targetId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (u.getRole() == Role.ADMIN) {
            throw ApiException.forbidden("Admin accounts cannot be disabled");
        }
        u.setEnabled(enabled);
        return Mappers.toDto(u);
    }
}
