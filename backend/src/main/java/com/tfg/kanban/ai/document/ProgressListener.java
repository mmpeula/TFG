package com.tfg.kanban.ai.document;

import com.tfg.kanban.ai.ProcessingStage;

/** Quien quiere enterarse de por que fase va el procesamiento. */
@FunctionalInterface
public interface ProgressListener {

    ProgressListener NONE = stage -> {
    };

    void stage(ProcessingStage stage);
}
