package org.codingmatters.poom.ci.runners.pipeline.loggers;

import org.codingmatters.poom.ci.runners.pipeline.PipelineExecutor;

public interface ClosableStageLogger extends PipelineExecutor.StageLogListener, AutoCloseable {
    ClosableStageLogger NOOP = new ClosableStageLogger() {
        @Override
        public void close() throws Exception {
        }

        @Override
        public void logLine(String log) {
        }
    };
}
