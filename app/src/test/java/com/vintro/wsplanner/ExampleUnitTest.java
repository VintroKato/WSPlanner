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
    public void testMaskName() {
        assertEquals("J*n K******i", Logger.maskName("Jan Kowalski"));
        assertEquals("A**a-M***a", Logger.maskName("Anna-Maria"));
        assertEquals("A*", Logger.maskName("A"));
        assertEquals("K*o", Logger.maskName("Ko"));
        assertEquals("", Logger.maskName(""));
        assertEquals("", Logger.maskName(null));
        assertEquals("K******i", Logger.maskName("K******i"));
    }

    @Test
    public void testMaskSensitiveData() {
        String log1 = "for student 'Kowalski', major 'Informatyka'";
        String masked1 = Logger.maskSensitiveData(log1);
        assertTrue(masked1.contains("for student 'K******i'"));

        String log2 = "StudentSurname: 'Nowak'";
        String masked2 = Logger.maskSensitiveData(log2);
        assertTrue(masked2.contains("StudentSurname: 'N***k'"));

        // User case 1: Name extracted from dashboard
        String log3 = "Name extracted from dashboard: Jan Kowalski";
        String masked3 = Logger.maskSensitiveData(log3);
        assertEquals("Name extracted from dashboard: J*n K******i", masked3);

        // User case 2: CourseSetupService log
        String log4 = "Course setup data loaded successfully: 17 courses, EnglishGroup=null, StudentName='Jan Kowalski', SeminarTeacher='mgr inż. Sebastian Witek'";
        String masked4 = Logger.maskSensitiveData(log4);
        assertTrue(masked4.contains("StudentName='J*n K******i'"));
        assertTrue(masked4.contains("SeminarTeacher='mgr inż. Sebastian Witek'"));

        // User case 3: SetupCourseActivity log
        String log5 = "Loaded 17 courses. Full name: 'Jan Kowalski'";
        String masked5 = Logger.maskSensitiveData(log5);
        assertEquals("Loaded 17 courses. Full name: 'J*n K******i'", masked5);

        // User case 4: PreferencesManager log
        String log6 = "Setting global student name: Jan Kowalski";
        String masked6 = Logger.maskSensitiveData(log6);
        assertEquals("Setting global student name: J*n K******i", masked6);

        // Additional cases: extracted from files.php, FullName, etc.
        String log7 = "Name extracted from files.php: Jan Kowalski";
        assertEquals("Name extracted from files.php: J*n K******i", Logger.maskSensitiveData(log7));

        String log8 = "Additional setup data fetched: English=null, FullName='Jan Kowalski', Seminar='mgr inż. Sebastian Witek'";
        String masked8 = Logger.maskSensitiveData(log8);
        assertTrue(masked8.contains("FullName='J*n K******i'"));
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

    @Test
    public void testIsVersionTagOnly() {
        assertTrue(com.vintro.wsplanner.services.UpdateManager.isVersionTagOnly("v1.0.2"));
        assertTrue(com.vintro.wsplanner.services.UpdateManager.isVersionTagOnly("1.0.0"));
        assertTrue(com.vintro.wsplanner.services.UpdateManager.isVersionTagOnly("v1.0.3-beta1"));
        assertTrue(com.vintro.wsplanner.services.UpdateManager.isVersionTagOnly(""));
        assertTrue(com.vintro.wsplanner.services.UpdateManager.isVersionTagOnly(null));

        assertFalse(com.vintro.wsplanner.services.UpdateManager.isVersionTagOnly("Fix schedule parsing"));
        assertFalse(com.vintro.wsplanner.services.UpdateManager.isVersionTagOnly("v1.0.2: Security update"));
    }

    @Test
    public void testExtractChangelog_WhenNoChangelogSection_AndBodyIsTechnical() {
        // Reproduce exact case from user's screenshot: v1.0.2 with only Build Info
        String releaseName = "v1.0.2";
        String body = "### Build Info\n\n> **Automated Build:** This APK was compiled and signed automatically by **GitHub Actions**...\n\n| Property | Value |\n| File | WSPlanner-v1.0.2.apk |";
        String result = com.vintro.wsplanner.services.UpdateManager.extractChangelog(releaseName, body);
        assertNull(result); // Must be null so dialog falls back to localized update_dialog_desc!
    }

    @Test
    public void testExtractChangelog_WhenWhatsChangedPresent() {
        String releaseName = "v1.0.4";
        String body = "### What's Changed\n\n- Fix schedule parsing\n- Add dark mode\n\n---\n\n### Build Info\n> Automated Build...";
        String result = com.vintro.wsplanner.services.UpdateManager.extractChangelog(releaseName, body);
        assertNotNull(result);
        assertTrue(result.contains("• Fix schedule parsing"));
        assertTrue(result.contains("• Add dark mode"));
        assertFalse(result.contains("Build Info"));
        assertFalse(result.contains("Automated Build"));
    }

    @Test
    public void testExtractChangelog_WhenCommitTitleProvided_AndNoChangelog() {
        String releaseName = "Fix schedule parsing on Friday";
        String body = "### Build Info\n> Automated Build...";
        String result = com.vintro.wsplanner.services.UpdateManager.extractChangelog(releaseName, body);
        assertEquals("Fix schedule parsing on Friday", result);
    }

    @Test
    public void testExtractChangelog_PreservesFullTextBeyond500Chars() {
        StringBuilder sb = new StringBuilder();
        sb.append("### What's Changed\n\n");
        for (int i = 1; i <= 25; i++) {
            sb.append("- Feature number ").append(i).append(": detailed description of change\n");
        }
        sb.append("\n---\n### Build Info\n");
        String result = com.vintro.wsplanner.services.UpdateManager.extractChangelog("v1.0.5", sb.toString());
        assertNotNull(result);
        assertTrue(result.length() > 600);
        assertTrue(result.contains("• Feature number 25: detailed description of change"));
        assertFalse(result.contains("…"));
    }

    @Test
    public void testExtractChangelogBulletList() {
        String releaseName = "v1.1.0";
        String body = "### What's Changed\n\n- The last name is now saved in encrypted storage\n- Added changelist to dialog\n\n---\n### Build info\n";
        java.util.List<String> items = com.vintro.wsplanner.services.UpdateManager.extractChangelogBulletList(releaseName, body);
        assertEquals(2, items.size());
        assertEquals("The last name is now saved in encrypted storage", items.get(0));
        assertEquals("Added changelist to dialog", items.get(1));
    }

    @Test
    public void testExtractChangelogBulletList_ExcludesCommitHeadingAndNonBulletLines() {
        String releaseName = "v1.2.0";
        String body = "## What's changed\n"
                + "### Added Student panel (profile page)\n\n"
                + "- Added a Student Panel with options to change your login and password\n"
                + "- Changed the \"Change group\" button on the main screen to \"Student Panel\"\n\n"
                + "---\n\n"
                + "## Build info\n\n"
                + "| **Commit** | 9c40e0e000000000000000000000000000000000 (Added Student panel (profile page)) |\n";

        java.util.List<String> items = com.vintro.wsplanner.services.UpdateManager.extractChangelogBulletList(releaseName, body);
        assertEquals(2, items.size());
        assertEquals("Added a Student Panel with options to change your login and password", items.get(0));
        assertEquals("Changed the \"Change group\" button on the main screen to \"Student Panel\"", items.get(1));
        assertEquals("Added Student panel (profile page)",
                com.vintro.wsplanner.services.UpdateManager.extractCommitMessage(releaseName, body));
    }

    @Test
    public void testExtractChangelogBulletList_WhenOnlyCommitHeadingPresent() {
        String releaseName = "v1.2.1";
        String body = "## What's changed\n"
                + "### Minor bugfix\n\n"
                + "---\n\n"
                + "## Build info\n\n"
                + "| **Commit** | abcdef123456 (Minor bugfix) |\n";

        java.util.List<String> items = com.vintro.wsplanner.services.UpdateManager.extractChangelogBulletList(releaseName, body);
        assertTrue(items.isEmpty());
        assertEquals("Minor bugfix",
                com.vintro.wsplanner.services.UpdateManager.extractCommitMessage(releaseName, body));
    }

    @Test
    public void testParseBulletLines_IgnoresNonBulletAndMarkdownFormatting() {
        String text = "Heading line\n"
                + "### Subheading\n"
                + "**Bold note:** not a bullet\n"
                + "---\n"
                + "- First `-` item\n"
                + "  - Nested `-` item\n";

        java.util.List<String> items = com.vintro.wsplanner.services.UpdateManager.parseBulletLines(text);
        assertEquals(2, items.size());
        assertEquals("First - item", items.get(0));
        assertEquals("Nested - item", items.get(1));
    }

    @Test
    public void testExtractCommitMessage() {
        // Case 1: Table format (e.g. v1.0.2)
        String bodyTable = "| **Commit** | daf0753a1ca6c6a39cc0bc9f94ac7c9ab490f000 (Added automatic update option in update dialog) |";
        assertEquals("Added automatic update option in update dialog",
                com.vintro.wsplanner.services.UpdateManager.extractCommitMessage("v1.0.2", bodyTable));

        // Case 2: Source Commit list format (e.g. v1.0.1)
        String bodyBullet = "* **Source Commit:** 7f7d9a559fcf6e1d89d427bf502ee1084613225d — Added adaptive font size and sticky bottom to small widget";
        assertEquals("Added adaptive font size and sticky bottom to small widget",
                com.vintro.wsplanner.services.UpdateManager.extractCommitMessage("v1.0.1", bodyBullet));

        // Case 3: Release name already has commit message
        assertEquals("Fix crash on Android 14",
                com.vintro.wsplanner.services.UpdateManager.extractCommitMessage("Fix crash on Android 14", bodyTable));
    }
}