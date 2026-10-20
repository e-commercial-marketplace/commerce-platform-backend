package com.marketplace.repository;

import com.marketplace.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository
    extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

  /**
   * Tìm theo specification và nạp sẵn category, seller trong cùng câu query để mapper đọc được mà
   * không phát sinh thêm query cho từng dòng (N+1).
   */
  @Override
  @EntityGraph(attributePaths = {"category", "seller"})
  Page<Product> findAll(Specification<Product> spec, Pageable pageable);

  @EntityGraph(attributePaths = {"category", "seller"})
  Optional<Product> findByIdAndActiveTrue(UUID id);

  /** Category còn product nào không (kể cả product đã ẩn), dùng để chặn xoá category. */
  boolean existsByCategoryId(UUID categoryId);
}
