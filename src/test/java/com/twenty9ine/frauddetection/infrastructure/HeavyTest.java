package com.twenty9ine.frauddetection.infrastructure;

import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Order;

@Order(3)  // Heavy tests last
public interface HeavyTest {}