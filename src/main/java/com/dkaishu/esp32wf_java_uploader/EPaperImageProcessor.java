package com.dkaishu.esp32wf_java_uploader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class EPaperImageProcessor {

    // EPD显示器配置数组 [width, height, paletteIndex]
    public static final int[][] EPD_ARRAY = {
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

    // 调色板定义
    public static final int[][][] PALETTES = {
            {{0, 0, 0}, {255, 255, 255}}, // 0: BW
            {{0, 0, 0}, {255, 255, 255}, {127, 0, 0}}, // 1: BW + red
            {{0, 0, 0}, {255, 255, 255}, {127, 127, 127}}, // 2: BW + gray
            {{0, 0, 0}, {255, 255, 255}, {127, 127, 127}, {127, 0, 0}}, // 3: BW + gray + red
            {{0, 0, 0}, {255, 255, 255}}, // 4: BW (duplicate)
            {{0, 0, 0}, {255, 255, 255}, {220, 180, 0}}, // 5: BW + yellow
            {{0, 0, 0}}, // 6: Black only
            {{0, 0, 0}, {255, 255, 255}, {0, 255, 0}, {0, 0, 255}, {255, 0, 0}, {255, 255, 0}, {255, 128, 0}} // 7: 7-color
    };

    private int[][] currentPalette;
    private int epdIndex;
    public EPaperImageProcessor(int epdIndex) {
        this.epdIndex = epdIndex;
        // 初始化当前调色板 (7.5b V2使用索引23，对应paletteIndex=1)
        int paletteIndex = EPD_ARRAY[epdIndex][2];
        this.currentPalette = PALETTES[paletteIndex];
    }

    // 图像裁切方法
    public BufferedImage cropImage(BufferedImage src, Rectangle rect) {
        if (rect.x < 0) rect.x = 0;
        if (rect.y < 0) rect.y = 0;
        if (rect.x + rect.width > src.getWidth()) rect.width = src.getWidth() - rect.x;
        if (rect.y + rect.height > src.getHeight()) rect.height = src.getHeight() - rect.y;

        return src.getSubimage(rect.x, rect.y, rect.width, rect.height);
    }

    // 图像处理方法 (isLevel: true=阈值, false=抖动; isColor: 是否使用彩色)
    public BufferedImage processImage(BufferedImage srcImage, boolean isLevel, boolean isColor) {
        int width = srcImage.getWidth();
        int height = srcImage.getHeight();

        // 根据处理模式调整调色板
        int paletteIndex = EPD_ARRAY[epdIndex][2];
        if (!isColor) {
            paletteIndex = paletteIndex & 0xFE; // 强制使用黑白模式
        }
        this.currentPalette = PALETTES[paletteIndex];

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        if (isLevel) {
            // 电平处理（简单阈值）
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    Color original = new Color(srcImage.getRGB(x, y));
                    int nearest = findNearestColor(original.getRed(), original.getGreen(), original.getBlue());
                    int[] color = currentPalette[nearest];
                    result.setRGB(x, y, new Color(color[0], color[1], color[2]).getRGB());
                }
            }
        } else {
            // 抖动处理（Floyd-Steinberg算法优化版）
            int[][][] error = new int[2][width + 2][3]; // 双缓冲误差数组

            for (int y = 0; y < height; y++) {
                int aInd = y % 2;
                int bInd = (y + 1) % 2;

                // 清除当前行的误差
                for (int x = 0; x < width + 2; x++) {
                    error[bInd][x][0] = 0;
                    error[bInd][x][1] = 0;
                    error[bInd][x][2] = 0;
                }

                for (int x = 0; x < width; x++) {
                    Color original = new Color(srcImage.getRGB(x, y));

                    // 添加误差
                    int r = Math.max(0, Math.min(255, original.getRed() + error[aInd][x + 1][0]));
                    int g = Math.max(0, Math.min(255, original.getGreen() + error[aInd][x + 1][1]));
                    int b = Math.max(0, Math.min(255, original.getBlue() + error[aInd][x + 1][2]));

                    // 查找最近颜色
                    int nearest;
                    if (epdIndex == 25 || epdIndex == 37) { // 7色屏幕特殊处理
                        nearest = findNearest7Color(r, g, b);
                    } else {
                        nearest = findNearestColor(r, g, b);
                    }

                    int[] paletteColor = currentPalette[nearest];
                    result.setRGB(x, y, new Color(paletteColor[0], paletteColor[1], paletteColor[2]).getRGB());

                    // 计算误差
                    int errR = r - paletteColor[0];
                    int errG = g - paletteColor[1];
                    int errB = b - paletteColor[2];

                    // 扩散误差（使用与JavaScript相同的权重）
                    if (x == 0) {
                        addError(error, bInd, x + 1, errR, errG, errB, 7.0/32);
                        addError(error, bInd, x + 2, errR, errG, errB, 2.0/32);
                        addError(error, aInd, x + 2, errR, errG, errB, 7.0/32);
                    } else if (x == width - 1) {
                        addError(error, bInd, x, errR, errG, errB, 7.0/32);
                        addError(error, bInd, x + 1, errR, errG, errB, 9.0/32);
                    } else {
                        addError(error, bInd, x, errR, errG, errB, 3.0/32);
                        addError(error, bInd, x + 1, errR, errG, errB, 5.0/32);
                        addError(error, bInd, x + 2, errR, errG, errB, 1.0/32);
                        addError(error, aInd, x + 2, errR, errG, errB, 7.0/32);
                    }
                }
            }
        }
        return result;
    }

    private void addError(int[][][] error, int bufIndex, int x, int errR, int errG, int errB, double factor) {
        if (x >= 0 && x < error[0].length) {
            error[bufIndex][x][0] += (int)(errR * factor);
            error[bufIndex][x][1] += (int)(errG * factor);
            error[bufIndex][x][2] += (int)(errB * factor);
        }
    }

    // 提取特定颜色通道
    public BufferedImage extractColorChannel(BufferedImage image, int[] targetColor) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color pixel = new Color(image.getRGB(x, y));
                if (pixel.getRed() == targetColor[0] &&
                        pixel.getGreen() == targetColor[1] &&
                        pixel.getBlue() == targetColor[2]) {
                    // 匹配目标颜色
                    result.setRGB(x, y, image.getRGB(x, y));
                } else {
                    // 其他颜色设为白色
                    result.setRGB(x, y, Color.WHITE.getRGB());
                }
            }
        }

        return result;
    }

    // 7色屏幕专用颜色匹配
    private int findNearest7Color(int r, int g, int b) {
        if (r == 0 && g == 0 && b == 0) return 0; // 黑色
        if (r == 255 && g == 255 && b == 255) return 1; // 白色
        if (r == 0 && g == 255 && b == 0) return 2; // 绿色
        if (r == 0 && g == 0 && b == 255) return 3; // 蓝色
        if (r == 255 && g == 0 && b == 0) return 4; // 红色
        if (r == 255 && g == 255 && b == 0) return 5; // 黄色
        if (r == 255 && g == 128 && b == 0) return 6; // 橙色
        return 0; // 默认返回黑色
    }

    private int findNearestColor(int r, int g, int b) {
        int nearest = 0;
        int minError = Integer.MAX_VALUE;

        for (int i = 0; i < currentPalette.length; i++) {
            int[] paletteColor = currentPalette[i];
            int error = colorDistance(r, g, b, paletteColor[0], paletteColor[1], paletteColor[2]);

            if (error < minError) {
                minError = error;
                nearest = i;
            }
        }

        return nearest;
    }

    private int colorDistance(int r1, int g1, int b1, int r2, int g2, int b2) {
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return dr * dr + dg * dg + db * db;
    }



    public static void main(String[] args) {
        // 初始化当前调色板 (7.5b V2使用索引19，对应paletteIndex=1)
        int epdIndex = 23;
        EPaperImageProcessor processor = new EPaperImageProcessor(epdIndex);

        try {
            // 加载示例图片
            BufferedImage originalImage = ImageIO.read(new File("360px-7.5inch-e-Paper-B-1.jpg"));

            // 设置裁切区域 (x, y, width, height)
            int targetWidth = EPD_ARRAY[epdIndex][0];
            int targetHeight = EPD_ARRAY[epdIndex][1];
            Rectangle cropArea = new Rectangle(0, 0, targetWidth, targetHeight);

            // 处理图像
            BufferedImage cropped = processor.cropImage(originalImage, cropArea);
            // (isLevel: true=阈值, false=抖动; isColor: 是否使用彩色)
            BufferedImage processed = processor.processImage(cropped, false, true);

            // 提取红色和黑色通道
            BufferedImage redChannel = processor.extractColorChannel(processed, new int[]{127, 0, 0});
            BufferedImage blackChannel = processor.extractColorChannel(processed, new int[]{0, 0, 0});

            // 保存结果
            ImageIO.write(cropped, "png", new File("cropped.png"));
            ImageIO.write(processed, "png", new File("processed.png"));
            ImageIO.write(redChannel, "png", new File("red_channel.png"));
            ImageIO.write(blackChannel, "png", new File("black_channel.png"));

            System.out.println("处理完成，输出图像已保存");



        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}