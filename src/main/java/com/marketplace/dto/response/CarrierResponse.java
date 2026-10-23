package com.marketplace.dto.response;

import java.util.UUID;

/** Một đơn vị vận chuyển; seller chỉ nhận các đơn vị có {@code active = true}. */
public record CarrierResponse(UUID id, String name, boolean active) {}
