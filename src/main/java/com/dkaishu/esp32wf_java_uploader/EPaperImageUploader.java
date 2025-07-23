package com.dkaishu.esp32wf_java_uploader;


import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

public class EPaperImageUploader {
    private static final String SERVER_URL = "http://192.168.0.111/";
    private static final int[][][] palArr = {
            {{0, 0, 0}, {255, 255, 255}},
            {{0, 0, 0}, {255, 255, 255}, {127, 0, 0}},
            {{0, 0, 0}, {255, 255, 255}, {127, 127, 127}},
            {{0, 0, 0}, {255, 255, 255}, {127, 127, 127}, {127, 0, 0}},
            {{0, 0, 0}, {255, 255, 255}},
            {{0, 0, 0}, {255, 255, 255}, {220, 180, 0}},
            {{0, 0, 0}},
            {{0, 0, 0}, {255, 255, 255}, {0, 255, 0}, {0, 0, 255}, {255, 0, 0}, {255, 255, 0}, {255, 128, 0}}
    };

    private static final int[][] epdArr = {
            {200, 200, 0}, {200, 200, 3}, {152, 152, 5},
            {122, 250, 0}, {104, 212, 1}, {104, 212, 5}, {104, 212, 0},
            {176, 264, 0}, {176, 264, 1},
            {128, 296, 0}, {128, 296, 1}, {128, 296, 5}, {128, 296, 0},
            {400, 300, 0}, {400, 300, 1}, {400, 300, 5},
            {600, 448, 0}, {600, 448, 1}, {600, 448, 5},
            {640, 384, 0}, {640, 384, 1}, {640, 384, 5},
            {800, 480, 0}, {800, 480, 1}, {880, 528, 1},
            {600, 448, 7}, {880, 528, 0}, {280, 480, 0},
            {152, 296, 0}, {648, 480, 1}, {128, 296, 1},
            {200, 200, 1}, {104, 214, 1}, {128, 296, 0},
            {400, 300, 1}, {152, 296, 1}, {648, 480, 0},
            {640, 400, 7}, {176, 264, 1}, {122, 250, 0},
            {122, 250, 1}, {240, 360, 0}, {176, 264, 0},
            {122, 250, 0}, {400, 300, 0}, {960, 680, 0},
            {800, 480, 0}, {128, 296, 1}, {960, 680, 1}
    };


    public static void main(String[] args) throws Exception {

        String imagePath = "测试1.jpg";
        //设备型号index
        int epdIndex = 23;

        BufferedImage image = ImageIO.read(new File(imagePath));
        //0: 单色电平处理 1: 彩色电平处理 2: 单色抖动处理 3: 彩色抖动处理
        int[] pixels = processImage(image, epdIndex, 3);
        uploadImage(pixels, epdIndex);
    }

    /**
     * 上传图像（缩放）
     * 0: 单色电平处理
     * 1: 彩色电平处理
     * 2: 单色抖动处理
     * 3: 彩色抖动处理
     */
    private static int[] processImage(BufferedImage image, int epdIndex, int processType) {
        int width = epdArr[epdIndex][0];
        int height = epdArr[epdIndex][1];
        int palInd = epdArr[epdIndex][2];

        // 根据处理类型调整调色板索引
        boolean isColor = (processType == 1 || processType == 3); // Level: color 或 Dithering: color
        if (!isColor) {
            palInd = palInd & 0xFE;
        }

        int[][] curPal = palArr[palInd];
        BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        scaledImage.getGraphics().drawImage(image, 0, 0, width, height, null);

        int[] pixels = new int[width * height];

        if (processType == 0 || processType == 1) { // 电平处理
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = scaledImage.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    if (epdIndex == 25 || epdIndex == 37) { // 7-color E-Paper
                        pixels[y * width + x] = getNear7Color(r, g, b, curPal);
                    } else {
                        pixels[y * width + x] = getNear(r, g, b, curPal);
                    }
                }
            }
        } else { // 抖动处理（Floyd-Steinberg算法优化版）
            double[][][] errArr = new double[2][width][3];
            for (int i = 0; i < width; i++) {
                Arrays.fill(errArr[1][i], 0.0);
            }

            int aInd = 0, bInd = 1;

            for (int y = 0; y < height; y++) {
                // 交换行索引
                int temp = aInd;
                aInd = bInd;
                bInd = temp;

                // 重置下一行误差
                for (int i = 0; i < width; i++) {
                    Arrays.fill(errArr[bInd][i], 0.0);
                }

                for (int x = 0; x < width; x++) {
                    int rgb = scaledImage.getRGB(x, y);
                    double r = (rgb >> 16) & 0xFF;
                    double g = (rgb >> 8) & 0xFF;
                    double b = rgb & 0xFF;

                    // 添加当前行误差
                    r += errArr[aInd][x][0];
                    g += errArr[aInd][x][1];
                    b += errArr[aInd][x][2];

                    // 限制在[0,255]范围
                    r = Math.min(255, Math.max(0, r));
                    g = Math.min(255, Math.max(0, g));
                    b = Math.min(255, Math.max(0, b));

                    int colorIndex;
                    if (epdIndex == 25 || epdIndex == 37) {
                        colorIndex = getNear7Color((int) r, (int) g, (int) b, curPal);
                    } else {
                        colorIndex = getNear((int) r, (int) g, (int) b, curPal);
                    }

                    pixels[y * width + x] = colorIndex;

                    // 获取目标颜色值
                    int[] colVal = curPal[colorIndex];
                    // 计算误差
                    double rErr = r - colVal[0];
                    double gErr = g - colVal[1];
                    double bErr = b - colVal[2];

                    // 根据位置应用不同权重的误差扩散
                    if (x == 0) {
                        addError(errArr, aInd, x + 1, rErr, gErr, bErr, 7); // 右
                        addError(errArr, bInd, x, rErr, gErr, bErr, 7);     // 下
                        addError(errArr, bInd, x + 1, rErr, gErr, bErr, 2); // 右下
                    } else if (x == width - 1) {
                        addError(errArr, bInd, x - 1, rErr, gErr, bErr, 7); // 左下
                        addError(errArr, bInd, x, rErr, gErr, bErr, 9);     // 正下
                    } else {
                        addError(errArr, bInd, x - 1, rErr, gErr, bErr, 3); // 左下
                        addError(errArr, bInd, x, rErr, gErr, bErr, 5);     // 正下
                        addError(errArr, bInd, x + 1, rErr, gErr, bErr, 1); // 右下
                        addError(errArr, aInd, x + 1, rErr, gErr, bErr, 7); // 右
                    }
                }
            }
        }
        return pixels;
    }


    /**
     * 上传图像（裁切）
     */
    private static int[] processImage(BufferedImage image, int epdIndex, int processType, int cropX, int cropY, int cropW, int cropH) {
        // 获取目标尺寸
        int width = epdArr[epdIndex][0];
        int height = epdArr[epdIndex][1];
        int palInd = epdArr[epdIndex][2];

        // 检查尺寸是否合法（与JS代码一致）
        if (cropW < 3 || cropH < 3) {
            throw new IllegalArgumentException("Image is too small");
        }

        // 根据处理类型调整调色板索引
        boolean isColor = (processType == 1 || processType == 3);
        if (!isColor) {
            palInd = palInd & 0xFE;
        }

        int[][] curPal = palArr[palInd];

        // 创建目标图像
        BufferedImage targetImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = targetImage.createGraphics();

        // 填充背景（与JS代码一致）
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                targetImage.setRGB(i, j, ((i + j) % 2 == 0) ? 0xFFFFFF : 0x000000);
            }
        }

        // 计算源图像裁切区域
        int srcWidth = image.getWidth();
        int srcHeight = image.getHeight();

        // 确保裁切区域在源图像范围内（与JS代码一致）
        int srcX = Math.max(0, Math.min(cropX, srcWidth - 1));
        int srcY = Math.max(0, Math.min(cropY, srcHeight - 1));
        int srcW = Math.min(cropW, srcWidth - srcX);
        int srcH = Math.min(cropH, srcHeight - srcY);

        // 裁切并缩放源图像
        if (srcW > 0 && srcH > 0) {
            BufferedImage croppedImage = image.getSubimage(srcX, srcY, srcW, srcH);
            g2d.drawImage(croppedImage, 0, 0, width, height, null);
        }

        g2d.dispose();

        int[] pixels = new int[width * height];

        if (processType == 0 || processType == 1) { // 电平处理
            for (int py = 0; py < height; py++) {
                for (int px = 0; px < width; px++) {
                    int rgb = targetImage.getRGB(px, py);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    if (epdIndex == 25 || epdIndex == 37) { // 7-color E-Paper
                        pixels[py * width + px] = getNear7Color(r, g, b, curPal);
                    } else {
                        pixels[py * width + px] = getNear(r, g, b, curPal);
                    }
                }
            }
        } else { // 抖动处理// 抖动处理
            double[][][] errArr = new double[2][width][3];
            for (int i = 0; i < width; i++) {
                Arrays.fill(errArr[1][i], 0.0);
            }

            int aInd = 0, bInd = 1;

            for (int y = 0; y < height; y++) {
                // 交换行索引
                int temp = aInd;
                aInd = bInd;
                bInd = temp;

                // 重置下一行误差
                for (int i = 0; i < width; i++) {
                    Arrays.fill(errArr[bInd][i], 0.0);
                }

                for (int x = 0; x < width; x++) {
                    int rgb = targetImage.getRGB(x, y);
                    double r = (rgb >> 16) & 0xFF;
                    double g = (rgb >> 8) & 0xFF;
                    double b = rgb & 0xFF;

                    // 添加当前行误差
                    r += errArr[aInd][x][0];
                    g += errArr[aInd][x][1];
                    b += errArr[aInd][x][2];

                    // 限制在[0,255]范围
                    r = Math.min(255, Math.max(0, r));
                    g = Math.min(255, Math.max(0, g));
                    b = Math.min(255, Math.max(0, b));

                    int colorIndex;
                    if (epdIndex == 25 || epdIndex == 37) {
                        colorIndex = getNear7Color((int) r, (int) g, (int) b, curPal);
                    } else {
                        colorIndex = getNear((int) r, (int) g, (int) b, curPal);
                    }

                    pixels[y * width + x] = colorIndex;

                    // 获取目标颜色值
                    int[] colVal = curPal[colorIndex];
                    // 计算误差
                    double rErr = r - colVal[0];
                    double gErr = g - colVal[1];
                    double bErr = b - colVal[2];

                    // 根据位置应用不同权重的误差扩散
                    if (x == 0) {
                        addError(errArr, aInd, x + 1, rErr, gErr, bErr, 7); // 右
                        addError(errArr, bInd, x, rErr, gErr, bErr, 7);     // 下
                        addError(errArr, bInd, x + 1, rErr, gErr, bErr, 2); // 右下
                    } else if (x == width - 1) {
                        addError(errArr, bInd, x - 1, rErr, gErr, bErr, 7); // 左下
                        addError(errArr, bInd, x, rErr, gErr, bErr, 9);     // 正下
                    } else {
                        addError(errArr, bInd, x - 1, rErr, gErr, bErr, 3); // 左下
                        addError(errArr, bInd, x, rErr, gErr, bErr, 5);     // 正下
                        addError(errArr, bInd, x + 1, rErr, gErr, bErr, 1); // 右下
                        addError(errArr, aInd, x + 1, rErr, gErr, bErr, 7); // 右
                    }
                }
            }
        }

        return pixels;
    }


    private static void addError(double[][][] errArr, int row, int x, double rErr, double gErr, double bErr, int k) {
        if (x < 0 || x >= errArr[row].length) return;

        double factor = k / 32.0; // 与原JS一致的比例因子
        errArr[row][x][0] += rErr * factor;
        errArr[row][x][1] += gErr * factor;
        errArr[row][x][2] += bErr * factor;
    }

    private static int getNear(int r, int g, int b, int[][] palette) {
        int index = 0;
        double minError = getError(r, g, b, palette[0]);

        for (int i = 1; i < palette.length; i++) {
            double error = getError(r, g, b, palette[i]);
            if (error < minError) {
                minError = error;
                index = i;
            }
        }
        return index;
    }

    private static int getNear7Color(int r, int g, int b, int[][] palette) {
        // Special handling for 7-color E-Paper
        if (r == 0 && g == 0 && b == 0) return 0; // Black
        if (r == 255 && g == 255 && b == 255) return 1; // White
        if (r == 0 && g == 255 && b == 0) return 2; // Green
        if (r == 0 && g == 0 && b == 255) return 3; // Blue
        if (r == 255 && g == 0 && b == 0) return 4; // Red
        if (r == 255 && g == 255 && b == 0) return 5; // Yellow
        if (r == 255 && g == 128 && b == 0) return 6; // Orange
        return 7;
    }

    private static double getError(int r, int g, int b, int[] color) {
        double dr = r - color[0];
        double dg = g - color[1];
        double db = b - color[2];
        return dr * dr + dg * dg + db * db;
    }

    private static void uploadImage(int[] pixels, int epdIndex) throws Exception {
        System.out.println("Uploading image...");

        if ((epdIndex == 3) || (epdIndex == 39) || (epdIndex == 43)) { // 2.13
            sendCommand("EPD" + (char) (epdIndex < 26 ? epdIndex + 97 : epdIndex - 26 + 65) + "_");
            uploadLine(pixels, 0);
            sendCommand("SHOW_");
        } else if (epdIndex == 40) { // 2.13 B V4
            sendCommand("EPD" + (char) (epdIndex < 26 ? epdIndex + 97 : epdIndex - 26 + 65) + "_");
            uploadLine(pixels, 0);
            sendCommand("NEXT_");
            uploadLine(pixels, 3);
            sendCommand("SHOW_");
        } else if ((epdIndex == 0) || (epdIndex == 3) || (epdIndex == 6) || (epdIndex == 7) ||
                (epdIndex == 9) || (epdIndex == 12) || (epdIndex == 16) || (epdIndex == 19) ||
                (epdIndex == 22) || (epdIndex == 26) || (epdIndex == 27) || (epdIndex == 28)) {
            sendCommand("EPD" + (char) (epdIndex < 26 ? epdIndex + 97 : epdIndex - 26 + 65) + "_");
            uploadData(pixels, 0);
            sendCommand("SHOW_");
        } else if (epdIndex > 15 && epdIndex < 22) {
            sendCommand("EPD" + (char) (epdIndex + 97) + "_");
            uploadData16(pixels);
            sendCommand("SHOW_");
        } else if (epdIndex == 25 || epdIndex == 37) { // 7 colors
            sendCommand("EPD" + (char) (epdIndex < 26 ? epdIndex + 97 : epdIndex - 26 + 65) + "_");
            uploadData4(pixels);
            sendCommand("SHOW_");
        } else {
            sendCommand("EPD" + (char) (epdIndex < 26 ? epdIndex + 97 : epdIndex - 26 + 65) + "_");
            if (epdIndex == 23) {
                uploadData(pixels, 0);
            } else {
                uploadData(pixels, (epdIndex == 1 || epdIndex == 12) ? -1 : 0);
            }
            sendCommand("NEXT_");
            int palInd = epdArr[epdIndex][2]; // 获取当前屏幕的调色板索引
            int c = (palInd == 1) ? 2 : 3; // 三色屏用2(红)，其他保持3
            uploadData(pixels, c);
            sendCommand("SHOW_");
        }

        System.out.println("Upload complete!");
    }

    // 图像裁切
    public static BufferedImage cropImage(BufferedImage src, Rectangle rect) {
        if (rect.x < 0) rect.x = 0;
        if (rect.y < 0) rect.y = 0;
        if (rect.x + rect.width > src.getWidth()) rect.width = src.getWidth() - rect.x;
        if (rect.y + rect.height > src.getHeight()) rect.height = src.getHeight() - rect.y;

        return src.getSubimage(rect.x, rect.y, rect.width, rect.height);
    }

    private static void sendCommand(String cmd) throws Exception {
//        System.out.println("Sending command: " + cmd);
        URL url = new URL(SERVER_URL + cmd);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.getResponseCode();
        conn.disconnect();
    }

    private static void uploadData(int[] pixels, int c) throws Exception {
        int index = 0;
        while (index < pixels.length) {
            StringBuilder sb = new StringBuilder();
            while (index < pixels.length && sb.length() < 1000) {
                if (c == -1) { // 16-bit data
                    int v = 0;
                    for (int i = 0; i < 16; i += 2) {
                        if (index < pixels.length) {
                            v |= (pixels[index++] << i);
                        }
                    }
                    sb.append(wordToStr(v));
                } else { // 8-bit data
                    int v = 0;
                    for (int i = 0; i < 8; i++) {
                        if (index < pixels.length && pixels[index++] != c) {
                            v |= (128 >> i);
                        }
                    }
                    sb.append(byteToStr(v));
                }
            }
//            System.out.println("uploadData:\n" + sb.toString() + wordToStr(sb.length()) + "LOAD_" + "\n");
            sendCommand(sb.toString() + wordToStr(sb.length()) + "LOAD_");
            System.out.println("Progress: " + (index * 100 / pixels.length) + "%");
        }
    }

    private static void uploadData16(int[] pixels) throws Exception {
        int index = 0;
        while (index < pixels.length) {
            StringBuilder sb = new StringBuilder();
            while (index < pixels.length && sb.length() < 1000) {
                int v = 0;
                for (int i = 0; i < 16; i += 2) {
                    if (index < pixels.length) {
                        v |= (pixels[index++] << i);
                    }
                }
                sb.append(wordToStr(v));
            }
            sendCommand(sb.toString() + wordToStr(sb.length()) + "LOAD_");
            System.out.println("Progress: " + (index * 100 / pixels.length) + "%");
        }
    }

    private static void uploadData4(int[] pixels) throws Exception {
        int index = 0;
        while (index < pixels.length) {
            StringBuilder sb = new StringBuilder();
            while (index < pixels.length && sb.length() < 1000) {
                int v = 0;
                for (int i = 0; i < 16; i += 4) {
                    if (index < pixels.length) {
                        v |= (pixels[index++] << i);
                    }
                }
                sb.append(wordToStr(v));
            }
            sendCommand(sb.toString() + wordToStr(sb.length()) + "LOAD_");
            System.out.println("Progress: " + (index * 100 / pixels.length) + "%");
        }
    }

    private static void uploadLine(int[] pixels, int c) throws Exception {
        int index = 0;
        while (index < pixels.length) {
            StringBuilder sb = new StringBuilder();
            while (sb.length() < 1000) {
                int x = 0;
                while (x < 122) {
                    int v = 0;
                    for (int i = 0; i < 8 && x < 122; i++, x++) {
                        if (index < pixels.length && pixels[index++] != c) {
                            v |= (128 >> i);
                        }
                    }
                    sb.append(byteToStr(v));
                }
            }
            sendCommand(sb.toString() + wordToStr(sb.length()) + "LOAD_");
            System.out.println("Progress: " + (index * 100 / pixels.length) + "%");
        }
    }

    private static String byteToStr(int v) {
        return String.valueOf((char) ((v & 0xF) + 97)) +
                String.valueOf((char) (((v >> 4) & 0xF) + 97));
    }

    private static String wordToStr(int v) {
        return byteToStr(v & 0xFF) + byteToStr((v >> 8) & 0xFF);
    }
}