package uk.gov.crowncommercial.dts.scale.cat.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the SupplierDunsUpdate entity around the custom methods within the class
 */
public class SupplierDunsUpdateTests {
    @Test
    void testGetFormattedCurrentDunsNumberFormatsCorrectlyForPreFormattedValue() {
        SupplierDunsUpdate testModel = new SupplierDunsUpdate();
        testModel.setCurrentDunsNumber("US-DUNS-12345");

        String result = testModel.getFormattedCurrentDunsNumber();

        assertNotNull(result);
        assertEquals("US-DUNS-12345", result);
    }

    @Test
    void testGetFormattedCurrentDunsNumberReturnsNullForNullValue() {
        SupplierDunsUpdate testModel = new SupplierDunsUpdate();

        String result = testModel.getFormattedCurrentDunsNumber();

        assertNull(result);
    }

    @Test
    void testGetFormattedCurrentDunsNumberFormatsCorrectlyForNonFormattedValue() {
        SupplierDunsUpdate testModel = new SupplierDunsUpdate();
        testModel.setCurrentDunsNumber("12345");

        String result = testModel.getFormattedCurrentDunsNumber();

        assertNotNull(result);
        assertEquals("US-DUNS-12345", result);
    }

    @Test
    void testGetCleanedCurrentDunsNumberFormatsCorrectlyForPreFormattedValue() {
        SupplierDunsUpdate testModel = new SupplierDunsUpdate();
        testModel.setCurrentDunsNumber("US-DUNS-12345");

        String result = testModel.getCleanedCurrentDunsNumber();

        assertNotNull(result);
        assertEquals("12345", result);
    }

    @Test
    void testGetCleanedCurrentDunsNumberReturnsNullForNullValue() {
        SupplierDunsUpdate testModel = new SupplierDunsUpdate();

        String result = testModel.getCleanedCurrentDunsNumber();

        assertNull(result);
    }

    @Test
    void testGetCleanedCurrentDunsNumberFormatsCorrectlyForNonFormattedValue() {
        SupplierDunsUpdate testModel = new SupplierDunsUpdate();
        testModel.setCurrentDunsNumber("12345");

        String result = testModel.getCleanedCurrentDunsNumber();

        assertNotNull(result);
        assertEquals("12345", result);
    }

    @Test
    void testGetFormattedReplacementDunsNumberFormatsCorrectlyForPreFormattedValue() {
        SupplierDunsUpdate testModel = new SupplierDunsUpdate();
        testModel.setReplacementDunsNumber("US-DUNS-12345");

        String result = testModel.getFormattedReplacementDunsNumber();

        assertNotNull(result);
        assertEquals("US-DUNS-12345", result);
    }

    @Test
    void testGetFormattedReplacementDunsNumberReturnsNullForNullValue() {
        SupplierDunsUpdate testModel = new SupplierDunsUpdate();

        String result = testModel.getFormattedReplacementDunsNumber();

        assertNull(result);
    }

    @Test
    void testGetFormattedReplacementDunsNumberFormatsCorrectlyForNonFormattedValue() {
        SupplierDunsUpdate testModel = new SupplierDunsUpdate();
        testModel.setReplacementDunsNumber("12345");

        String result = testModel.getFormattedReplacementDunsNumber();

        assertNotNull(result);
        assertEquals("US-DUNS-12345", result);
    }

    @Test
    void testGetCleanedReplacementDunsNumberFormatsCorrectlyForPreFormattedValue() {
        SupplierDunsUpdate testModel = new SupplierDunsUpdate();
        testModel.setReplacementDunsNumber("US-DUNS-12345");

        String result = testModel.getCleanedReplacementDunsNumber();

        assertNotNull(result);
        assertEquals("12345", result);
    }

    @Test
    void testGetCleanedReplacementDunsNumberReturnsNullForNullValue() {
        SupplierDunsUpdate testModel = new SupplierDunsUpdate();

        String result = testModel.getCleanedReplacementDunsNumber();

        assertNull(result);
    }

    @Test
    void testGetCleanedReplacementDunsNumberFormatsCorrectlyForNonFormattedValue() {
        SupplierDunsUpdate testModel = new SupplierDunsUpdate();
        testModel.setReplacementDunsNumber("12345");

        String result = testModel.getCleanedReplacementDunsNumber();

        assertNotNull(result);
        assertEquals("12345", result);
    }
}