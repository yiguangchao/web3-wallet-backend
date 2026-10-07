package com.example.wallet.module.asset.dto;

import com.example.wallet.module.asset.entity.AssetFlow;
import java.util.List;

public record AssetFlowPage(List<AssetFlow> items, String nextCursor, boolean hasMore) {
}
