package com.vintro.wsplanner;

import org.junit.Test;
import static org.junit.Assert.*;

import com.vintro.wsplanner.services.TeacherEmailService;

public class TeacherEmailServiceTest {

    @Test
    public void testNormalizeTeacherName() {
        // strips academic titles and punctuation
        assertEquals("jerzy adamczyk", TeacherEmailService.normalizeForSearch("dr inż. Jerzy Adamczyk"));
        assertEquals("piotr bednarczyk", TeacherEmailService.normalizeForSearch("dr Piotr Bednarczyk"));
        assertEquals("anna nowak", TeacherEmailService.normalizeForSearch("mgr inż. Anna Nowak"));
        assertEquals("jan kowalski", TeacherEmailService.normalizeForSearch("prof. dr hab. Jan Kowalski"));
        assertEquals("marek zieliński", TeacherEmailService.normalizeForSearch("ks. dr Marek Zieliński"));
    }

    @Test
    public void testCleanWhitespaceAndEmpty() {
        assertEquals("", TeacherEmailService.normalizeForSearch(null));
        assertEquals("", TeacherEmailService.normalizeForSearch("   "));
        assertEquals("", TeacherEmailService.normalizeForSearch("—"));
        assertEquals("adam kowalski", TeacherEmailService.normalizeForSearch("  Adam   Kowalski  "));
    }

    @Test
    public void testGenericEmailsRejected() {
        assertTrue(TeacherEmailService.isGenericUniversityEmail("rektorat@wspa.pl"));
        assertTrue(TeacherEmailService.isGenericUniversityEmail("kancelaria@wspa.pl"));
        assertTrue(TeacherEmailService.isGenericUniversityEmail("dziekanat@wspa.pl"));
        assertTrue(TeacherEmailService.isGenericUniversityEmail("info@wspa.pl"));
        assertFalse(TeacherEmailService.isGenericUniversityEmail("j.kowalski@wspa.pl"));
        assertFalse(TeacherEmailService.isGenericUniversityEmail("a.nowak@wspa.pl"));
    }

    @Test
    public void testNetworkErrorResult() {
        TeacherEmailService.TeacherEmailResult netErr = TeacherEmailService.TeacherEmailResult.networkError();
        assertTrue(netErr.isNetworkError);
        assertFalse(netErr.hasEmail);
        assertFalse(netErr.isFoundOnWebsite);
        assertNull(netErr.email);

        TeacherEmailService.TeacherEmailResult found = TeacherEmailService.TeacherEmailResult.found("teacher@wspa.pl", "http://example.com", "Teacher");
        assertFalse(found.isNetworkError);
        assertTrue(found.hasEmail);
        assertEquals("teacher@wspa.pl", found.email);
    }
}
