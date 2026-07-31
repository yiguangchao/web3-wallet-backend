package com.example.wallet.module.accounting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.accounting.entity.AccountingEntry;
import com.example.wallet.module.accounting.entity.AccountingJournal;
import com.example.wallet.module.accounting.mapper.AccountingEntryMapper;
import com.example.wallet.module.accounting.mapper.AccountingJournalMapper;
import com.example.wallet.module.accounting.model.AccountingJournalView;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AccountingQueryService {
    private final AccountingJournalMapper journalMapper;
    private final AccountingEntryMapper entryMapper;

    public AccountingQueryService(AccountingJournalMapper journalMapper,
                                  AccountingEntryMapper entryMapper) {
        this.journalMapper = journalMapper;
        this.entryMapper = entryMapper;
    }

    public AccountingJournalView getByBusiness(String businessType, Long businessId) {
        AccountingJournal journal = journalMapper.selectByBusiness(businessType, businessId);
        if (journal == null) {
            throw new BizException("accounting journal not found");
        }
        List<AccountingEntry> entries = entryMapper.selectList(
                new LambdaQueryWrapper<AccountingEntry>()
                        .eq(AccountingEntry::getJournalId, journal.getId())
                        .orderByAsc(AccountingEntry::getId));
        return new AccountingJournalView(journal, entries);
    }

    public long countImbalances() {
        return journalMapper.countImbalancedJournals();
    }
}
