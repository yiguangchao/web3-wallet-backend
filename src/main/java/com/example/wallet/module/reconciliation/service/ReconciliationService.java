package com.example.wallet.module.reconciliation.service;

import com.example.wallet.module.reconciliation.entity.ReconciliationDifference;
import java.util.List;

public interface ReconciliationService {
    Long run();
    List<ReconciliationDifference> listDifferences(String status);
    long countOpenDifferences();
}
