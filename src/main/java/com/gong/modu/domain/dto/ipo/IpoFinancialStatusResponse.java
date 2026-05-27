package com.gong.modu.domain.dto.ipo;

import com.gong.modu.domain.enums.ipo.FinancialDataUnavailableReason;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class IpoFinancialStatusResponse {
    private List<IpoFinancialResponse> financials;
    private boolean available;
    private FinancialDataUnavailableReason unavailableReason;
    private String message;
}
