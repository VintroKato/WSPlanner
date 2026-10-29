package com.vintro.wsplanner;

import com.vintro.wsplanner.utils.Logger;

import org.junit.Test;

import static org.junit.Assert.*;

// basic unit test
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void testMaskSurname() {
        assertEquals("K******i", Logger.maskSurname("Kowalski"));
        assertEquals("N***k", Logger.maskSurname("Nowak"));
        assertEquals("K*o", Logger.maskSurname("Ko"));
        assertEquals("A*", Logger.maskSurname("A"));
        assertEquals("", Logger.maskSurname(""));
        assertEquals("", Logger.maskSurname(null));
    }

    @Test
    public void testMaskSensitiveData() {
        String log1 = "for student 'Kowalski', major 'Informatyka'";
        String masked1 = Logger.maskSensitiveData(log1);
        assertTrue(masked1.contains("for student 'K******i'"));

        String log2 = "StudentSurname: 'Nowak'";
        String masked2 = Logger.maskSensitiveData(log2);
        assertTrue(masked2.contains("StudentSurname: 'N***k'"));
    }

    @Test
    public void testFormatException() {
        NullPointerException npe = new NullPointerException();
        String formattedNpe = Logger.formatException(npe);
        assertNotNull(formattedNpe);
        assertFalse(formattedNpe.contains("null"));
        assertTrue(formattedNpe.contains("NullPointerException"));

        RuntimeException re = new RuntimeException("connection timeout");
        String formattedRe = Logger.formatException(re);
        assertEquals("RuntimeException: connection timeout", formattedRe);
    }
}