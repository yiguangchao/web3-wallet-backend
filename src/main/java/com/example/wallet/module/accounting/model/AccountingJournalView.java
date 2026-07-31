package com.example.wallet.module.accounting.model;

import com.example.wallet.module.accounting.entity.AccountingEntry;
import com.example.wallet.module.accounting.entity.AccountingJournal;
import java.util.List;

public record AccountingJournalView(AccountingJournal journal, List<AccountingEntry> entries) {
}
