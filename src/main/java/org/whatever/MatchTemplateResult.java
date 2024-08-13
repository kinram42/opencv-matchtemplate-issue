package org.whatever;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Point;

class MatchTemplateResult {
    public boolean matchActual;
    public double closestVal;
    public Point closestLoc;
    public final List<Path> outputImages = new ArrayList<>();
}
