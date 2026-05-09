package com.kond.orders.service;

import com.kond.orders.dto.ShippingRateRequest;
import com.kond.orders.dto.ShippingRateResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Shipping rate calculator.
 * INTENTIONAL: This service has ZERO test coverage — a known rough edge.
 */
@Service
public class ShippingRateCalculator {

    private static final BigDecimal BASE_RATE_STANDARD = new BigDecimal("5.99");
    private static final BigDecimal BASE_RATE_EXPRESS = new BigDecimal("12.99");
    private static final BigDecimal BASE_RATE_OVERNIGHT = new BigDecimal("24.99");
    private static final BigDecimal WEIGHT_MULTIPLIER = new BigDecimal("0.50");
    private static final BigDecimal DISTANCE_FACTOR = new BigDecimal("1.15");

    public List<ShippingRateResponse> calculateRates(ShippingRateRequest request) {
        List<ShippingRateResponse> rates = new ArrayList<>();

        BigDecimal weight = request.getWeight();
        boolean isInternational = isInternational(request.getOriginAddress(), request.getDestinationAddress());
        BigDecimal distanceMultiplier = isInternational ? DISTANCE_FACTOR.multiply(new BigDecimal("2.5")) : DISTANCE_FACTOR;

        // Standard shipping
        ShippingRateResponse standard = new ShippingRateResponse();
        standard.setCarrier("Standard Post");
        standard.setRate(calculateRate(BASE_RATE_STANDARD, weight, distanceMultiplier));
        standard.setEstimatedDays(isInternational ? "10-15 business days" : "5-7 business days");
        standard.setServiceLevel("STANDARD");
        rates.add(standard);

        // Express shipping
        ShippingRateResponse express = new ShippingRateResponse();
        express.setCarrier("Express Courier");
        express.setRate(calculateRate(BASE_RATE_EXPRESS, weight, distanceMultiplier));
        express.setEstimatedDays(isInternational ? "5-7 business days" : "2-3 business days");
        express.setServiceLevel("EXPRESS");
        rates.add(express);

        // Overnight (domestic only)
        if (!isInternational) {
            ShippingRateResponse overnight = new ShippingRateResponse();
            overnight.setCarrier("Priority Overnight");
            overnight.setRate(calculateRate(BASE_RATE_OVERNIGHT, weight, BigDecimal.ONE));
            overnight.setEstimatedDays("1 business day");
            overnight.setServiceLevel("OVERNIGHT");
            rates.add(overnight);
        }

        return rates;
    }

    public BigDecimal calculateSingleRate(BigDecimal weight, String serviceLevel, boolean international) {
        BigDecimal baseRate;
        switch (serviceLevel.toUpperCase()) {
            case "EXPRESS":
                baseRate = BASE_RATE_EXPRESS;
                break;
            case "OVERNIGHT":
                baseRate = BASE_RATE_OVERNIGHT;
                break;
            default:
                baseRate = BASE_RATE_STANDARD;
        }

        BigDecimal distanceMultiplier = international ? DISTANCE_FACTOR.multiply(new BigDecimal("2.5")) : DISTANCE_FACTOR;
        return calculateRate(baseRate, weight, distanceMultiplier);
    }

    private BigDecimal calculateRate(BigDecimal baseRate, BigDecimal weight, BigDecimal distanceMultiplier) {
        BigDecimal weightCharge = weight.multiply(WEIGHT_MULTIPLIER);
        return baseRate.add(weightCharge).multiply(distanceMultiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isInternational(String origin, String destination) {
        String originCountry = extractCountry(origin);
        String destCountry = extractCountry(destination);
        return !originCountry.equalsIgnoreCase(destCountry);
    }

    private String extractCountry(String address) {
        if (address == null || address.isBlank()) {
            return "US";
        }
        String[] parts = address.split(",");
        return parts[parts.length - 1].trim();
    }
}
