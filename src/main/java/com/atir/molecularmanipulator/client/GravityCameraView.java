package com.atir.molecularmanipulator.client;

import org.joml.Quaternionf;

/** Camera's render-only world-to-gravity correction, including an unfinished gravity turn. */
public interface GravityCameraView {
    Quaternionf omnisequence$inverseViewFrame();
}
