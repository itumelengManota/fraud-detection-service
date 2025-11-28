package com.twenty9ine.frauddetection.application.port;

import com.twenty9ine.frauddetection.domain.valueobject.MLPrediction;
import com.twenty9ine.frauddetection.domain.valueobject.Transaction;

public interface MLServicePort {
    MLPrediction predict(Transaction transaction);
}
