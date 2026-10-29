package com.atir.molecularmanipulator.integration.eco;

import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress;

/** Read-only access to the actual CPU-owned parent, including its exact progress and suspension reason. */
public interface EcoOmniParentOrderCpu {
    ECOBigOrderProgress ecoOmni$parentProgress();
    String ecoOmni$parentProblem();
}
