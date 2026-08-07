package com.example.wallet.module.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.accounting.entity.AccountingEntry;
import com.example.wallet.module.accounting.entity.AccountingJournal;
import com.example.wallet.module.accounting.mapper.AccountingEntryMapper;
import com.example.wallet.module.accounting.mapper.AccountingJournalMapper;
import com.example.wallet.module.accounting.model.AccountingJournalView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountingQueryServiceTest {

    @Mock private AccountingJournalMapper journalMapper;
    @Mock private AccountingEntryMapper entryMapper;

    private AccountingQueryService service;

    @BeforeEach
    void setUp() {
        service = new AccountingQueryService(journalMapper, entryMapper);
    }

    @Test
    void shouldLoadJournalAndEntriesByBusinessIdentity() {
        AccountingJournal journal = journal(901L, 701L);
        List<AccountingEntry> entries = List.of(
                entry(1L, "USER_AVAILABLE"),
                entry(2L, "USER_FROZEN"),
                entry(3L, "SYSTEM_CLEARING"));
        when(journalMapper.selectByBusiness("DEPOSIT", 501L)).thenReturn(journal);
        when(entryMapper.selectList(any(Wrapper.class))).thenReturn(entries);

        AccountingJournalView result = service.getByBusiness("DEPOSIT", 501L);

        assertThat(result.journal()).isSameAs(journal);
        assertThat(result.entries()).containsExactlyElementsOf(entries);
        verify(journalMapper).selectByBusiness("DEPOSIT", 501L);
    }

    @Test
    void shouldLoadJournalByImmutableSourceFlowIdentity() {
        AccountingJournal journal = journal(901L, 701L);
        List<AccountingEntry> entries = List.of(entry(1L, "USER_AVAILABLE"));
        when(journalMapper.selectBySourceFlowId(701L)).thenReturn(journal);
        when(entryMapper.selectList(any(Wrapper.class))).thenReturn(entries);

        AccountingJournalView result = service.getBySourceFlowId(701L);

        assertThat(result.journal().getSourceFlowId()).isEqualTo(701L);
        assertThat(result.entries()).containsExactlyElementsOf(entries);
        verify(journalMapper).selectBySourceFlowId(701L);
    }

    @Test
    void shouldRejectUnknownSourceFlow() {
        when(journalMapper.selectBySourceFlowId(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getBySourceFlowId(999L))
                .isInstanceOf(BizException.class)
                .hasMessage("accounting journal not found");
    }

    @Test
    void shouldExposeDetectedJournalImbalanceCount() {
        when(journalMapper.countImbalancedJournals()).thenReturn(3L);

        assertThat(service.countImbalances()).isEqualTo(3L);
    }

    private AccountingJournal journal(long id, long sourceFlowId) {
        AccountingJournal journal = new AccountingJournal();
        journal.setId(id);
        journal.setSourceFlowId(sourceFlowId);
        journal.setBusinessType("DEPOSIT");
        journal.setBusinessId(501L);
        return journal;
    }

    private AccountingEntry entry(long id, String accountCode) {
        AccountingEntry entry = new AccountingEntry();
        entry.setId(id);
        entry.setJournalId(901L);
        entry.setAccountCode(accountCode);
        return entry;
    }
}
