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

public class PropagatedVersionsTest {

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
    public void givenFileWithTwoCoordinates__whenReading__thenBothPropagated() throws Exception {
        PropagationContext actual = PropagatedVersions.from(this.fileWith(
                "---\n" +
                "- io.flexio:flexio-service-parent:1.617.0\n" +
                "- io.flexio.apis:flexio-apis:2.34.1\n"
        ));

        assertThat(actual.iEmpty(), is(false));
        assertThat(actual.text(), containsString("io.flexio:flexio-service-parent:1.617.0"));
        assertThat(actual.text(), containsString("io.flexio.apis:flexio-apis:2.34.1"));
    }

    @Test
    public void givenNullFile__whenReading__thenContextIsEmpty() throws Exception {
        assertThat(PropagatedVersions.from(null).iEmpty(), is(true));
    }

    @Test
    public void givenEmptyFile__whenReading__thenContextIsEmpty() throws Exception {
        assertThat(PropagatedVersions.from(this.fileWith("")).iEmpty(), is(true));
    }

    @Test
    public void givenFileWithOnlyDocumentMarker__whenReading__thenContextIsEmpty() throws Exception {
        assertThat(PropagatedVersions.from(this.fileWith("---\n")).iEmpty(), is(true));
    }

    @Test
    public void givenFileWithMalformedLine__whenReading__thenFailsNamingTheLine() throws Exception {
        try {
            PropagatedVersions.from(this.fileWith(
                    "---\n" +
                    "- io.flexio:flexio-service-parent:1.617.0\n" +
                    "- not-a-coordinate\n"
            ));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("not-a-coordinate"));
        }
    }
}
