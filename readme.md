Hi,

I've been breaking my head over this for hours, and I either don't understand how the mask template works, or there is some kind of bug. I tried with the last 5 versions of OpenCV (4.9.0, 4.8.1, 4.7.0, 4.6.0) but they all have the same behaviour.

Let me first explain what I'm trying to do, which is pretty straightforward: I want to find a template using a mask, using TM_CCORR_NORMED (to get values between 0 and 1), which means that I want to ignore some parts of the template (the transparent pixels).

This is the 20x20 template (needle):

<img src="readme_images/needle_white.png" width="40" />

It looks like a window, it has a few white pixels (the borders), and a few transparent pixels.

Trying to find it in a 50x50 image (haystack) and drawing a yellow border around it sometimes works fine.

4 examples that work fine:

center <img src="readme_images/haystack_white_needle_on_all.png" width="100" />
results in <img src="readme_images/method_3_find_needle_white_in_haystack_white_needle_on_all_05_haystack_with_rect.png" width="100" />
with heatmap <img src="readme_images/method_3_find_needle_white_in_haystack_white_needle_on_all_04_heatmap.png" width="62" />
and heatmap over the result <img src="readme_images/method_3_find_needle_white_in_haystack_white_needle_on_all_04_heatmap_overlay.png" width="100" />

black <img src="readme_images/haystack_white_needle_on_black.png" width="100" />
results in <img src="readme_images/method_3_find_needle_white_in_haystack_white_needle_on_black_05_haystack_with_rect.png" width="100" />
with heatmap <img src="readme_images/method_3_find_needle_white_in_haystack_white_needle_on_black_04_heatmap.png" width="62" />
and heatmap over the result <img src="readme_images/method_3_find_needle_white_in_haystack_white_needle_on_black_04_heatmap_overlay.png" width="100" />

green <img src="readme_images/haystack_white_needle_on_green.png" width="100" />
results in <img src="readme_images/method_3_find_needle_white_in_haystack_white_needle_on_green_05_haystack_with_rect.png" width="100" />
with an invalid heatmap

purple <img src="readme_images/haystack_white_needle_on_purple.png" width="100" />
results in <img src="readme_images/method_3_find_needle_white_in_haystack_white_needle_on_purple_05_haystack_with_rect.png" width="100" />
with an invalid heatmap

However, it fails to find the needle on the red part of the haystack:

<img src="readme_images/haystack_white_needle_on_red.png" width="100" />

I tried moving the red part (including needle) to a different location, but it seems color-related rather than location-related, because this also fails to find it there:

<img src="readme_images/haystack_white_needle_on_red_bottom_right.png" width="100" />

I tried to investigate the results, and it looks like there are some `+Infinity`, `-Infinity` (results of dividing by 0) and `NaN` (not sure why that occurs) entries in the normalized matrices.

Out of the 4 matches that seem to work fine:
- 2 are "really" OK: they have a normalized heatmap that makes sense, with values between 0 and 255.  These are where I draw the top-left corner of the yellow box (+- 1 pixel).
- 2 have a correct `maxLoc`, but the heatmaps for the other 2 matches are black (or at least they appear to be black because of wrong normalization)
The last 2 (red) don't find the correct `maxLoc`.

I added a lot of logging to get an idea of what is happening, so let me walk you through this use case on the red part where the matching fails:

<img src="readme_images/haystack_white_needle_on_red.png" width="100" />

Here is some code and logs:

This is how I read images:

```java
private static Mat readImage(String imageName, int codec) {
    Mat img = Imgcodecs.imread(new File(Main.class.getClassLoader().getResource("images/" + imageName + ".png").getFile()).getAbsolutePath(), codec);
    l.info("Read image '{}' using codec {}, result Mat: {}", imageName, codec, img);
    return img;
}
```

And this is how I write them to disk:

```java
private static void writeImage(String prefix, String imageName, Mat img) {
    String imageFilename = prefix + imageName + ".png";
    Imgcodecs.imwrite(imageFilename, img);
    l.info("Wrote image '{}'", imageFilename);
}
```

This is the code to create a mask out of the needle image:

```java
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
```
Docs say that "any non-0 value is not ignored", I used 0 and 255 but tried 0 and 1 too, result is the same.

First, I read both needle and haystack as `IMREAD_COLOR`, ignoring any alpha channel (transparency), these will be used for the template matching. I then re-read the needle INCLUDING the alpha channel, only to create a mask out of it. I write needle and heystack back to disk (to ensure I know what I'm working with), and I also write the mask to disk:

```java
// note that we ignore transparency here (4th alpha channel), we read as 3 channels
Mat needleImg = readImage(needle, Imgcodecs.IMREAD_COLOR);
writeImage(prefix, "01_needle", needleImg);

Mat haystackImg = readImage(haystack, Imgcodecs.IMREAD_COLOR);
writeImage(prefix, "02_haystack", haystackImg);

// this uses the 4th alpha channel
Mat mask = createMask(needle);
writeImage(prefix, "03_mask", mask);
```

This is what is logged:

Log:
```bash
2024-08-06 23:24:43 INFO Read image 'needle_white' using codec 1, result Mat: Mat [ 20*20*CV_8UC3, isCont=true, isSubmat=false, nativeObj=0x222f65fcbc0, dataAddr=0x222f65abdc0 ]
2024-08-06 23:24:43 INFO Wrote image 'method_3_find_needle_white_in_haystack_white_needle_on_red_01_needle.png'
2024-08-06 23:24:43 INFO Read image 'haystack_white_needle_on_red' using codec 1, result Mat: Mat [ 50*50*CV_8UC3, isCont=true, isSubmat=false, nativeObj=0x222f65fc220, dataAddr=0x222f5e2ab40 ]
2024-08-06 23:24:43 INFO Wrote image 'method_3_find_needle_white_in_haystack_white_needle_on_red_02_haystack.png'
2024-08-06 23:24:43 INFO Read image 'needle_white' using codec -1, result Mat: Mat [ 20*20*CV_8UC4, isCont=true, isSubmat=false, nativeObj=0x222f65fc300, dataAddr=0x222f6a810c0 ]
2024-08-06 23:24:43 INFO Wrote image 'method_3_find_needle_white_in_haystack_white_needle_on_red_03_mask.png'
```

The needle is 20x20:

<img src="readme_images/method_3_find_needle_white_in_haystack_white_needle_on_red_01_needle.png" width="40" />

The haystack is 50x50:

<img src="readme_images/method_3_find_needle_white_in_haystack_white_needle_on_red_02_haystack.png" width="100" />

The mask is 20x20:

<img src="readme_images/method_3_find_needle_white_in_haystack_white_needle_on_red_03_mask.png" width="40" />

I then use `matchTemplate`, and also try normalizing it (0-100) to understand what happens.

Code:
```java
Mat matchResult = new Mat();
Imgproc.matchTemplate(haystackImg, needleImg, matchResult, matchMethod, mask);
l.info("matchResult:           {}", matchResult);

Mat normalizedMatchResult = new Mat();
Core.normalize(matchResult, normalizedMatchResult, 0, 100, Core.NORM_MINMAX, CvType.CV_32F);
l.info("normalizedMatchResult: {}", matchResult);
```

Logs:
```bash
2024-08-06 23:24:43 INFO matchResult:           Mat [ 31*31*CV_32FC1, isCont=true, isSubmat=false, nativeObj=0x222f65fd790, dataAddr=0x222f6c9cb00 ]
2024-08-06 23:24:43 INFO normalizedMatchResult: Mat [ 31*31*CV_32FC1, isCont=true, isSubmat=false, nativeObj=0x222f65fd790, dataAddr=0x222f6c9cb00 ]
```

Nothing special so far. The heatmap generation also happens here, the black heatmap suggests that we're not staying withing the 0-255 boundaries, so the normalization fails. Code:

```java
Mat heatmap = new Mat();
Core.normalize(matchResult, heatmap, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);
writeImage(prefix, "04_heatmap", heatmap);
```

I then use `minMaxLoc` as documented, and I tried both on the original `matchResult` and my custom-normalized `normalizedMatchResult`.

Code:
```java
Core.MinMaxLocResult mmr = Core.minMaxLoc(matchResult);
l.info("minMaxLoc matchResult:           maxVal {}, maxLoc {}, minVal {}, minLoc {}", mmr.maxVal, mmr.maxLoc, mmr.minVal, mmr.minLoc);
Core.MinMaxLocResult mmr2 = Core.minMaxLoc(normalizedMatchResult);
l.info("minMaxLoc normalizedMatchResult: maxVal {}, maxLoc {}, minVal {}, minLoc {}", mmr2.maxVal, mmr2.maxLoc, mmr2.minVal, mmr2.minLoc);
```

Logs:
```bash
2024-08-06 23:24:43 INFO minMaxLoc matchResult:           maxVal Infinity, maxLoc {2.0, 27.0}, minVal -Infinity, minLoc {5.0, 30.0}
2024-08-06 23:24:43 INFO minMaxLoc normalizedMatchResult: maxVal -Infinity, maxLoc {0.0, 0.0}, minVal Infinity, minLoc {0.0, 0.0}
```

This isn't right, `maxVal` should be between 0.0 and 1.0 when using `TM_CCORR_NORMED`, but it's `Infinity`.

Also, `maxLoc {2.0, 27.0}` is not correct, it's close: it should be `{3.0, 28.0}`, but there is more.

I custom-printed the values in the result (the code is ugly, but it's readable output). I replace +Infinity and -Infinity with "+In" and "-In", I printed NaN as "nan", and I rounded the values I expect to be between 0 and 1.
This is the result:
```bash
2024-08-06 23:24:43 INFO   y  0:  0,66  0,66  0,74  0,82  0,74  0,66  0,66  0,66  0,66  0,66  0,66  0,70  0,75  0,69  0,62  0,62  0,62  0,62  0,62  0,62  0,64  0,67  0,62  0,58  0,58  0,58  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  1:  0,66  0,66  0,75  0,83  0,75  0,66  0,66  0,66  0,66  0,66  0,66  0,71  0,75  0,69  0,62  0,62  0,62  0,62  0,62  0,62  0,65  0,67  0,63  0,58  0,58  0,58  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  2:  0,74  0,75  0,84  0,92  0,84  0,75  0,74  0,74  0,73  0,72  0,72  0,76  0,81  0,74  0,67  0,66  0,65  0,65  0,64  0,63  0,65  0,68  0,63  0,58  0,58  0,58  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  3:  0,82  0,83  0,92  1,00  0,92  0,83  0,82  0,80  0,79  0,78  0,77  0,81  0,86  0,79  0,71  0,70  0,69  0,67  0,66  0,64  0,66  0,68  0,63  0,58  0,58  0,58  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  4:  0,74  0,75  0,84  0,92  0,84  0,75  0,74  0,74  0,73  0,72  0,72  0,76  0,81  0,74  0,67  0,66  0,65  0,65  0,64  0,63  0,65  0,68  0,63  0,58  0,58  0,58  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  5:  0,66  0,66  0,75  0,83  0,75  0,66  0,66  0,66  0,66  0,66  0,66  0,71  0,75  0,69  0,62  0,62  0,62  0,62  0,62  0,62  0,65  0,67  0,63  0,58  0,58  0,58  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  6:  0,66  0,66  0,74  0,82  0,74  0,66  0,66  0,66  0,66  0,66  0,66  0,70  0,75  0,69  0,62  0,62  0,62  0,62  0,62  0,62  0,64  0,67  0,62  0,58  0,58  0,58  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  7:  0,64  0,64  0,71  0,78  0,71  0,64  0,64  0,64  0,64  0,65  0,65  0,69  0,73  0,68  0,62  0,63  0,63  0,63  0,64  0,64  0,66  0,69  0,65  0,61  0,62  0,62  0,62  0,62  0,62  0,62  0,62 
2024-08-06 23:24:43 INFO   y  8:  0,61  0,61  0,68  0,75  0,68  0,61  0,61  0,62  0,62  0,63  0,64  0,68  0,72  0,67  0,63  0,63  0,64  0,65  0,65  0,66  0,68  0,71  0,68  0,65  0,66  0,66  0,67  0,67  0,67  0,67  0,67 
2024-08-06 23:24:43 INFO   y  9:  0,60  0,60  0,67  0,73  0,67  0,60  0,60  0,61  0,62  0,63  0,64  0,67  0,71  0,67  0,63  0,63  0,64  0,65  0,66  0,67  0,69  0,71  0,68  0,65  0,66  0,67  0,68  0,68  0,68  0,68  0,68 
2024-08-06 23:24:43 INFO   y 10:  0,59  0,59  0,65  0,71  0,65  0,59  0,59  0,61  0,62  0,63  0,64  0,67  0,70  0,66  0,62  0,63  0,65  0,66  0,67  0,67  0,69  0,71  0,69  0,66  0,67  0,68  0,69  0,69  0,69  0,69  0,69 
2024-08-06 23:24:43 INFO   y 11:  0,63  0,64  0,70  0,75  0,70  0,64  0,63  0,65  0,66  0,66  0,66  0,69  0,72  0,69  0,65  0,65  0,66  0,68  0,68  0,68  0,70  0,71  0,69  0,67  0,67  0,69  0,71  0,71  0,71  0,71  0,71 
2024-08-06 23:24:43 INFO   y 12:  0,67  0,68  0,74  0,79  0,74  0,68  0,67  0,68  0,69  0,69  0,69  0,72  0,74  0,71  0,67  0,67  0,68  0,69  0,69  0,69  0,70  0,71  0,69  0,67  0,68  0,70  0,72  0,72  0,72  0,72  0,72 
2024-08-06 23:24:43 INFO   y 13:  0,60  0,60  0,66  0,71  0,66  0,60  0,60  0,62  0,64  0,64  0,64  0,67  0,70  0,67  0,64  0,64  0,66  0,68  0,68  0,68  0,70  0,71  0,70  0,68  0,69  0,71  0,73  0,73  0,73  0,73  0,73 
2024-08-06 23:24:43 INFO   y 14:  0,51  0,51  0,57  0,62  0,57  0,51  0,51  0,55  0,58  0,58  0,59  0,63  0,66  0,63  0,60  0,61  0,64  0,66  0,67  0,68  0,70  0,71  0,70  0,69  0,69  0,72  0,74  0,74  0,74  0,74  0,74 
2024-08-06 23:24:43 INFO   y 15:  0,50  0,50  0,55  0,60  0,55  0,50  0,50  0,54  0,58  0,58  0,59  0,62  0,65  0,62  0,60  0,61  0,64  0,67  0,68  0,68  0,70  0,72  0,70  0,69  0,70  0,73  0,75  0,75  0,75  0,75  0,75 
2024-08-06 23:24:43 INFO   y 16:  0,47  0,47  0,51  0,55  0,51  0,47  0,47  0,51  0,56  0,57  0,58  0,61  0,63  0,62  0,60  0,61  0,65  0,68  0,69  0,70  0,72  0,74  0,73  0,72  0,73  0,76  0,79  0,79  0,79  0,79  0,79 
2024-08-06 23:24:43 INFO   y 17:  0,43  0,43  0,47  0,50  0,47  0,43  0,43  0,49  0,53  0,55  0,57  0,59  0,62  0,61  0,61  0,62  0,66  0,70  0,71  0,72  0,74  0,76  0,76  0,75  0,77  0,80  0,83  0,83  0,83  0,83  0,83 
2024-08-06 23:24:43 INFO   y 18:  0,42  0,42  0,45  0,47  0,45  0,42  0,42  0,48  0,53  0,55  0,57  0,59  0,61  0,61  0,61  0,62  0,66  0,70  0,72  0,73  0,74  0,76  0,76  0,76  0,77  0,81  0,84  0,84  0,84  0,84  0,84 
2024-08-06 23:24:43 INFO   y 19:  0,41  0,41  0,42  0,44  0,42  0,41  0,41  0,47  0,53  0,55  0,57  0,58  0,60  0,60  0,61  0,62  0,67  0,71  0,72  0,73  0,75  0,76  0,76  0,77  0,78  0,81  0,85  0,85  0,85  0,85  0,85 
2024-08-06 23:24:43 INFO   y 20:  0,43  0,44  0,45  0,46  0,45  0,44  0,43  0,50  0,55  0,57  0,58  0,59  0,60  0,61  0,62  0,63  0,68  0,72  0,73  0,74  0,75  0,76  0,77  0,77  0,78  0,82  0,86  0,86  0,86  0,86  0,86 
2024-08-06 23:24:43 INFO   y 21:  0,45  0,46  0,47  0,47  0,47  0,46  0,45  0,52  0,57  0,58  0,59  0,60  0,61  0,62  0,63  0,64  0,69  0,73  0,74  0,75  0,75  0,76  0,77  0,78  0,79  0,83  0,87  0,87  0,87  0,87  0,87 
2024-08-06 23:24:43 INFO   y 22:  0,37  0,38  0,38  0,39  0,38  0,38  0,37  0,46  0,53  0,54  0,55  0,57  0,58  0,59  0,61  0,62  0,67  0,72  0,73  0,74  0,75  0,76  0,77  0,78  0,79  0,84  0,88  0,88  0,88  0,88  0,88 
2024-08-06 23:24:43 INFO   y 23:  0,27  0,27  0,27  0,27  0,27  0,27  0,27  0,39  0,48  0,50  0,52  0,53  0,55  0,57  0,58  0,60  0,66  0,72  0,73  0,74  0,75  0,76  0,78  0,79  0,80  0,85  0,89  0,89  0,89  0,89  0,89 
2024-08-06 23:24:43 INFO   y 24:  0,26  0,26  0,26  0,26  0,26  0,26  0,26  0,38  0,48  0,50  0,51  0,53  0,55  0,57  0,58  0,60  0,66  0,72  0,73  0,75  0,76  0,77  0,78  0,79  0,81  0,85  0,90  0,90  0,90  0,90  0,90 
2024-08-06 23:24:43 INFO   y 25:  0,18  0,18  0,18  0,18  0,18  0,18  0,18  0,34  0,45  0,48  0,50  0,52  0,54  0,57  0,59  0,60  0,67  0,73  0,75  0,76  0,78  0,79  0,81  0,82  0,84  0,89  0,93  0,93  0,93  0,93  0,93 
2024-08-06 23:24:43 INFO   y 26: -0,00 -0,00 -0,00  nan   nan   nan   nan   0,30  0,43  0,46  0,49  0,51  0,54  0,57  0,59  0,61  0,68  0,75  0,76  0,78  0,80  0,82  0,83  0,85  0,87  0,92  0,97  0,97  0,97  0,97  0,97 
2024-08-06 23:24:43 INFO   y 27:  nan   nan   +In   nan   nan   nan   nan   0,30  0,43  0,46  0,49  0,51  0,54  0,57  0,59  0,61  0,68  0,75  0,76  0,78  0,80  0,82  0,83  0,85  0,87  0,92  0,97  0,97  0,97  0,97  0,97 
2024-08-06 23:24:43 INFO   y 28:   0    0,00  0,00  nan   0,00  nan   nan   0,30  0,43  0,46  0,49  0,51  0,54  0,57  0,59  0,61  0,68  0,75  0,76  0,78  0,80  0,82  0,83  0,85  0,87  0,92  0,97  0,97  0,97  0,97  0,97 
2024-08-06 23:24:43 INFO   y 29:  nan   nan   nan   nan   nan   nan   nan   0,30  0,43  0,46  0,49  0,51  0,54  0,57  0,59  0,61  0,68  0,75  0,76  0,78  0,80  0,82  0,83  0,85  0,87  0,92  0,97  0,97  0,97  0,97  0,97 
2024-08-06 23:24:43 INFO   y 30:  0,00  0,00  0,00 -0,00   0    -In   -In   0,30  0,43  0,46  0,49  0,51  0,54  0,57  0,59  0,61  0,68  0,75  0,76  0,78  0,80  0,82  0,83  0,85  0,87  0,92  0,97  0,97  0,97  0,97  0,97
```
As you can see, the `maxLoc` of `{2.0, 27.0}` comes from the `+Infinity` from `y 27` in the 3d column. I think the infinities/nan's are throwing off the `minMaxLoc`.

Since this example is still close, let's look at the one where I moved the red part to the bottom right:

<img src="readme_images/haystack_white_needle_on_red_bottom_right.png" width="100" />

It has similar Infinity/NaN fields, but the best match location is even further off:
```bash
2024-08-06 23:24:43 INFO minMaxLoc matchResult:           maxVal Infinity, maxLoc {1.0, 30.0}, minVal -Infinity, minLoc {6.0, 28.0}
2024-08-06 23:24:43 INFO minMaxLoc normalizedMatchResult: maxVal -Infinity, maxLoc {0.0, 0.0}, minVal Infinity, minLoc {0.0, 0.0}
```

In the bottom right of the matrix, similar Infinity/NaN things are happening:

```bash
2024-08-06 23:24:43 INFO   y  0:  0,97  0,97  0,97  0,97  0,97  0,97  0,97  0,93  0,90  0,89  0,88  0,87  0,86  0,85  0,84  0,83  0,79  0,75  0,74  0,73  0,72  0,71  0,69  0,68  0,67  0,62  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  1:  0,97  0,97  0,97  0,97  0,97  0,97  0,97  0,93  0,90  0,89  0,88  0,87  0,86  0,85  0,84  0,83  0,79  0,75  0,74  0,73  0,72  0,71  0,69  0,68  0,67  0,62  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  2:  0,97  0,97  0,97  0,97  0,97  0,97  0,97  0,93  0,90  0,89  0,88  0,87  0,86  0,85  0,84  0,83  0,79  0,75  0,74  0,73  0,72  0,71  0,69  0,68  0,67  0,62  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  3:  0,97  0,97  0,97  0,97  0,97  0,97  0,97  0,93  0,90  0,89  0,88  0,87  0,86  0,85  0,84  0,83  0,79  0,75  0,74  0,73  0,72  0,71  0,69  0,68  0,67  0,62  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  4:  0,97  0,97  0,97  0,97  0,97  0,97  0,97  0,93  0,90  0,89  0,88  0,87  0,86  0,85  0,84  0,83  0,79  0,75  0,74  0,73  0,72  0,71  0,69  0,68  0,67  0,62  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  5:  0,97  0,97  0,97  0,97  0,97  0,97  0,97  0,93  0,90  0,89  0,88  0,87  0,86  0,85  0,84  0,83  0,79  0,75  0,74  0,73  0,72  0,71  0,69  0,68  0,67  0,62  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  6:  0,97  0,97  0,97  0,97  0,97  0,97  0,97  0,93  0,90  0,89  0,88  0,87  0,86  0,85  0,84  0,83  0,79  0,75  0,74  0,73  0,72  0,71  0,69  0,68  0,67  0,62  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  7:  0,92  0,92  0,92  0,92  0,92  0,92  0,92  0,89  0,85  0,85  0,84  0,83  0,82  0,81  0,81  0,80  0,76  0,73  0,72  0,71  0,70  0,69  0,68  0,67  0,66  0,62  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  8:  0,87  0,87  0,87  0,87  0,87  0,87  0,87  0,84  0,81  0,80  0,79  0,79  0,78  0,78  0,77  0,77  0,73  0,70  0,69  0,69  0,68  0,67  0,67  0,66  0,66  0,62  0,58  0,58  0,58  0,58  0,58 
2024-08-06 23:24:43 INFO   y  9:  0,85  0,85  0,85  0,85  0,85  0,85  0,85  0,82  0,79  0,79  0,79  0,78  0,78  0,78  0,77  0,77  0,74  0,71  0,71  0,71  0,70  0,70  0,69  0,69  0,69  0,66  0,63  0,63  0,63  0,63  0,63 
2024-08-06 23:24:43 INFO   y 10:  0,83  0,83  0,83  0,83  0,83  0,83  0,83  0,81  0,78  0,78  0,78  0,78  0,78  0,77  0,77  0,77  0,75  0,73  0,73  0,72  0,72  0,72  0,72  0,72  0,72  0,70  0,67  0,68  0,68  0,68  0,67 
2024-08-06 23:24:43 INFO   y 11:  0,82  0,82  0,82  0,82  0,82  0,82  0,82  0,79  0,77  0,77  0,77  0,77  0,76  0,76  0,76  0,75  0,73  0,71  0,71  0,71  0,71  0,70  0,70  0,70  0,69  0,67  0,65  0,65  0,66  0,65  0,65 
2024-08-06 23:24:43 INFO   y 12:  0,80  0,80  0,80  0,80  0,80  0,80  0,80  0,78  0,76  0,76  0,76  0,76  0,75  0,75  0,74  0,74  0,71  0,69  0,69  0,70  0,69  0,69  0,68  0,67  0,67  0,64  0,62  0,63  0,64  0,63  0,62 
2024-08-06 23:24:43 INFO   y 13:  0,78  0,78  0,78  0,78  0,78  0,78  0,78  0,76  0,75  0,75  0,76  0,75  0,74  0,73  0,73  0,72  0,70  0,68  0,69  0,70  0,69  0,68  0,67  0,67  0,66  0,64  0,62  0,64  0,66  0,64  0,62 
2024-08-06 23:24:43 INFO   y 14:  0,76  0,76  0,76  0,76  0,76  0,76  0,76  0,75  0,73  0,74  0,75  0,74  0,73  0,72  0,72  0,71  0,69  0,68  0,69  0,70  0,69  0,67  0,67  0,66  0,65  0,64  0,62  0,65  0,67  0,65  0,62 
2024-08-06 23:24:43 INFO   y 15:  0,75  0,75  0,75  0,75  0,75  0,75  0,75  0,73  0,72  0,73  0,74  0,73  0,72  0,71  0,70  0,70  0,68  0,67  0,69  0,71  0,69  0,67  0,66  0,65  0,65  0,63  0,62  0,65  0,69  0,65  0,62 
2024-08-06 23:24:43 INFO   y 16:  0,68  0,68  0,68  0,68  0,68  0,68  0,68  0,67  0,66  0,68  0,69  0,68  0,67  0,67  0,66  0,66  0,65  0,64  0,67  0,69  0,67  0,65  0,65  0,64  0,64  0,63  0,62  0,66  0,70  0,66  0,62 
2024-08-06 23:24:43 INFO   y 17:  0,61  0,61  0,61  0,61  0,61  0,61  0,61  0,60  0,60  0,62  0,64  0,63  0,62  0,62  0,62  0,62  0,61  0,61  0,64  0,68  0,66  0,63  0,63  0,63  0,63  0,63  0,62  0,67  0,71  0,67  0,62 
2024-08-06 23:24:43 INFO   y 18:  0,59  0,59  0,59  0,59  0,59  0,59  0,59  0,59  0,58  0,61  0,63  0,63  0,62  0,62  0,63  0,63  0,63  0,64  0,68  0,72  0,69  0,67  0,67  0,68  0,69  0,69  0,69  0,74  0,79  0,74  0,69 
2024-08-06 23:24:43 INFO   y 19:  0,57  0,57  0,57  0,57  0,57  0,57  0,57  0,57  0,57  0,60  0,63  0,62  0,61  0,62  0,63  0,64  0,65  0,67  0,71  0,75  0,73  0,71  0,72  0,72  0,73  0,74  0,75  0,81  0,86  0,81  0,75 
2024-08-06 23:24:43 INFO   y 20:  0,54  0,54  0,54  0,54  0,54  0,54  0,54  0,54  0,55  0,58  0,62  0,61  0,60  0,60  0,61  0,61  0,62  0,63  0,68  0,72  0,70  0,68  0,68  0,69  0,69  0,70  0,71  0,76  0,81  0,76  0,71 
2024-08-06 23:24:43 INFO   y 21:  0,51  0,51  0,51  0,51  0,51  0,51  0,51  0,52  0,53  0,57  0,61  0,59  0,58  0,58  0,58  0,58  0,59  0,60  0,65  0,69  0,67  0,64  0,64  0,65  0,65  0,65  0,66  0,72  0,77  0,72  0,66 
2024-08-06 23:24:43 INFO   y 22:  0,49  0,49  0,49  0,49  0,49  0,49  0,49  0,50  0,51  0,56  0,60  0,58  0,56  0,57  0,57  0,57  0,58  0,59  0,65  0,69  0,67  0,64  0,64  0,64  0,64  0,65  0,66  0,72  0,78  0,72  0,66 
2024-08-06 23:24:43 INFO   y 23:  0,46  0,46  0,46  0,46  0,46  0,46  0,46  0,48  0,50  0,54  0,59  0,57  0,55  0,55  0,55  0,55  0,57  0,58  0,64  0,70  0,66  0,63  0,63  0,63  0,63  0,65  0,66  0,73  0,79  0,73  0,66 
2024-08-06 23:24:43 INFO   y 24:  0,43  0,43  0,43  0,43  0,43  0,43  0,43  0,45  0,48  0,53  0,58  0,55  0,53  0,53  0,53  0,53  0,56  0,58  0,64  0,70  0,66  0,62  0,62  0,62  0,62  0,64  0,66  0,74  0,80  0,74  0,66 
2024-08-06 23:24:43 INFO   y 25:  0,30  0,30  0,30  0,30  0,30  0,30  0,30  0,34  0,38  0,45  0,52  0,49  0,47  0,47  0,48  0,49  0,51  0,54  0,62  0,69  0,65  0,60  0,61  0,61  0,62  0,64  0,66  0,74  0,82  0,74  0,66 
2024-08-06 23:24:43 INFO   y 26:  nan   nan   nan   nan   nan   nan   nan   0,18  0,26  0,37  0,45  0,42  0,40  0,41  0,42  0,43  0,47  0,50  0,59  0,67  0,63  0,59  0,59  0,60  0,61  0,64  0,66  0,75  0,83  0,75  0,66 
2024-08-06 23:24:43 INFO   y 27:  0,00  0,00  0,00  0,00  0,00  nan   nan   0,18  0,26  0,37  0,46  0,44  0,41  0,44  0,46  0,48  0,52  0,56  0,65  0,73  0,69  0,65  0,66  0,68  0,69  0,72  0,75  0,84  0,92  0,84  0,75 
2024-08-06 23:24:43 INFO   y 28:  0,00  0,00  0,00  nan    0    nan   -In   0,18  0,26  0,37  0,46  0,45  0,43  0,46  0,49  0,52  0,57  0,61  0,70  0,79  0,75  0,70  0,72  0,74  0,76  0,80  0,83  0,92  1,00  0,92  0,83 
2024-08-06 23:24:43 INFO   y 29:  nan   nan   nan   nan    0    nan   nan   0,18  0,26  0,37  0,46  0,44  0,41  0,44  0,46  0,48  0,52  0,56  0,65  0,73  0,69  0,65  0,66  0,68  0,69  0,72  0,75  0,84  0,92  0,84  0,75 
2024-08-06 23:24:43 INFO   y 30:  nan   +In   nan   nan   nan   nan   nan   0,18  0,26  0,37  0,45  0,42  0,40  0,41  0,42  0,43  0,47  0,50  0,59  0,67  0,63  0,59  0,59  0,60  0,61  0,64  0,66  0,75  0,83  0,75  0,66 
```

Again, `maxLoc {1.0, 30.0}` (which is wrong) is caused by the `+Infinity` on line 30 in column 2.

I tried with blue iso white needle, which fails on both red and white.