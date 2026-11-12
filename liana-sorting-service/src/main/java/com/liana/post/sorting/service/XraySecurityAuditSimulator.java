package com.liana.post.sorting.service;

import com.liana.post.sorting.model.dto.SortingDetectedLabelResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

@Service
public class XraySecurityAuditSimulator {
    private static final List<String> IMAGE_SUFFIXES = List.of(".jpg", ".jpeg", ".png", ".bmp", ".webp");
    private static final String DATA_URL_PREFIX = "data:image/jpeg;base64,";
    private final String xrayRoot;

    public XraySecurityAuditSimulator(@Value("${sorting.xray.root:x-ray}") String xrayRoot) {
        this.xrayRoot = xrayRoot;
    }

    public XrayAuditSample sample() {
        Path root = resolveXrayRoot();
        if (root == null) {
            return XrayAuditSample.empty();
        }

        Path imageDir = root.resolve("test").resolve("images");
        List<Path> images = listImages(imageDir);
        if (images.isEmpty()) {
            return XrayAuditSample.empty();
        }

        Path image = images.get(ThreadLocalRandom.current().nextInt(images.size()));
        List<String> names = loadClassNames(root.resolve("data.yaml"));
        List<SortingDetectedLabelResponse> labels = loadLabels(root.resolve("test").resolve("labels"), image, names);
        String annotated = annotateImage(image, labels).orElse(null);
        return new XrayAuditSample(image.getFileName().toString(), annotated, labels);
    }

    private Path resolveXrayRoot() {
        List<Path> candidates = new ArrayList<>();
        if (StringUtils.hasText(xrayRoot)) {
            candidates.add(Path.of(xrayRoot));
        }
        candidates.addAll(List.of(
                Path.of("x-ray"),
                Path.of("..", "x-ray"),
                Path.of("..", "..", "x-ray")
        ));
        for (Path candidate : candidates) {
            Path resolved = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(resolved.resolve("test").resolve("images"))
                    && Files.isRegularFile(resolved.resolve("data.yaml"))) {
                return resolved;
            }
        }
        return null;
    }

    private List<Path> listImages(Path imageDir) {
        if (!Files.isDirectory(imageDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(imageDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> IMAGE_SUFFIXES.contains(fileSuffix(path)))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private List<String> loadClassNames(Path dataYaml) {
        if (!Files.isRegularFile(dataYaml)) {
            return List.of();
        }
        try {
            String content = Files.readString(dataYaml, StandardCharsets.UTF_8);
            int namesIndex = content.indexOf("names:");
            if (namesIndex < 0) {
                return List.of();
            }
            int open = content.indexOf('[', namesIndex);
            int close = content.indexOf(']', open);
            if (open < 0 || close < 0 || close <= open) {
                return List.of();
            }
            String body = content.substring(open + 1, close);
            List<String> names = new ArrayList<>();
            for (String token : body.split(",")) {
                String name = token.trim().replace("'", "").replace("\"", "");
                if (StringUtils.hasText(name)) {
                    names.add(name);
                }
            }
            return names;
        } catch (IOException ex) {
            return List.of();
        }
    }

    private List<SortingDetectedLabelResponse> loadLabels(Path labelDir, Path image, List<String> names) {
        Path labelPath = labelDir.resolve(stripExtension(image.getFileName().toString()) + ".txt");
        if (!Files.isRegularFile(labelPath)) {
            return List.of();
        }
        try {
            List<SortingDetectedLabelResponse> labels = new ArrayList<>();
            for (String line : Files.readAllLines(labelPath, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!StringUtils.hasText(trimmed)) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                if (parts.length < 5) {
                    continue;
                }
                int classId = Integer.parseInt(parts[0]);
                SortingDetectedLabelResponse label = new SortingDetectedLabelResponse();
                label.setLabel(classId >= 0 && classId < names.size() ? names.get(classId) : String.valueOf(classId));
                label.setX(Double.parseDouble(parts[1]));
                label.setY(Double.parseDouble(parts[2]));
                label.setWidth(Double.parseDouble(parts[3]));
                label.setHeight(Double.parseDouble(parts[4]));
                label.setConfidence(parts.length >= 6 ? Double.parseDouble(parts[5]) : 1.0D);
                labels.add(label);
            }
            return labels;
        } catch (RuntimeException | IOException ex) {
            return List.of();
        }
    }

    private Optional<String> annotateImage(Path imagePath, List<SortingDetectedLabelResponse> labels) {
        try {
            BufferedImage source = ImageIO.read(imagePath.toFile());
            if (source == null) {
                return Optional.empty();
            }
            BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, Color.BLACK, null);
            graphics.setStroke(new BasicStroke(Math.max(2F, image.getWidth() / 320F)));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(14, image.getWidth() / 45)));

            for (SortingDetectedLabelResponse label : labels) {
                drawLabel(graphics, image, label);
            }
            graphics.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", output);
            return Optional.of(DATA_URL_PREFIX + Base64.getEncoder().encodeToString(output.toByteArray()));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private void drawLabel(Graphics2D graphics, BufferedImage image, SortingDetectedLabelResponse label) {
        int boxWidth = Math.max(1, (int) Math.round(label.getWidth() * image.getWidth()));
        int boxHeight = Math.max(1, (int) Math.round(label.getHeight() * image.getHeight()));
        int left = clamp((int) Math.round((label.getX() * image.getWidth()) - (boxWidth / 2.0D)), 0, image.getWidth() - 1);
        int top = clamp((int) Math.round((label.getY() * image.getHeight()) - (boxHeight / 2.0D)), 0, image.getHeight() - 1);
        int right = clamp(left + boxWidth, left + 1, image.getWidth());
        int bottom = clamp(top + boxHeight, top + 1, image.getHeight());
        Color color = colorFor(label.getLabel());
        graphics.setColor(color);
        graphics.drawRect(left, top, right - left, bottom - top);

        String text = label.getLabel();
        int textWidth = graphics.getFontMetrics().stringWidth(text) + 10;
        int textHeight = graphics.getFontMetrics().getHeight() + 4;
        int labelTop = Math.max(0, top - textHeight);
        graphics.fillRect(left, labelTop, Math.min(textWidth, image.getWidth() - left), textHeight);
        graphics.setColor(Color.WHITE);
        graphics.drawString(text, left + 5, labelTop + textHeight - 7);
    }

    private Color colorFor(String label) {
        if ("Nonmetallic_Lighter".equals(label)) {
            return new Color(220, 38, 38);
        }
        if ("Portable_Charger_1".equals(label) || "Portable_Charger_2".equals(label)) {
            return new Color(234, 88, 12);
        }
        if ("Laptop".equals(label) || "Mobile_Phone".equals(label) || "Tablet".equals(label)) {
            return new Color(37, 99, 235);
        }
        return new Color(5, 150, 105);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String stripExtension(String value) {
        int index = value.lastIndexOf('.');
        return index < 0 ? value : value.substring(0, index);
    }

    private String fileSuffix(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index);
    }

    public static class XrayAuditSample {
        private final String sourceImageName;
        private final String imageDataUrl;
        private final List<SortingDetectedLabelResponse> labels;

        public XrayAuditSample(String sourceImageName, String imageDataUrl, List<SortingDetectedLabelResponse> labels) {
            this.sourceImageName = sourceImageName;
            this.imageDataUrl = imageDataUrl;
            this.labels = labels;
        }

        static XrayAuditSample empty() {
            return new XrayAuditSample(null, null, List.of());
        }

        public String getSourceImageName() { return sourceImageName; }
        public String getImageDataUrl() { return imageDataUrl; }
        public List<SortingDetectedLabelResponse> getLabels() { return labels; }
    }
}
