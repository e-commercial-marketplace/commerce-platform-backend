package com.marketplace.service.impl;

import com.marketplace.dto.response.UserSummaryResponse;
import com.marketplace.exception.ResourceNotFoundException;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public UserSummaryResponse getProfile(UUID userId) {
    return userRepository
        .findById(userId)
        .map(UserSummaryResponse::from)
        .orElseThrow(() -> new ResourceNotFoundException("User", userId));
  }
}
