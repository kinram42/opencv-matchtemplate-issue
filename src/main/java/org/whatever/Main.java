package org.whatever;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.opencv.imgproc.Imgproc;

public class Main {
    private static final Logger l = LogManager.getLogger(Main.class);

    private static Mat readImage(String imageName, int codec) {
        Mat img = Imgcodecs.imread(new File(Main.class.getClassLoader().getResource("images/" + imageName + ".png").getFile()).getAbsolutePath(), codec);
        l.info("Read image '{}' using codec {}, result Mat: {}", imageName, codec, img);
        return img;
    }

    private static void writeImage(String prefix, String imageName, Mat img) {
        String imageFilename = "output_images/" + prefix + imageName + ".png";
        Imgcodecs.imwrite(imageFilename, img);
        l.info("Wrote image '{}'", imageFilename);
    }

    public static Mat createMask(String imageName) {
        Mat img = readImage(imageName, Imgcodecs.IMREAD_UNCHANGED);

        Mat mask = new Mat(img.size(), CvType.CV_8UC1);
        for (int y = 0; y < img.rows(); y++) {
            for (int x = 0; x < img.cols(); x++) {
                // OpenCV uses BGR order, so the alpha channel is the 4th channel
                double alphaValue = img.get(y, x)[3];
                if (alphaValue > 0) {
                    mask.put(y, x, 255); // Consider this pixel
                } else {
                    mask.put(y, x, 0); // Ignore this pixel
                }
            }
        }
        return mask;
    }

    private static String tryMatch(String needle, String haystack, int matchMethod) {
        double minimumMatchThreshold = 0.99;

        String prefix = "method_" + matchMethod + "_find_" + needle + "_in_" + haystack + "_";

        // note that we ignore transparency here (4th alpha channel), we read as 3 channels
        Mat needleImg = readImage(needle, Imgcodecs.IMREAD_COLOR);
        writeImage(prefix, "01_needle", needleImg);

        Mat haystackImg = readImage(haystack, Imgcodecs.IMREAD_COLOR);
        writeImage(prefix, "02_haystack", haystackImg);

        // this uses the 4th alpha channel
        Mat mask = createMask(needle);
        writeImage(prefix, "03_mask", mask);

        // try to match
        Mat matchResult = new Mat();
        Imgproc.matchTemplate(haystackImg, needleImg, matchResult, matchMethod, mask);
        l.info("matchResult:           {}", matchResult);

        Mat normalizedMatchResult = new Mat();
        Core.normalize(matchResult, normalizedMatchResult, 0, 100, Core.NORM_MINMAX, CvType.CV_32F);
        l.info("normalizedMatchResult: {}", matchResult);

        Mat heatmap = new Mat();
        Core.normalize(matchResult, heatmap, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
        writeImage(prefix, "04_heatmap", heatmap);

        // find the min/max in the histogram
        Core.MinMaxLocResult mmr = Core.minMaxLoc(matchResult);
        l.info("minMaxLoc matchResult:           maxVal {}, maxLoc {}, minVal {}, minLoc {}", mmr.maxVal, mmr.maxLoc, mmr.minVal, mmr.minLoc);
        Core.MinMaxLocResult mmr2 = Core.minMaxLoc(normalizedMatchResult);
        l.info("minMaxLoc normalizedMatchResult: maxVal {}, maxLoc {}, minVal {}, minLoc {}", mmr2.maxVal, mmr2.maxLoc, mmr2.minVal, mmr2.minLoc);

        for (int y = 0; y < matchResult.rows(); y++) {
            StringBuilder line = new StringBuilder(String.format("  y %2d: ", y));
            for (int x = 0; x < matchResult.cols(); x++) {
                double value = matchResult.get(y, x)[0];
                String strVal = "";
                if (value == Double.POSITIVE_INFINITY) {
                    strVal = " +In ";
                } else if (value == Double.NEGATIVE_INFINITY) {
                    strVal = " -In ";
                } else if (Double.isNaN(value)) {
                    strVal = " nan ";
                } else if (value > 99) {
                    strVal = " >99 (" + value + ")";
                } else if (value < -99) {
                    strVal = " <-99(" + value + ")";
                } else if (value == 0) {
                    strVal = "  0  ";
                } else if (value < 0) {
                    strVal = String.format("%.2f", value);
                } else {
                    strVal = String.format(" %.2f", value);
                }
                line.append(strVal).append(" ");
            }
            l.info(line);
        }

        Point matchLocationTopLeft;
        double matchPercentage = -1;
        // depending on the matchMethod, we are looking for the maximum or minimum
        if (matchMethod == Imgproc.TM_SQDIFF || matchMethod == Imgproc.TM_SQDIFF_NORMED) {
            matchLocationTopLeft = mmr.minLoc;
            matchPercentage = mmr.minVal * 100;
        } else {
            matchLocationTopLeft = mmr.maxLoc;
            matchPercentage = mmr.maxVal * 100;
        }

        // a value of +Infinity is not a match, it should be 1.0 maximum or something is wrong
        boolean matchFound = mmr.maxVal >= (minimumMatchThreshold) && mmr.maxVal <= 1;

        l.info("Matches {}%, best match top-left {}", matchPercentage, matchLocationTopLeft);

        // draw a red rectangle around the best match if it's better than a certain threshold
        if (matchFound) {
            Mat haystackWithRect = haystackImg.clone();
            // make sure we draw AROUND the needle/template, don't worry about the fact this might be out of bounds, it's not for the test images
            Point rectTopLeft = new Point(matchLocationTopLeft.x - 1, matchLocationTopLeft.y - 1);
            Point rectBottomRight = new Point(rectTopLeft.x + needleImg.cols() + 1, rectTopLeft.y + needleImg.rows() + 1);
            Scalar color = new Scalar(0, 255, 255); // yellow

            Imgproc.rectangle(haystackWithRect, rectTopLeft, rectBottomRight, color, 1);
            writeImage(prefix, "05_haystack_with_rect", haystackWithRect);
        } else {
            l.warn("not match found, matchPercentage {} is below minimumMatchThreshold {} or is +Infinity", matchPercentage, minimumMatchThreshold);
        }
        return "method " + matchMethod + " needle " + needle + " haystack " + haystack + " match percentage: " + matchPercentage + "%, top left location: " + matchLocationTopLeft;
    }

    public static void main(String[] args) {
        OpenCV.loadLocally();

        List<String> results = new ArrayList<>();

        //for (int matchMethod : new int[] { Imgproc.TM_CCORR_NORMED, Imgproc.TM_CCOEFF_NORMED, Imgproc.TM_SQDIFF_NORMED }) {
        for (int matchMethod : new int[] { Imgproc.TM_CCORR_NORMED }) {
            l.info("");
            l.info("Match method: {}", matchMethod);
            for (String haystack : new String[] {
                "haystack_blue_needle_on_all",
                "haystack_blue_needle_on_black",
                "haystack_blue_needle_on_green",
                "haystack_blue_needle_on_red",
                "haystack_blue_needle_on_white" }) {
                String needle = "needle_blue";
                results.add(tryMatch(needle, haystack, matchMethod));
            }
            for (String haystack : new String[] {
                "haystack_white_needle_on_all",
                "haystack_white_needle_on_black",
                "haystack_white_needle_on_green",
                "haystack_white_needle_on_purple",
                "haystack_white_needle_on_red",
                "haystack_white_needle_on_red_bottom_right" }) {
                String needle = "needle_white";
                results.add(tryMatch(needle, haystack, matchMethod));
            }
        }

        results.forEach(l::info);
    }
}