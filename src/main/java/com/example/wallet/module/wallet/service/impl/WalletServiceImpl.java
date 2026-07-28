package com.example.wallet.module.wallet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.custody.CustodyKeyService;
import com.example.wallet.infrastructure.custody.CustodyWalletProperties;
import com.example.wallet.infrastructure.custody.DerivedCustodyAddress;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.user.entity.SysUser;
import com.example.wallet.module.user.mapper.SysUserMapper;
import com.example.wallet.module.wallet.dto.AllocateDepositAddressRequest;
import com.example.wallet.module.wallet.dto.DepositAddressResponse;
import com.example.wallet.module.wallet.dto.Erc20BalanceRequest;
import com.example.wallet.module.wallet.entity.CustodyDepositAddress;
import com.example.wallet.module.wallet.entity.CustodyDepositAddressStatus;
import com.example.wallet.module.wallet.mapper.CustodyDepositAddressMapper;
import com.example.wallet.module.wallet.mapper.CustodyHdSequenceMapper;
import com.example.wallet.module.wallet.service.WalletService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletServiceImpl implements WalletService {

    private static final String PLATFORM_CUSTODY = "PLATFORM_CUSTODY";
    private static final String DEPOSIT_ADDRESS = "DEPOSIT";

    private final CustodyDepositAddressMapper depositAddressMapper;
    private final CustodyHdSequenceMapper sequenceMapper;
    private final SysUserMapper userMapper;
    private final CustodyKeyService custodyKeyService;
    private final CustodyWalletProperties custodyProperties;
    private final Web3Service web3Service;

    public WalletServiceImpl(CustodyDepositAddressMapper depositAddressMapper,
                             CustodyHdSequenceMapper sequenceMapper,
                             SysUserMapper userMapper,
                             CustodyKeyService custodyKeyService,
                             CustodyWalletProperties custodyProperties,
                             Web3Service web3Service) {
        this.depositAddressMapper = depositAddressMapper;
        this.sequenceMapper = sequenceMapper;
        this.userMapper = userMapper;
        this.custodyKeyService = custodyKeyService;
        this.custodyProperties = custodyProperties;
        this.web3Service = web3Service;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepositAddressResponse allocateDepositAddress(Long userId, AllocateDepositAddressRequest request) {
        requireCustodyEnabled();
        String chain = normalizeAndValidateChain(request.getChain());
        SysUser user = userMapper.selectByIdForUpdate(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BizException("active user not found");
        }

        CustodyDepositAddress existing = depositAddressMapper.selectOne(
                new LambdaQueryWrapper<CustodyDepositAddress>()
                        .eq(CustodyDepositAddress::getUserId, userId)
                        .eq(CustodyDepositAddress::getChain, chain)
                        .eq(CustodyDepositAddress::getStatus, CustodyDepositAddressStatus.ACTIVE.getCode()));
        if (existing != null) {
            return toResponse(existing);
        }

        String keyVersion = custodyProperties.getActiveKeyVersion();
        sequenceMapper.ensureSequence(chain, keyVersion);
        Long index = sequenceMapper.selectNextIndexForUpdate(chain, keyVersion);
        if (index == null) {
            throw new BizException("custody derivation sequence is unavailable");
        }
        DerivedCustodyAddress derived = custodyKeyService.deriveAddress(keyVersion, index);

        LocalDateTime now = LocalDateTime.now();
        CustodyDepositAddress address = new CustodyDepositAddress();
        address.setUserId(userId);
        address.setChain(chain);
        address.setAddress(derived.address().toLowerCase(Locale.ROOT));
        address.setCustodyType(PLATFORM_CUSTODY);
        address.setAddressType(DEPOSIT_ADDRESS);
        address.setKeyVersion(derived.keyVersion());
        address.setDerivationIndex(derived.derivationIndex());
        address.setDerivationPath(derived.derivationPath());
        address.setStatus(CustodyDepositAddressStatus.ACTIVE.getCode());
        address.setAssignedAt(now);
        address.setCreatedAt(now);
        address.setUpdatedAt(now);
        if (depositAddressMapper.insert(address) != 1) {
            throw new BizException("custody deposit address creation failed");
        }
        if (sequenceMapper.advance(chain, keyVersion, index) != 1) {
            throw new BizException("custody derivation sequence update failed");
        }
        return toResponse(address);
    }

    @Override
    public List<DepositAddressResponse> listDepositAddresses(Long userId) {
        return depositAddressMapper.selectList(new LambdaQueryWrapper<CustodyDepositAddress>()
                        .eq(CustodyDepositAddress::getUserId, userId)
                        .orderByDesc(CustodyDepositAddress::getCreatedAt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepositAddressResponse updateDepositAddressStatus(Long addressId,
                                                             CustodyDepositAddressStatus status) {
        CustodyDepositAddress found = depositAddressMapper.selectById(addressId);
        if (found == null) {
            throw new BizException("custody deposit address not found");
        }
        userMapper.selectByIdForUpdate(found.getUserId());
        CustodyDepositAddress address = depositAddressMapper.selectByIdForUpdate(addressId);
        if (address == null) {
            throw new BizException("custody deposit address not found");
        }
        CustodyDepositAddressStatus currentStatus = CustodyDepositAddressStatus.fromCode(address.getStatus());
        if (currentStatus == CustodyDepositAddressStatus.RETIRED
                && status != CustodyDepositAddressStatus.RETIRED) {
            throw new BizException("retired custody address is terminal");
        }
        if (currentStatus == status) {
            return toResponse(address);
        }
        if (status == CustodyDepositAddressStatus.ACTIVE) {
            Long activeCount = depositAddressMapper.selectCount(
                    new LambdaQueryWrapper<CustodyDepositAddress>()
                            .eq(CustodyDepositAddress::getUserId, address.getUserId())
                            .eq(CustodyDepositAddress::getChain, address.getChain())
                            .eq(CustodyDepositAddress::getStatus, CustodyDepositAddressStatus.ACTIVE.getCode())
                            .ne(CustodyDepositAddress::getId, addressId));
            if (activeCount > 0) {
                throw new BizException("user already has an active deposit address for this chain");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime disabledAt = status == CustodyDepositAddressStatus.ACTIVE ? null : now;
        if (depositAddressMapper.updateStatusIfCurrent(
                addressId, currentStatus.getCode(), status.getCode(), disabledAt, now) != 1) {
            throw new BizException("custody address status changed concurrently");
        }
        address.setStatus(status.getCode());
        address.setDisabledAt(disabledAt);
        address.setUpdatedAt(now);
        return toResponse(address);
    }

    @Override
    public BigDecimal getEthBalance(String address) {
        return web3Service.getEthBalance(address);
    }

    @Override
    public BigDecimal getErc20Balance(Erc20BalanceRequest request) {
        return web3Service.getErc20Balance(request.getWalletAddress(), request.getTokenAddress(), request.getDecimals());
    }

    private void requireCustodyEnabled() {
        if (!custodyProperties.isEnabled()) {
            throw new BizException("custody wallet is disabled");
        }
    }

    private String normalizeAndValidateChain(String requestedChain) {
        String chain = requestedChain.trim().toUpperCase(Locale.ROOT);
        if (!chain.equalsIgnoreCase(custodyProperties.getChain())) {
            throw new BizException("custody chain is not supported");
        }
        return chain;
    }

    private DepositAddressResponse toResponse(CustodyDepositAddress address) {
        return new DepositAddressResponse(
                address.getId(),
                address.getChain(),
                address.getAddress(),
                CustodyDepositAddressStatus.fromCode(address.getStatus()).name(),
                address.getAssignedAt());
    }
}
