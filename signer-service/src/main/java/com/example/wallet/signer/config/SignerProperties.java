package com.example.wallet.signer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("signer")
public class SignerProperties {
    private String walletServiceTokenHash;
    private String adminServiceTokenHash;
    private String walletSubjectPattern = "^CN=web3-wallet-backend(?:,.*)?$";
    private String adminSubjectPattern = "^CN=wallet-key-admin-[^,]+(?:,.*)?$";
    private long requestClockSkewSeconds = 60;
    private long processingAlertSeconds = 300;
    private long tokenPolicyApprovalTtlSeconds = 86400;
    private boolean production = true;
    public String getWalletServiceTokenHash() { return walletServiceTokenHash; }
    public void setWalletServiceTokenHash(String value) { this.walletServiceTokenHash = value; }
    public String getAdminServiceTokenHash() { return adminServiceTokenHash; }
    public void setAdminServiceTokenHash(String value) { this.adminServiceTokenHash = value; }
    public String getWalletSubjectPattern() { return walletSubjectPattern; }
    public void setWalletSubjectPattern(String value) { this.walletSubjectPattern = value; }
    public String getAdminSubjectPattern() { return adminSubjectPattern; }
    public void setAdminSubjectPattern(String value) { this.adminSubjectPattern = value; }
    public long getRequestClockSkewSeconds() { return requestClockSkewSeconds; }
    public void setRequestClockSkewSeconds(long value) { this.requestClockSkewSeconds = value; }
    public long getProcessingAlertSeconds() { return processingAlertSeconds; }
    public void setProcessingAlertSeconds(long value) { this.processingAlertSeconds = value; }
    public long getTokenPolicyApprovalTtlSeconds() { return tokenPolicyApprovalTtlSeconds; }
    public void setTokenPolicyApprovalTtlSeconds(long value) { this.tokenPolicyApprovalTtlSeconds = value; }
    public boolean isProduction() { return production; }
    public void setProduction(boolean value) { this.production = value; }
}
