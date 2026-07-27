package com.example.wallet.infrastructure.custody;

import java.math.BigDecimal;

public interface CustodyKeyService {

    DerivedCustodyAddress deriveAddress(String keyVersion, long derivationIndex);

    SweepBroadcastResult sweepEth(String keyVersion, long derivationIndex, String expectedFromAddress,
                                  String collectionAddress, BigDecimal minimumAmount, BigDecimal reserve);

    SweepBroadcastResult sweepErc20(String keyVersion, long derivationIndex, String expectedFromAddress,
                                    String tokenAddress, Integer decimals, String collectionAddress);
}
