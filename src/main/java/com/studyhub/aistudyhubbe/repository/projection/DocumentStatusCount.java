package com.studyhub.aistudyhubbe.repository.projection;

import com.studyhub.aistudyhubbe.entity.DocumentStatus;

public interface DocumentStatusCount {

    DocumentStatus getStatus();

    long getTotal();
}
