package com.example.wallet.module.deposit.dto;

import com.example.wallet.module.deposit.entity.DepositOrder;
import java.util.List;

public record DepositOrderPage(List<DepositOrder> items, String nextCursor, boolean hasMore) {
}
