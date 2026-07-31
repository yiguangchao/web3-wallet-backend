package com.example.wallet.signer.core;

import com.example.wallet.signer.api.SignRequest;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SigningPolicyService {
    private final JdbcTemplate jdbc;
    public SigningPolicyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KeyPolicy validateAndReserve(SignRequest request) {
        Boolean stopped = jdbc.queryForObject("SELECT emergency_stopped FROM signer_control WHERE id=1 FOR UPDATE", Boolean.class);
        if (Boolean.TRUE.equals(stopped)) throw new IllegalStateException("signer is emergency-stopped");
        KeyPolicy policy = jdbc.query("SELECT kms_key_version_name,expected_address,chain_id,single_value_limit,daily_value_limit,single_fee_limit FROM signer_key_config WHERE key_id=? AND status='ACTIVE' FOR UPDATE",
                rs -> rs.next() ? new KeyPolicy(rs.getString(1), rs.getString(2), rs.getLong(3),
                        rs.getBigDecimal(4).toBigIntegerExact(), rs.getBigDecimal(5).toBigIntegerExact(),
                        rs.getBigDecimal(6).toBigIntegerExact()) : null, request.keyId());
        if (policy == null) throw new IllegalArgumentException("active signing key not found");
        if (policy.chainId != request.chainId()) throw new IllegalArgumentException("chain id is not allowed");
        if (!policy.address.equalsIgnoreCase(request.expectedFromAddress())) throw new IllegalArgumentException("expected sender does not match policy");
        Transfer transfer = parseTransfer(request);
        BigInteger policyAmount;
        BigInteger dailyLimit;
        String assetKey;
        String beneficiary;
        if (transfer == null) {
            if (!"0x".equalsIgnoreCase(request.data()))
                throw new IllegalArgumentException("arbitrary contract calls are forbidden");
            policyAmount = request.value(); dailyLimit = policy.dailyValueLimit;
            assetKey = "NATIVE"; beneficiary = request.to();
            if (policyAmount.signum() < 0 || policyAmount.compareTo(policy.singleValueLimit) > 0)
                throw new IllegalArgumentException("native transaction value exceeds policy");
        } else {
            if (request.value().signum() != 0) throw new IllegalArgumentException("token transfer must have zero native value");
            TokenPolicy token = jdbc.query("SELECT single_raw_limit,daily_raw_limit FROM signer_token_policy WHERE key_id=? AND chain_id=? AND token_address=? AND status=1 FOR UPDATE",
                    rs -> rs.next() ? new TokenPolicy(rs.getBigDecimal(1).toBigIntegerExact(),
                            rs.getBigDecimal(2).toBigIntegerExact()) : null,
                    request.keyId(), request.chainId(), request.to().toLowerCase(Locale.ROOT));
            if (token == null || transfer.amount.compareTo(token.singleLimit) > 0)
                throw new IllegalArgumentException("token or transfer amount is not allowed");
            policyAmount = transfer.amount; dailyLimit = token.dailyLimit;
            assetKey = request.to().toLowerCase(Locale.ROOT); beneficiary = transfer.recipient;
        }
        BigInteger fee = request.gasLimit().multiply(request.maxFeePerGas());
        if (fee.signum() <= 0 || fee.compareTo(policy.singleFeeLimit) > 0)
            throw new IllegalArgumentException("transaction fee exceeds policy");
        int allowed = jdbc.queryForObject("SELECT COUNT(*) FROM signer_address_policy WHERE key_id=? AND chain_id=? AND to_address=? AND status=1",
                Integer.class, request.keyId(), request.chainId(), beneficiary.toLowerCase(Locale.ROOT));
        if (allowed != 1) throw new IllegalArgumentException("transaction destination is not allowlisted");
        LocalDate date = LocalDate.now(java.time.ZoneOffset.UTC);
        jdbc.update("INSERT IGNORE INTO signer_daily_usage VALUES(?,?,?,0,0,?)", request.keyId(), assetKey, date, LocalDateTime.now());
        BigInteger used = jdbc.queryForObject("SELECT total_value FROM signer_daily_usage WHERE key_id=? AND asset_key=? AND usage_date=? FOR UPDATE",
                java.math.BigDecimal.class, request.keyId(), assetKey, date).toBigIntegerExact();
        if (used.add(policyAmount).compareTo(dailyLimit) > 0)
            throw new IllegalArgumentException("daily signing value limit exceeded");
        jdbc.update("UPDATE signer_daily_usage SET total_value=total_value+?,transaction_count=transaction_count+1,updated_at=? WHERE key_id=? AND asset_key=? AND usage_date=?",
                policyAmount, LocalDateTime.now(), request.keyId(), assetKey, date);
        return policy;
    }

    private Transfer parseTransfer(SignRequest request) {
        String data = request.data().toLowerCase(Locale.ROOT);
        if (!data.startsWith("0xa9059cbb")) return null;
        if (data.length() != 138) throw new IllegalArgumentException("invalid ERC-20 transfer calldata");
        String recipient = "0x" + data.substring(34, 74);
        BigInteger amount = new BigInteger(data.substring(74, 138), 16);
        if (amount.signum() <= 0) throw new IllegalArgumentException("token transfer amount must be positive");
        return new Transfer(recipient, amount);
    }

    public record KeyPolicy(String kmsKeyVersionName, String address, long chainId,
                            BigInteger singleValueLimit, BigInteger dailyValueLimit,
                            BigInteger singleFeeLimit) {}
    private record TokenPolicy(BigInteger singleLimit, BigInteger dailyLimit) {}
    private record Transfer(String recipient, BigInteger amount) {}
}
