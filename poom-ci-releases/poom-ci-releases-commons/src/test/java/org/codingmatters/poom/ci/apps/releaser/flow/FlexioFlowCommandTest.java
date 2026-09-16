package org.codingmatters.poom.ci.apps.releaser.flow;

import org.codingmatters.poom.ci.apps.releaser.command.RecordingCommandHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class FlexioFlowCommandTest {

    @Rule
    public TemporaryFolder dir = new TemporaryFolder();

    @Test
    public void givenFlow__whenStartingHotfix__thenFlexioFlowHotfixStartInvoked() throws Exception {
        RecordingCommandHelper helper = new RecordingCommandHelper();

        new FlexioFlow(this.dir.getRoot(), helper).startHotfix();

        assertThat(helper.commands(), contains("flexio-flow hotfix start -D"));
    }

    @Test
    public void givenFlow__whenFinishingHotfix__thenFlexioFlowHotfixFinishInvoked() throws Exception {
        RecordingCommandHelper helper = new RecordingCommandHelper();

        new FlexioFlow(this.dir.getRoot(), helper).finishHotfix();

        assertThat(helper.commands(), contains("flexio-flow hotfix finish -D"));
    }

    @Test
    public void givenFlow__whenStartingRelease__thenFlexioFlowReleaseStartInvoked() throws Exception {
        RecordingCommandHelper helper = new RecordingCommandHelper();

        new FlexioFlow(this.dir.getRoot(), helper).startRelease();

        assertThat(helper.commands(), contains("flexio-flow release start -D"));
    }
}
