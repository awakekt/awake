/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * Counts the distinct colours in an image and fails below a minimum, to tell a drawn frame from a
 * blank one. A single-file program, so a CI runner needs only the JDK:
 *
 * <pre>java tools/PngColours.java &lt;file.png&gt; [minimum]</pre>
 */
public final class PngColours {
    private PngColours() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: java tools/PngColours.java <file.png> [minimum]");
            System.exit(2);
        }
        File file = new File(args[0]);
        int minimum = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            System.err.println(file + " is not an image the JDK reads");
            System.exit(1);
        }
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                colours.add(image.getRGB(x, y));
            }
        }
        System.out.println(file + ": " + image.getWidth() + "x" + image.getHeight() + ", " + colours.size() + " distinct colours");
        if (colours.size() < minimum) {
            System.err.println(file + " has " + colours.size() + " colour(s), fewer than " + minimum + ": nothing was drawn");
            System.exit(1);
        }
    }
}
