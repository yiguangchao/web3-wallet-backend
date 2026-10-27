package com.example.wallet.module.withdraw.dto;

import java.util.List;

public record WithdrawOrderPage(List<WithdrawOrderResponse> items, String nextCursor, boolean hasMore) {
}
