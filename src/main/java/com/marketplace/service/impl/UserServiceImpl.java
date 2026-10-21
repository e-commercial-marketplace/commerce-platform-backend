package com.marketplace.service.impl;

import com.marketplace.common.exception.ResourceNotFoundException;
import com.marketplace.dto.response.UserSummaryResponse;
import com.marketplace.mapper.UserMapper;
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
  private final UserMapper userMapper;

  @Override
  @Transactional(readOnly = true)
  public UserSummaryResponse getProfile(UUID userId) {
    return userRepository
        .findById(userId)
        .map(userMapper::toSummary)
        .orElseThrow(() -> new ResourceNotFoundException("User", userId));
  }
}
