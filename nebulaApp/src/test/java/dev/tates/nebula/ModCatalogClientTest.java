package dev.tates.nebula;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.json.JSONArray;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class ModCatalogClientTest {
    @Test
    public void parsesNamedGithubRepositories() throws Exception {
        JSONArray repositories = new JSONArray("["
                + "{\"name\":\"Town of Us: Mira\","
                + "\"url\":\"https://github.com/AU-Avengers/TOU-Mira\"},"
                + "{\"name\":\"Reactor\","
                + "\"url\":\"https://github.com/NuclearPowered/Reactor\"}]");

        Map<String, String> result = ModCatalogClient.parseGithubRepos(repositories);

        assertEquals(2, result.size());
        assertEquals("https://github.com/AU-Avengers/TOU-Mira",
                result.get("Town of Us: Mira"));
    }

    @Test
    public void rejectsNonGithubRepository() throws Exception {
        JSONArray repositories = new JSONArray("["
                + "{\"name\":\"Redirect\",\"url\":\"https://example.com/repo\"}]");

        assertThrows(IOException.class,
                () -> ModCatalogClient.parseGithubRepos(repositories));
    }

    @Test
    public void rejectsDuplicateRepositoryNames() throws Exception {
        JSONArray repositories = new JSONArray("["
                + "{\"name\":\"Reactor\",\"url\":\"https://github.com/a/one\"},"
                + "{\"name\":\"Reactor\",\"url\":\"https://github.com/a/two\"}]");

        assertThrows(IOException.class,
                () -> ModCatalogClient.parseGithubRepos(repositories));
    }
}
