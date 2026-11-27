package com.vintro.wsplanner.services;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

// unit tests for release changelog extraction and localized comment parsing
public class UpdateManagerChangelogTest {

    private static final String SAMPLE_RELEASE_BODY =
            "[![Download APK](https://img.shields.io/badge/Download-WSPlanner--v1.4.1.apk-2563EB?style=for-the-badge&logo=android&logoColor=white)](https://github.com/VintroKato/WSPlanner/releases/download/v1.4.1/WSPlanner-v1.4.1.apk)\n"
                    + "\n"
                    + "## What's changed\n"
                    + "### UI animations, tooltips and localized changelogs\n"
                    + "\n"
                    + "- Added sliding bottom navigation animation\n"
                    + "- Added long-press tooltips to PUW buttons\n"
                    + "\n"
                    + "---\n"
                    + "\n"
                    + "## Build info\n"
                    + "\n"
                    + "| Property | Value |\n"
                    + "| :--- | :--- |\n"
                    + "| **File** | [`WSPlanner-v1.4.1.apk`](https://github.com/VintroKato/WSPlanner/releases/download/v1.4.1/WSPlanner-v1.4.1.apk) |\n"
                    + "| **Commit** | abc123def456 (UI animations, tooltips and localized changelogs) |\n"
                    + "\n"
                    + "---\n"
                    + "\n"
                    + "<!-- changelog:ru\n"
                    + "Плавные анимации интерфейса и подсказки\n"
                    + "- Добавлена скользящая анимация в нижнем меню\n"
                    + "- Добавлены подсказки при долгом нажатии на кнопки PUW\n"
                    + "-->\n"
                    + "\n"
                    + "<!-- changelog:uk\n"
                    + "Плавні анімації інтерфейсу та підказки\n"
                    + "- Додано ковзну анімацію в нижньому меню\n"
                    + "- Додано підказки при довгому натисканні на кнопки PUW\n"
                    + "-->\n";

    @Test
    public void englishExtractorIgnoresTopBadgeAndHiddenLocalizedComments() {
        List<String> enItems = UpdateManager.extractChangelogBulletList("v1.4.1", SAMPLE_RELEASE_BODY);
        assertEquals(2, enItems.size());
        assertEquals("Added sliding bottom navigation animation", enItems.get(0));
        assertEquals("Added long-press tooltips to PUW buttons", enItems.get(1));

        String commitMsg = UpdateManager.extractCommitMessage("v1.4.1", SAMPLE_RELEASE_BODY);
        assertEquals("UI animations, tooltips and localized changelogs", commitMsg);
    }

    @Test
    public void localizedExtractorParsesRussianAndUkrainianTitleAndBullets() {
        UpdateManager.LocalizedChangelog ru = UpdateManager.extractLocalizedChangelog(SAMPLE_RELEASE_BODY, "ru");
        assertNotNull(ru);
        assertEquals("Плавные анимации интерфейса и подсказки", ru.title);
        assertEquals(2, ru.items.size());
        assertEquals("Добавлена скользящая анимация в нижнем меню", ru.items.get(0));
        assertEquals("Добавлены подсказки при долгом нажатии на кнопки PUW", ru.items.get(1));

        UpdateManager.LocalizedChangelog uk = UpdateManager.extractLocalizedChangelog(SAMPLE_RELEASE_BODY, "uk");
        assertNotNull(uk);
        assertEquals("Плавні анімації інтерфейсу та підказки", uk.title);
        assertEquals(2, uk.items.size());
        assertEquals("Додано ковзну анімацію в нижньому меню", uk.items.get(0));
        assertEquals("Додано підказки при довгому натисканні на кнопки PUW", uk.items.get(1));
    }

    @Test
    public void localizedExtractorHandlesBulletsOnlyAndLegacyReleases() {
        String bulletsOnlyBody = "<!-- changelog:ru\n- Исправлен баг\n-->";
        UpdateManager.LocalizedChangelog ru = UpdateManager.extractLocalizedChangelog(bulletsOnlyBody, "ru");
        assertNotNull(ru);
        assertEquals("", ru.title);
        assertEquals(1, ru.items.size());
        assertEquals("Исправлен баг", ru.items.get(0));

        String legacyBody = "## What's changed\n### Legacy release\n\n- Old bullet\n\n---\n## Build info\n";
        assertNull(UpdateManager.extractLocalizedChangelog(legacyBody, "ru"));
        assertNull(UpdateManager.extractLocalizedChangelog(legacyBody, "uk"));
    }
}
