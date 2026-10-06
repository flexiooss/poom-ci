package org.codingmatters.poom.ci.apps.releaser.graph;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

public class TagVersionsTest {

    @Rule
    public TemporaryFolder dir = new TemporaryFolder();

    private File fileWith(String content) throws Exception {
        File result = this.dir.newFile();
        try (Writer writer = new FileWriter(result)) {
            writer.write(content);
        }
        return result;
    }

    @Test
    public void givenFileWithTwoRepositories__whenReading__thenBothAreReadable() throws Exception {
        TagVersions actual = TagVersions.from(this.fileWith(
                "---\n" +
                "Flexio-corp/flexio-data-renderer: \"1.29.0\"\n" +
                "Flexio-corp/flexio-tabular-config: \"1.13.0\"\n"
        ));

        assertThat(actual.isEmpty(), is(false));
        assertThat(actual.tagFor("Flexio-corp/flexio-data-renderer").get(), is("1.29.0"));
        assertThat(actual.tagFor("Flexio-corp/flexio-tabular-config").get(), is("1.13.0"));
        assertThat(actual.repositories(), hasSize(2));
    }

    @Test
    public void givenRepositoryNotListed__whenAskingForItsTag__thenEmpty() throws Exception {
        TagVersions actual = TagVersions.from(this.fileWith(
                "---\nFlexio-corp/flexio-data-renderer: \"1.29.0\"\n"));

        assertThat(actual.tagFor("Flexio-corp/other").isPresent(), is(false));
    }

    @Test
    public void givenNullFile__whenReading__thenEmpty() throws Exception {
        assertThat(TagVersions.from(null).isEmpty(), is(true));
    }

    @Test
    public void givenEmptyFile__whenReading__thenEmpty() throws Exception {
        assertThat(TagVersions.from(this.fileWith("")).isEmpty(), is(true));
    }

    @Test
    public void givenFileWithOnlyDocumentMarker__whenReading__thenEmpty() throws Exception {
        assertThat(TagVersions.from(this.fileWith("---\n")).isEmpty(), is(true));
    }

    @Test
    public void givenEmptyTagVersions__whenAskingForAnyTag__thenEmpty() throws Exception {
        assertThat(TagVersions.from(null).tagFor("Flexio-corp/whatever").isPresent(), is(false));
    }

    @Test
    public void givenFourComponentVersion__whenReading__thenAccepted() throws Exception {
        TagVersions actual = TagVersions.from(this.fileWith(
                "---\nFlexio-corp/flexio-data-renderer: \"1.29.0.1\"\n"));

        assertThat(actual.tagFor("Flexio-corp/flexio-data-renderer").get(), is("1.29.0.1"));
    }

    @Test
    public void givenMalformedVersion__whenReading__thenFailsNamingTheRepositoryAndTheValue() throws Exception {
        try {
            TagVersions.from(this.fileWith(
                    "---\nFlexio-corp/flexio-data-renderer: \"latest\"\n"));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("Flexio-corp/flexio-data-renderer"));
            assertThat(e.getMessage(), containsString("latest"));
        }
    }

    @Test
    public void givenTwoComponentVersion__whenReading__thenFails() throws Exception {
        try {
            TagVersions.from(this.fileWith(
                    "---\nFlexio-corp/flexio-data-renderer: \"1.29\"\n"));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("1.29"));
        }
    }
}
