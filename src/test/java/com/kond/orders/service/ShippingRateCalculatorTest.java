package com.kond.orders.service;

import com.kond.orders.dto.ShippingRateRequest;
import com.kond.orders.dto.ShippingRateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShippingRateCalculatorTest {

    private ShippingRateCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ShippingRateCalculator();
    }

    private ShippingRateRequest request(String origin, String destination, String weight) {
        ShippingRateRequest req = new ShippingRateRequest();
        req.setOriginAddress(origin);
        req.setDestinationAddress(destination);
        req.setWeight(new BigDecimal(weight));
        return req;
    }

    private ShippingRateResponse findByLevel(List<ShippingRateResponse> rates, String level) {
        return rates.stream()
                .filter(r -> level.equals(r.getServiceLevel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No entry with serviceLevel=" + level));
    }

    // --- calculateRates() domestic ---

    @Test
    void calculateRates_domestic_returnsThreeEntries() {
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("123 Main St, Springfield, US", "456 Oak Ave, Denver, US", "2.0"));
        assertEquals(3, rates.size());
    }

    @Test
    void calculateRates_domestic_weight2_standardRate() {
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("123 Main St, US", "456 Oak Ave, US", "2.0"));
        // (5.99 + 1.00) × 1.15 = 8.04
        assertEquals(0, new BigDecimal("8.04").compareTo(findByLevel(rates, "STANDARD").getRate()));
    }

    @Test
    void calculateRates_domestic_weight2_expressRate() {
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("123 Main St, US", "456 Oak Ave, US", "2.0"));
        // (12.99 + 1.00) × 1.15 = 16.09
        assertEquals(0, new BigDecimal("16.09").compareTo(findByLevel(rates, "EXPRESS").getRate()));
    }

    @Test
    void calculateRates_domestic_weight2_overnightRate() {
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("123 Main St, US", "456 Oak Ave, US", "2.0"));
        // (24.99 + 1.00) × 1.0 = 25.99  (overnight uses BigDecimal.ONE, not DISTANCE_FACTOR)
        assertEquals(0, new BigDecimal("25.99").compareTo(findByLevel(rates, "OVERNIGHT").getRate()));
    }

    @Test
    void calculateRates_domestic_weight10_standardRate() {
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("123 Main St, US", "456 Oak Ave, US", "10.0"));
        // (5.99 + 5.00) × 1.15 = 12.64
        assertEquals(0, new BigDecimal("12.64").compareTo(findByLevel(rates, "STANDARD").getRate()));
    }

    @Test
    void calculateRates_domestic_weight10_expressRate() {
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("123 Main St, US", "456 Oak Ave, US", "10.0"));
        // (12.99 + 5.00) × 1.15 = 20.69
        assertEquals(0, new BigDecimal("20.69").compareTo(findByLevel(rates, "EXPRESS").getRate()));
    }

    @Test
    void calculateRates_domestic_overnightMetadata() {
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("123 Main St, US", "456 Oak Ave, US", "2.0"));
        ShippingRateResponse overnight = findByLevel(rates, "OVERNIGHT");
        assertEquals("Priority Overnight", overnight.getCarrier());
        assertEquals("1 business day", overnight.getEstimatedDays());
        assertEquals("OVERNIGHT", overnight.getServiceLevel());
    }

    @Test
    void calculateRates_domestic_standardMetadata() {
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("123 Main St, US", "456 Oak Ave, US", "2.0"));
        ShippingRateResponse standard = findByLevel(rates, "STANDARD");
        assertEquals("Standard Post", standard.getCarrier());
        assertEquals("5-7 business days", standard.getEstimatedDays());
    }

    // --- calculateRates() international ---

    @Test
    void calculateRates_international_returnsTwoEntries() {
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("123 Main St, US", "Musterstrasse 1, Berlin, DE", "2.0"));
        assertEquals(2, rates.size());
    }

    @Test
    void calculateRates_international_noOvernightEntry() {
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("123 Main St, US", "Musterstrasse 1, Berlin, DE", "2.0"));
        assertTrue(rates.stream().noneMatch(r -> "OVERNIGHT".equals(r.getServiceLevel())));
    }

    @Test
    void calculateRates_international_weight2_standardRate() {
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("123 Main St, US", "Musterstrasse 1, Berlin, DE", "2.0"));
        // (5.99 + 1.00) × 2.875 = 20.10
        assertEquals(0, new BigDecimal("20.10").compareTo(findByLevel(rates, "STANDARD").getRate()));
    }

    @Test
    void calculateRates_international_weight2_expressRate() {
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("123 Main St, US", "Musterstrasse 1, Berlin, DE", "2.0"));
        // (12.99 + 1.00) × 2.875 = 40.22
        assertEquals(0, new BigDecimal("40.22").compareTo(findByLevel(rates, "EXPRESS").getRate()));
    }

    @Test
    void calculateRates_international_standardEstimatedDays() {
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("123 Main St, US", "Musterstrasse 1, Berlin, DE", "2.0"));
        assertEquals("10-15 business days", findByLevel(rates, "STANDARD").getEstimatedDays());
    }

    @Test
    void calculateRates_international_expressEstimatedDays() {
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("123 Main St, US", "Musterstrasse 1, Berlin, DE", "2.0"));
        assertEquals("5-7 business days", findByLevel(rates, "EXPRESS").getEstimatedDays());
    }

    // --- calculateSingleRate() per service level ---

    @Test
    void calculateSingleRate_standard_domestic() {
        // (5.99 + 1.00) × 1.15 = 8.04
        BigDecimal result = calculator.calculateSingleRate(new BigDecimal("2.0"), "STANDARD", false);
        assertEquals(0, new BigDecimal("8.04").compareTo(result));
    }

    @Test
    void calculateSingleRate_express_domestic() {
        // (12.99 + 1.00) × 1.15 = 16.09
        BigDecimal result = calculator.calculateSingleRate(new BigDecimal("2.0"), "EXPRESS", false);
        assertEquals(0, new BigDecimal("16.09").compareTo(result));
    }

    @Test
    void calculateSingleRate_overnight_domestic_usesDistanceFactor() {
        // calculateSingleRate always uses DISTANCE_FACTOR (1.15), unlike calculateRates() overnight
        // (24.99 + 1.00) × 1.15 = 29.89
        BigDecimal result = calculator.calculateSingleRate(new BigDecimal("2.0"), "OVERNIGHT", false);
        assertEquals(0, new BigDecimal("29.89").compareTo(result));
    }

    @Test
    void calculateSingleRate_standard_international() {
        // (5.99 + 1.00) × 2.875 = 20.10
        BigDecimal result = calculator.calculateSingleRate(new BigDecimal("2.0"), "STANDARD", true);
        assertEquals(0, new BigDecimal("20.10").compareTo(result));
    }

    @Test
    void calculateSingleRate_express_international() {
        // (12.99 + 1.00) × 2.875 = 40.22
        BigDecimal result = calculator.calculateSingleRate(new BigDecimal("2.0"), "EXPRESS", true);
        assertEquals(0, new BigDecimal("40.22").compareTo(result));
    }

    @Test
    void calculateSingleRate_unknownServiceLevel_defaultsToStandard() {
        BigDecimal economy = calculator.calculateSingleRate(new BigDecimal("2.0"), "ECONOMY", false);
        BigDecimal standard = calculator.calculateSingleRate(new BigDecimal("2.0"), "STANDARD", false);
        assertEquals(0, standard.compareTo(economy));
    }

    @Test
    void calculateSingleRate_lowercaseExpress_caseInsensitive() {
        BigDecimal lower = calculator.calculateSingleRate(new BigDecimal("2.0"), "express", false);
        BigDecimal upper = calculator.calculateSingleRate(new BigDecimal("2.0"), "EXPRESS", false);
        assertEquals(0, upper.compareTo(lower));
    }

    @Test
    void calculateSingleRate_lowercaseStandard_caseInsensitive() {
        BigDecimal lower = calculator.calculateSingleRate(new BigDecimal("2.0"), "standard", false);
        BigDecimal upper = calculator.calculateSingleRate(new BigDecimal("2.0"), "STANDARD", false);
        assertEquals(0, upper.compareTo(lower));
    }

    @Test
    void calculateSingleRate_nullServiceLevel_defaultsToStandard() {
        BigDecimal nullLevel = calculator.calculateSingleRate(new BigDecimal("2.0"), null, false);
        BigDecimal standard = calculator.calculateSingleRate(new BigDecimal("2.0"), "STANDARD", false);
        assertEquals(0, standard.compareTo(nullLevel));
    }

    // --- edge cases ---

    @Test
    void calculateSingleRate_zeroWeight_standard_domestic() {
        // 5.99 × 1.15 = 6.89
        BigDecimal result = calculator.calculateSingleRate(BigDecimal.ZERO, "STANDARD", false);
        assertEquals(0, new BigDecimal("6.89").compareTo(result));
    }

    @Test
    void calculateRates_zeroWeight_doesNotThrow() {
        List<ShippingRateResponse> rates = assertDoesNotThrow(
                () -> calculator.calculateRates(request("123 Main St, US", "456 Oak Ave, US", "0")));
        assertEquals(3, rates.size());
    }

    @Test
    void calculateSingleRate_heavyWeight_express_domestic() {
        // (12.99 + 250.00) × 1.15 = 302.44
        BigDecimal result = calculator.calculateSingleRate(new BigDecimal("500"), "EXPRESS", false);
        assertEquals(0, new BigDecimal("302.44").compareTo(result));
    }

    @Test
    void calculateRates_heavyWeight_returnsFinitePositiveRates() {
        List<ShippingRateResponse> rates = assertDoesNotThrow(
                () -> calculator.calculateRates(request("123 Main St, US", "456 Oak Ave, US", "500")));
        assertEquals(3, rates.size());
        rates.forEach(r -> {
            assertNotNull(r.getRate());
            assertTrue(r.getRate().compareTo(BigDecimal.ZERO) > 0);
        });
    }

    @Test
    void calculateRates_nullOriginAddress_defaultsToUS_domestic() {
        ShippingRateRequest req = new ShippingRateRequest();
        req.setOriginAddress(null);
        req.setDestinationAddress("456 Oak Ave, US");
        req.setWeight(new BigDecimal("2.0"));
        List<ShippingRateResponse> rates = calculator.calculateRates(req);
        assertEquals(3, rates.size()); // null → "US", same as destination → domestic
    }

    @Test
    void calculateRates_singleSegmentAddress_parsedAsCountry() {
        // "US" has no commas → country = "US"; both same → domestic
        List<ShippingRateResponse> rates = calculator.calculateRates(request("US", "US", "2.0"));
        assertEquals(3, rates.size());
    }

    @Test
    void calculateRates_whitespaceAroundCountrySegment_treatedAsDomestic() {
        // trailing space in country segment — trim() normalises it
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("123 St, US ", "456 Ave, US", "2.0"));
        assertEquals(3, rates.size());
    }

    @Test
    void calculateRates_sameCityDifferentAddresses_domestic() {
        List<ShippingRateResponse> rates = calculator.calculateRates(
                request("100 First Ave, New York, US", "200 Second Ave, Los Angeles, US", "2.0"));
        assertEquals(3, rates.size());
    }

    @Test
    void calculateRates_negativeWeight_throwsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateRates(request("123 Main St, US", "456 Oak Ave, US", "-1.0")));
        assertEquals("Weight must be greater than or equal to zero", ex.getMessage());
    }

    @Test
    void calculateRates_nullWeight_throwsIllegalArgument() {
        ShippingRateRequest req = new ShippingRateRequest();
        req.setOriginAddress("123 Main St, US");
        req.setDestinationAddress("456 Oak Ave, US");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateRates(req));
        assertEquals("Weight is required", ex.getMessage());
    }
}
