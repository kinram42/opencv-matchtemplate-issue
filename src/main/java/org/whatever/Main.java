package org.whatever;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import nu.pattern.OpenCV;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
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
    private static final Path DIR_ROOT = Path.of("C:\\data\\poc");
    private static final Path DIR_INPUT_NEEDLES = DIR_ROOT.resolve("input_needles");
    private static final Path DIR_INPUT_HAYSTACKS = DIR_ROOT.resolve("input_haystacks");
    public static final Path DIR_OUTPUT = DIR_ROOT.resolve("output");

    private static Mat readImage(Path image, int codec) {
        Mat img = Imgcodecs.imread(image.toString(), codec);
        l.debug("Read image '{}' using codec {}, {}", image, codec, matInfoAsString("imread", img));
        return img;
    }

    private static Path writeImage(String filename, Mat img) {
        l.debug("Write image '{}'", filename);
        Imgcodecs.imwrite(filename, img);
        return Path.of(filename);
    }

    private static String matInfoAsString(String description, Mat m) {
        return String.format("Mat info for %s: %s", description, m);
    }

    // currently not used, was to check if this was better (no)
    public static Mat createMaskBinary(Path image) {
        Mat img = readImage(image, Imgcodecs.IMREAD_UNCHANGED);

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
        l.debug(matInfoAsString("mask", mask));
        return mask;
    }

    public static Mat createMask_CV_32F(Path image) {
        Mat img = readImage(image, Imgcodecs.IMREAD_UNCHANGED);

        Mat mask = new Mat(img.size(), CvType.CV_32F);
        for (int y = 0; y < img.rows(); y++) {
            for (int x = 0; x < img.cols(); x++) {
                // OpenCV uses BGRA, alpha is 4th channel
                mask.put(y, x, img.get(y, x)[3]);
            }
        }
        l.debug(matInfoAsString("mask", mask));
        return mask;
    }

    public static String matchMethodToString(int matchMethod) {
        return switch (matchMethod) {
            case Imgproc.TM_SQDIFF -> "TM_SQDIFF";
            case Imgproc.TM_SQDIFF_NORMED -> "TM_SQDIFF_NORMED";
            case Imgproc.TM_CCORR -> "TM_CCORR";
            case Imgproc.TM_CCORR_NORMED -> "TM_CCORR_NORMED";
            case Imgproc.TM_CCOEFF -> "TM_CCOEFF";
            case Imgproc.TM_CCOEFF_NORMED -> "TM_CCOEFF_NORMED";
            default -> "Unknown";
        };
    }

    private static String getOpenCvFilenameForWriting(Path directory, String prefix, String imageName) {
        return directory.resolve(prefix + imageName + ".png").toString();
    }

    private static void match(DataSetEntry dataSetEntry, Point needleLocation) {
        Path needle = dataSetEntry.needle;
        Path haystack = dataSetEntry.haystack;
        int matchMethod = dataSetEntry.matchMethod;
        Path resultsDirectory = dataSetEntry.dirTemplateMatchOngoingUndecided;

        String prefix = String.format("method_%s_find_%s_in_%s_",
            matchMethodToString(matchMethod), needle.getFileName().toString(), haystack.getFileName().toString());

        // note that we ignore transparency here (4th alpha channel), we read as 3 channels
        Mat needleImg = readImage(needle, Imgcodecs.IMREAD_COLOR);
        dataSetEntry.result.outputImages.add(writeImage(getOpenCvFilenameForWriting(resultsDirectory, prefix, "01_needle"), needleImg));

        Mat haystackImg = readImage(haystack, Imgcodecs.IMREAD_COLOR);
        //drawMat("haystackImg", haystackImg);
        dataSetEntry.result.outputImages.add(writeImage(getOpenCvFilenameForWriting(resultsDirectory, prefix, "02_haystack"), haystackImg));

        // this uses the 4th alpha channel
        Mat mask = createMask_CV_32F(needle);
        dataSetEntry.result.outputImages.add(writeImage(getOpenCvFilenameForWriting(resultsDirectory, prefix, "03_mask"), mask));

        // try to match
        Mat matchResult = new Mat();
        Imgproc.matchTemplate(haystackImg, needleImg, matchResult, matchMethod, mask);
        l.debug("matchResult: {}", matchResult);

        Mat heatmap = new Mat();
        Core.normalize(matchResult, heatmap, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
        dataSetEntry.result.outputImages.add(writeImage(getOpenCvFilenameForWriting(resultsDirectory, prefix, "04_heatmap"), heatmap));

        // find the min/max in the histogram
        Core.MinMaxLocResult mmr = Core.minMaxLoc(matchResult);
        l.debug("minMaxLoc matchResult: maxVal {}, maxLoc {}, minVal {}, minLoc {}", mmr.maxVal, mmr.maxLoc, mmr.minVal, mmr.minLoc);

        // depending on the matchMethod, we are looking for the maximum or minimum
        if (matchMethod == Imgproc.TM_SQDIFF || matchMethod == Imgproc.TM_SQDIFF_NORMED) {
            dataSetEntry.result.closestLoc = mmr.minLoc;
            dataSetEntry.result.closestVal = mmr.minVal;
       } else {
            dataSetEntry.result.closestLoc = mmr.maxLoc;
            dataSetEntry.result.closestVal = mmr.maxVal;
        }

        dataSetEntry.result.matchActual = needleLocation.equals(dataSetEntry.result.closestLoc);

        // draw a rectangle around the best match
        Mat haystackWithRect = haystackImg.clone();
        // make sure we draw AROUND the needle/template, don't worry about the fact this might be out of bounds, it's not for the test images
        Point rectTopLeft = new Point(dataSetEntry.result.closestLoc.x - 1, dataSetEntry.result.closestLoc.y - 1);
        Point rectBottomRight = new Point(rectTopLeft.x + needleImg.cols() + 1, rectTopLeft.y + needleImg.rows() + 1);
        Scalar color = new Scalar(0, 255, 255); // yellow

        Imgproc.rectangle(haystackWithRect, rectTopLeft, rectBottomRight, color, 1);
        dataSetEntry.result.outputImages.add(writeImage(getOpenCvFilenameForWriting(resultsDirectory, prefix, "05_haystack_with_rect"), haystackWithRect));
    }

    public static List<Path> getAllFilesInDirectory(Path directoryPath) throws IOException {
        List<Path> fileList = new ArrayList<>();
        Files.walk(directoryPath)
             .filter(Files::isRegularFile)
             .forEach(fileList::add);

        l.debug("Found {} files in directory {}:", fileList.size(), directoryPath);
        fileList.forEach(file -> l.debug("  " + file.getFileName()));
        return fileList;
    }

    public static void createAndCleanDirectory(Path directoryPath) {
        l.debug("Creating/cleaning directory {}", directoryPath.toAbsolutePath());
        try {
            Files.createDirectories(directoryPath);
            FileUtils.cleanDirectory(directoryPath.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void generateHaystacks(Path inputNeedle, Point needleLocation, Path dirShouldNotMatch, Path dirShouldMatch) {
        l.debug("Generating haystacks");
        try {
            Mat foreground = readImage(inputNeedle, Imgcodecs.IMREAD_UNCHANGED);
            for (Path inputHaystack : getAllFilesInDirectory(DIR_INPUT_HAYSTACKS)) {
                // we don't need alpha channel from haystack, assume no transparency
                Mat background = readImage(inputHaystack, Imgcodecs.IMREAD_COLOR);
                // create the original haystack/background
                writeImage(dirShouldNotMatch.resolve(inputHaystack.getFileName()).toString(), background);

                // based on https://stackoverflow.com/questions/40895785/using-opencv-to-overlay-transparent-image-onto-another-image
                for (int y = 0; y < foreground.rows(); y++) {
                    for (int x = 0; x < foreground.cols(); x++) {
                        int backgroundX = (int)needleLocation.x + x;
                        int backgroundY = (int)needleLocation.y + y;
                        double[] data = {0, 0, 0};
                        for (int channel = 0; channel < 3; channel++) {
                            // normalize alpha from 0-255 to 0-1
                            double alphaForeground = foreground.get(y, x)[3] / 255.0;
                            data[channel] = (int)(alphaForeground * foreground.get(y, x)[channel] + background.get(backgroundY, backgroundX)[channel] * (1 - alphaForeground));
                        }
                        background.put(backgroundY, backgroundX, data);
                    }
                }
                // create the haystack with the needle overlay
                String filename = String.format("%s_over_%s", FilenameUtils.getBaseName(inputNeedle.toString()), inputHaystack.getFileName());
                writeImage(dirShouldMatch.resolve(filename).toString(), background);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        // this is a location we choose to overlay the transparent needle in the haystacks
        Point needleLocation = new Point(860, 440);

        List<Integer> methods = List.of(
            Imgproc.TM_SQDIFF,
            Imgproc.TM_SQDIFF_NORMED/*,
            Imgproc.TM_CCORR,
            Imgproc.TM_CCORR_NORMED,
            Imgproc.TM_CCOEFF,
            Imgproc.TM_CCOEFF_NORMED*/);

        l.debug("Loading OpenCV");
        OpenCV.loadLocally();

        // prepare a list of containers to do the work and gather the results
        List<DataSetEntry> dataSetEntries = new ArrayList<>();

        createAndCleanDirectory(DIR_OUTPUT);

        // loop over all needle images
        var inputNeedles = getAllFilesInDirectory(DIR_INPUT_NEEDLES);
        l.debug("Looping over {} input needles", inputNeedles.size());
        var j = 1;
        for (Path inputNeedle : inputNeedles) {
            l.debug("Input needle {}/{}: {}", j++, inputNeedles.size(), FilenameUtils.getBaseName(inputNeedle.toString()));

            Path needleOutputRoot = DIR_OUTPUT.resolve("needle_" + FilenameUtils.getBaseName(inputNeedle.toString()));

            // prepare dirs
            Path dirShouldMatch = needleOutputRoot.resolve("should_match");
            Path dirShouldNotMatch = needleOutputRoot.resolve("should_not_match");
            Path dirTemplateMatchOngoingUndecided = needleOutputRoot.resolve("tm_ongoing_undecided");
            Path dirTmExpectedMatchActualMatch = needleOutputRoot.resolve("tm_expected_match_actual_match");
            Path dirTmExpectedMatchActualNoMatch = needleOutputRoot.resolve("tm_expected_match_actual_no_match");
            Path dirTmExpectedNoMatchActualMatch = needleOutputRoot.resolve("tm_expected_no_match_actual_match");
            Path dirTmExpectedNoMatchActualNoMatch = needleOutputRoot.resolve("tm_expected_no_match_actual_no_match");

            l.debug("Creating output dirs for needle");
            var dirsToClean = List.of(
                dirShouldMatch,
                dirShouldNotMatch,
                dirTemplateMatchOngoingUndecided,
                dirTmExpectedMatchActualMatch,
                dirTmExpectedMatchActualNoMatch,
                dirTmExpectedNoMatchActualMatch,
                dirTmExpectedNoMatchActualNoMatch);

            dirsToClean.forEach(Main::createAndCleanDirectory);

            generateHaystacks(inputNeedle, needleLocation, dirShouldNotMatch, dirShouldMatch);

            // generate input data for matching
            l.debug("Adding data for matching");
            var shouldMatchImages = getAllFilesInDirectory(dirShouldMatch);
            var shouldNotMatchImages = getAllFilesInDirectory(dirShouldNotMatch);
            for (int method : methods) {
                shouldMatchImages.forEach(haystack -> {
                    var dataSetEntry = new DataSetEntry();
                    dataSetEntry.needle = inputNeedle;
                    dataSetEntry.matchMethod = method;
                    dataSetEntry.haystack = haystack;
                    dataSetEntry.matchExpected = true;
                    dataSetEntry.dirTemplateMatchOngoingUndecided = dirTemplateMatchOngoingUndecided;
                    dataSetEntry.dirTemplateMatchExpectedMatchActualMatch = dirTmExpectedMatchActualMatch;
                    dataSetEntry.dirTemplateMatchExpectedMatchActualNoMatch = dirTmExpectedMatchActualNoMatch;
                    dataSetEntry.dirTemplateMatchExpectedNoMatchActualMatch = dirTmExpectedNoMatchActualMatch;
                    dataSetEntry.dirTemplateMatchExpectedNoMatchActualNoMatch = dirTmExpectedNoMatchActualNoMatch;
                    dataSetEntries.add(dataSetEntry);
                });
                shouldNotMatchImages.forEach(haystack -> {
                    var dataSetEntry = new DataSetEntry();
                    dataSetEntry.needle = inputNeedle;
                    dataSetEntry.matchMethod = method;
                    dataSetEntry.haystack = haystack;
                    dataSetEntry.matchExpected = false;
                    dataSetEntry.dirTemplateMatchOngoingUndecided = dirTemplateMatchOngoingUndecided;
                    dataSetEntry.dirTemplateMatchExpectedMatchActualMatch = dirTmExpectedMatchActualMatch;
                    dataSetEntry.dirTemplateMatchExpectedMatchActualNoMatch = dirTmExpectedMatchActualNoMatch;
                    dataSetEntry.dirTemplateMatchExpectedNoMatchActualMatch = dirTmExpectedNoMatchActualMatch;
                    dataSetEntry.dirTemplateMatchExpectedNoMatchActualNoMatch = dirTmExpectedNoMatchActualNoMatch;
                    dataSetEntries.add(dataSetEntry);
                });
            }
        }

        l.debug("Preparing CSV results file");
        CsvUtils.prepareFile();

        // perform the template matching on all dataSetEntries
        l.info("Performing template matching on {} data entries", dataSetEntries.size());
        int i = 1;
        for (DataSetEntry dataSetEntry : dataSetEntries) {
            String prefix = String.format("%s/%s", i, dataSetEntries.size());
            l.info("{} Processing: needle '{}' in haystack '{}' with method {}", prefix, dataSetEntry.needle.getFileName(), dataSetEntry.haystack.getFileName(), matchMethodToString(dataSetEntry.matchMethod));
            match(dataSetEntry, needleLocation);
            l.debug("{} Result: closestVal {}, closestLoc {}", prefix, dataSetEntry.result.closestVal, dataSetEntry.result.closestLoc);

            // move from ongoing to correct/wrong (false positive/negative
            for (Path outputImage : dataSetEntry.result.outputImages) {
                Path destinationRoot;
                if (dataSetEntry.matchExpected) {
                    if (dataSetEntry.result.matchActual) {
                        destinationRoot = dataSetEntry.dirTemplateMatchExpectedMatchActualMatch;
                    } else {
                        destinationRoot = dataSetEntry.dirTemplateMatchExpectedMatchActualNoMatch;
                    }
                } else {
                    if (dataSetEntry.result.matchActual) {
                        destinationRoot = dataSetEntry.dirTemplateMatchExpectedNoMatchActualMatch;
                    } else {
                        destinationRoot = dataSetEntry.dirTemplateMatchExpectedNoMatchActualNoMatch;
                    }
                }
                Path destinationFile = destinationRoot.resolve(outputImage.getFileName());

                l.debug("{} Moving {} to {}", prefix, outputImage, destinationFile);
                FileUtils.moveFile(outputImage.toFile(), destinationFile.toFile());
            }
            CsvUtils.writeLineToFile(dataSetEntry.toCsvString());
            i++;
        }
    }
}