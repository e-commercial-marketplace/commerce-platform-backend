package com.marketplace.service.impl;

import com.marketplace.common.exception.BusinessException;
import com.marketplace.dto.request.CarrierCreateRequest;
import com.marketplace.dto.request.CarrierUpdateRequest;
import com.marketplace.dto.response.CarrierResponse;
import com.marketplace.entity.Carrier;
import com.marketplace.mapper.CarrierMapper;
import com.marketplace.repository.CarrierRepository;
import com.marketplace.repository.UserRepository;
import com.marketplace.service.CarrierService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quản lý đơn vị vận chuyển theo cùng cách với category: tên duy nhất, không phân biệt hoa thường. */
@Service
@RequiredArgsConstructor
public class CarrierServiceImpl implements CarrierService {

  private final CarrierRepository carrierRepository;
  private final UserRepository userRepository;
  private final CarrierMapper carrierMapper;

  @Override
  @Transactional(readOnly = true)
  public List<CarrierResponse> listAll() {
    return carrierMapper.toResponses(carrierRepository.findAllByOrderByNameAsc());
  }

  @Override
  @Transactional(readOnly = true)
  public List<CarrierResponse> listActive(UUID sellerId) {
    userRepository.getApprovedSeller(sellerId); // seller chưa được duyệt thì 403
    return carrierMapper.toResponses(carrierRepository.findByActiveTrueOrderByNameAsc());
  }

  @Override
  @Transactional
  public CarrierResponse create(CarrierCreateRequest request) {
    // Bước 1: bỏ khoảng trắng hai đầu, tên đã có (không phân biệt hoa thường) thì 400.
    String name = request.name().trim();
    if (carrierRepository.existsByNameIgnoreCase(name)) {
      throw duplicateName();
    }
    // Bước 2: lưu ngay (flush) để hai request tạo cùng tên một lúc bật lỗi unique tại đây.
    return save(Carrier.builder().name(name).active(true).build());
  }

  @Override
  @Transactional
  public CarrierResponse update(UUID id, CarrierUpdateRequest request) {
    Carrier carrier =
        carrierRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Carrier", id));
    if (request.name() != null) {
      String name = request.name().trim();
      // Đổi thành chính tên của nó thì được; trùng với đơn vị KHÁC thì 400.
      if (carrierRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
        throw duplicateName();
      }
      carrier.setName(name);
    }
    if (request.active() != null) {
      carrier.setActive(request.active());
    }
    return save(carrier);
  }

  private CarrierResponse save(Carrier carrier) {
    try {
      return carrierMapper.toResponse(carrierRepository.saveAndFlush(carrier));
    } catch (DataIntegrityViolationException e) {
      throw duplicateName(); // thua cuộc đua: request khác vừa dùng tên này
    }
  }

  private static BusinessException duplicateName() {
    return BusinessException.validation("Carrier name already exists");
  }
}
