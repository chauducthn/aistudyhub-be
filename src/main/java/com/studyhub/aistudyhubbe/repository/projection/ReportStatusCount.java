package com.studyhub.aistudyhubbe.repository.projection;

import com.studyhub.aistudyhubbe.entity.ReportStatus;

public interface ReportStatusCount {

    ReportStatus getStatus();

    long getTotal();
}
