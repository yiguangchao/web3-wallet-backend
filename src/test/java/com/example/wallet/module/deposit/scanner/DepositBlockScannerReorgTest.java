package com.example.wallet.module.deposit.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.example.wallet.infrastructure.redis.RedisDistributedLock;
import com.example.wallet.infrastructure.web3.Web3Properties;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.chain.entity.ChainBlockScanRecord;
import com.example.wallet.module.chain.entity.ChainScannedBlock;
import com.example.wallet.module.deposit.config.DepositScanProperties;
import com.example.wallet.module.wallet.mapper.CustodyDepositAddressMapper;
import java.math.BigInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;

@ExtendWith(MockitoExtension.class)
class DepositBlockScannerReorgTest {

    @Mock private Web3j web3j;
    @Mock private CustodyDepositAddressMapper addressMapper;
    @Mock private DepositScanPersistenceService persistenceService;
    @Mock private RedisDistributedLock lock;
    @Mock private SupportedAssetService assetService;

    private DepositBlockScanner scanner;

    @BeforeEach
    void setUp() {
        DepositScanProperties properties = new DepositScanProperties();
        properties.getScan().setInitialBlock(BigInteger.valueOf(90));
        properties.getScan().setReorgDepth(10);
        Web3Properties web3Properties = new Web3Properties();
        web3Properties.setChainId(11155111L);
        scanner = new DepositBlockScanner(
                web3j, addressMapper, properties, persistenceService, lock, assetService, web3Properties);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldWalkBackwardUntilStoredAndCanonicalHashesMatch() throws Exception {
        ChainBlockScanRecord record = new ChainBlockScanRecord();
        record.setLastScannedBlock(BigInteger.valueOf(105));
        when(persistenceService.findScannedBlock(BigInteger.valueOf(105)))
                .thenReturn(stored(105, "0x" + "1".repeat(64)));
        when(persistenceService.findScannedBlock(BigInteger.valueOf(104)))
                .thenReturn(stored(104, "0x" + "2".repeat(64)));
        String ancestorHash = "0x" + "3".repeat(64);
        when(persistenceService.findScannedBlock(BigInteger.valueOf(103)))
                .thenReturn(stored(103, ancestorHash));

        Request request = mock(Request.class);
        EthBlock response = mock(EthBlock.class);
        doReturn(request).when(web3j).ethGetBlockByNumber(any(), eq(false));
        when(request.send()).thenReturn(response);
        when(response.getBlock()).thenReturn(
                block(105, "0x" + "a".repeat(64)),
                block(104, "0x" + "b".repeat(64)),
                block(103, ancestorHash));

        ScannedBlock ancestor = scanner.findCommonAncestor(record);

        assertThat(ancestor.number()).isEqualTo(BigInteger.valueOf(103));
        assertThat(ancestor.hash()).isEqualTo(ancestorHash);
    }

    private ChainScannedBlock stored(long number, String hash) {
        ChainScannedBlock block = new ChainScannedBlock();
        block.setBlockNumber(BigInteger.valueOf(number));
        block.setBlockHash(hash);
        return block;
    }

    private EthBlock.Block block(long number, String hash) {
        EthBlock.Block block = new EthBlock.Block();
        block.setNumber("0x" + Long.toHexString(number));
        block.setHash(hash);
        block.setParentHash("0x" + "0".repeat(64));
        return block;
    }
}
