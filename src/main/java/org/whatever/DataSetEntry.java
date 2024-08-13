package org.whatever;

import java.nio.file.Path;
import java.text.DecimalFormat;
import static org.whatever.Main.matchMethodToString;

class DataSetEntry {
    public Path needle;
    public Path haystack;
    public int matchMethod;
    public boolean matchExpected;
    public Path dirTemplateMatchOngoingUndecided;
    public Path dirTemplateMatchExpectedMatchActualMatch;
    public Path dirTemplateMatchExpectedMatchActualNoMatch;
    public Path dirTemplateMatchExpectedNoMatchActualMatch;
    public Path dirTemplateMatchExpectedNoMatchActualNoMatch;

    public MatchTemplateResult result = new MatchTemplateResult();

    public DataSetEntry() {
    }

    public boolean matchesAsExpected() {
        return matchExpected == this.result.matchActual;
    }

    public String toString() {
        return "Result:" +
               "\n  needle               " + needle +
               "\n  haystack             " + haystack +
               "\n  matchMethod          " + matchMethodToString(matchMethod) +
               "\n  matchExpected        " + matchExpected +
               "\n  matchActual          " + result.matchActual +
               "\n  matchesAsExpected    " + matchesAsExpected() +
               "\n  closestVal           " + new DecimalFormat("#.##########################").format(result.closestVal)
               +
               "\n  closestLoc           " + result.closestLoc;
    }

    public static String getCsvHeader() {
        return "needle;haystack;matchMethod;matchExpected;matchActual;matchesAsExpected;closestVal;closestLoc";
    }
    public String toCsvString() {
        return needle + ";" + haystack + ";" + matchMethodToString(matchMethod) + ";" + matchExpected + ";"
               + result.matchActual + ";" + matchesAsExpected() + ";" + new DecimalFormat(
            "#.##########################").format(result.closestVal) + ";" + result.closestLoc;
    }
}
