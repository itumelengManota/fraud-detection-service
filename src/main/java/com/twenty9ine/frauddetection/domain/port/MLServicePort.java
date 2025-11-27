package com.twenty9ine.frauddetection.domain.port;

import com.twenty9ine.frauddetection.domain.model.MLPrediction;
import com.twenty9ine.frauddetection.domain.model.Transaction;

public interface MLServicePort {
    MLPrediction predict(Transaction transaction);
}
