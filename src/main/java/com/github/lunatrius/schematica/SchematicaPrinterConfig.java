package com.github.lunatrius.schematica;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.Minecraft;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class SchematicaPrinterConfig {
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "schematica_survival.properties";
    private static final String KEY_REQUIRE_EMERALD = "printer.requireEmerald";
    private static final String KEY_BLOCKS_PER_EMERALD = "printer.blocksPerEmerald";
    private static final String KEY_REQUIRE_FOOD = "printer.requireFood";
    private static final String KEY_FOOD_HUNGER_MULTIPLIER = "printer.foodHungerMultiplier";
    private static final String KEY_AUTO_UNLOAD_PROJECTION_AFTER_PRINT = "printer.autoUnloadProjectionAfterPrint";
    private static final String KEY_CLIENT_PROJECTION_UPLOAD = "printer.clientProjectionUpload";
    private static final String KEY_SCHEMATIC_STRICT_REMAP = "schematic.strictRemap";
    private static final String KEY_PRINTER_ROTATION_USE_BLOCK_FACING_API = "printer.rotation.useBlockFacingApi";
    private static final String KEY_PRINTER_ROTATION_DEBUG_LOG = "printer.rotation.debugLog";
    private static final String KEY_PROJECTION_GHOST_ALPHA_SOLID = "projection.ghostAlphaSolid";
    private static final String KEY_PROJECTION_GHOST_ALPHA_TRANSLUCENT = "projection.ghostAlphaTranslucent";
    private static final String KEY_PROJECTION_LINE_ALPHA = "projection.lineAlpha";
    private static final String KEY_PRINTER_GUI_STATE_PREFIX = "printer.guiState.";
    private static final String KEY_PRINTER_GUI_SELECTED_FILE_SUFFIX = ".selectedFile";
    private static final String KEY_PRINTER_GUI_ROTATION_SUFFIX = ".rotation";
    private static final String KEY_PRINTER_GUI_MIRROR_X_SUFFIX = ".mirrorX";
    private static final String KEY_PRINTER_GUI_MIRROR_Z_SUFFIX = ".mirrorZ";
    private static final String KEY_PRINTER_GUI_LAST_APPLIED_SIGNATURE_SUFFIX = ".lastAppliedSignature";
    // ManyLib-compatible keys (dot + camelCase naming rule)
    private static final String MANYLIB_KEY_REQUIRE_EMERALD = "schematicaSurvival.printer.requireEmerald";
    private static final String MANYLIB_KEY_BLOCKS_PER_EMERALD = "schematicaSurvival.printer.blocksPerEmerald";
    private static final String MANYLIB_KEY_REQUIRE_FOOD = "schematicaSurvival.printer.requireFood";
    private static final String MANYLIB_KEY_FOOD_HUNGER_MULTIPLIER = "schematicaSurvival.printer.foodHungerMultiplier";
    private static final String MANYLIB_SECTION_VALUES = "Values";
    private static final boolean DEFAULT_REQUIRE_EMERALD = true;
    private static final int DEFAULT_BLOCKS_PER_EMERALD = 32;
    private static final boolean DEFAULT_REQUIRE_FOOD = false;
    private static final float DEFAULT_FOOD_HUNGER_MULTIPLIER = 1.0F;
    private static final boolean DEFAULT_AUTO_UNLOAD_PROJECTION_AFTER_PRINT = true;
    private static final boolean DEFAULT_CLIENT_PROJECTION_UPLOAD = false;
    private static final boolean DEFAULT_SCHEMATIC_STRICT_REMAP = false;
    private static final boolean DEFAULT_PRINTER_ROTATION_USE_BLOCK_FACING_API = true;
    private static final boolean DEFAULT_PRINTER_ROTATION_DEBUG_LOG = false;
    private static final int DEFAULT_EMERALD_ITEM_ID = 388;
    private static final int DEFAULT_EMERALD_SUBTYPE = 0;
    private static final float DEFAULT_PROJECTION_GHOST_ALPHA_SOLID = 0.35F;
    private static final float DEFAULT_PROJECTION_GHOST_ALPHA_TRANSLUCENT = 0.28F;
    private static final float DEFAULT_PROJECTION_LINE_ALPHA = 0.65F;

    private static boolean requireEmerald = DEFAULT_REQUIRE_EMERALD;
    private static int blocksPerEmerald = DEFAULT_BLOCKS_PER_EMERALD;
    private static boolean requireFood = DEFAULT_REQUIRE_FOOD;
    private static float foodHungerMultiplier = DEFAULT_FOOD_HUNGER_MULTIPLIER;
    private static boolean autoUnloadProjectionAfterPrint = DEFAULT_AUTO_UNLOAD_PROJECTION_AFTER_PRINT;
    private static boolean clientProjectionUpload = DEFAULT_CLIENT_PROJECTION_UPLOAD;
    private static boolean schematicStrictRemap = DEFAULT_SCHEMATIC_STRICT_REMAP;
    private static boolean printerRotationUseBlockFacingApi = DEFAULT_PRINTER_ROTATION_USE_BLOCK_FACING_API;
    private static boolean printerRotationDebugLog = DEFAULT_PRINTER_ROTATION_DEBUG_LOG;
    private static float projectionGhostAlphaSolid = DEFAULT_PROJECTION_GHOST_ALPHA_SOLID;
    private static float projectionGhostAlphaTranslucent = DEFAULT_PROJECTION_GHOST_ALPHA_TRANSLUCENT;
    private static float projectionLineAlpha = DEFAULT_PROJECTION_LINE_ALPHA;
    private static final Map<String, String> printerGuiSelectedFileByPos = new HashMap<String, String>();
    private static final Map<String, Integer> printerGuiRotationByPos = new HashMap<String, Integer>();
    private static final Map<String, Boolean> printerGuiMirrorXByPos = new HashMap<String, Boolean>();
    private static final Map<String, Boolean> printerGuiMirrorZByPos = new HashMap<String, Boolean>();
    private static final Map<String, String> printerGuiLastAppliedSignatureByPos = new HashMap<String, String>();

    private SchematicaPrinterConfig() {
    }

    public static synchronized void load() {
        Properties properties = new Properties();
        File file = resolveConfigFile();
        if (file.exists()) {
            try {
                FileInputStream input = new FileInputStream(file);
                try {
                    properties.load(input);
                } finally {
                    input.close();
                }
            } catch (IOException ignored) {
            }
        }

        requireEmerald = parseBoolean(properties.getProperty(KEY_REQUIRE_EMERALD), DEFAULT_REQUIRE_EMERALD);
        blocksPerEmerald = clampPositive(parseInt(properties.getProperty(KEY_BLOCKS_PER_EMERALD), DEFAULT_BLOCKS_PER_EMERALD), DEFAULT_BLOCKS_PER_EMERALD);
        requireFood = parseBoolean(properties.getProperty(KEY_REQUIRE_FOOD), DEFAULT_REQUIRE_FOOD);
        foodHungerMultiplier = clampNonNegative(
                parseFloat(properties.getProperty(KEY_FOOD_HUNGER_MULTIPLIER), DEFAULT_FOOD_HUNGER_MULTIPLIER),
                DEFAULT_FOOD_HUNGER_MULTIPLIER);
        autoUnloadProjectionAfterPrint = parseBoolean(
                properties.getProperty(KEY_AUTO_UNLOAD_PROJECTION_AFTER_PRINT),
                DEFAULT_AUTO_UNLOAD_PROJECTION_AFTER_PRINT);
        clientProjectionUpload = parseBoolean(
                properties.getProperty(KEY_CLIENT_PROJECTION_UPLOAD),
                DEFAULT_CLIENT_PROJECTION_UPLOAD);
        schematicStrictRemap = parseBoolean(
                properties.getProperty(KEY_SCHEMATIC_STRICT_REMAP),
                DEFAULT_SCHEMATIC_STRICT_REMAP);
        printerRotationUseBlockFacingApi = parseBoolean(
                properties.getProperty(KEY_PRINTER_ROTATION_USE_BLOCK_FACING_API),
                DEFAULT_PRINTER_ROTATION_USE_BLOCK_FACING_API);
        printerRotationDebugLog = parseBoolean(
                properties.getProperty(KEY_PRINTER_ROTATION_DEBUG_LOG),
                DEFAULT_PRINTER_ROTATION_DEBUG_LOG);
        projectionGhostAlphaSolid = clampAlpha(
                parseFloat(properties.getProperty(KEY_PROJECTION_GHOST_ALPHA_SOLID), DEFAULT_PROJECTION_GHOST_ALPHA_SOLID),
                DEFAULT_PROJECTION_GHOST_ALPHA_SOLID);
        projectionGhostAlphaTranslucent = clampAlpha(
                parseFloat(properties.getProperty(KEY_PROJECTION_GHOST_ALPHA_TRANSLUCENT), DEFAULT_PROJECTION_GHOST_ALPHA_TRANSLUCENT),
                DEFAULT_PROJECTION_GHOST_ALPHA_TRANSLUCENT);
        projectionLineAlpha = clampAlpha(
                parseFloat(properties.getProperty(KEY_PROJECTION_LINE_ALPHA), DEFAULT_PROJECTION_LINE_ALPHA),
                DEFAULT_PROJECTION_LINE_ALPHA);

        loadManyLibCompatibleValues();
        loadPrinterGuiState(properties);

        save();
    }

    public static synchronized boolean isRequireEmeraldEnabled() {
        return requireEmerald;
    }

    public static synchronized int getBlocksPerEmerald() {
        return blocksPerEmerald;
    }

    public static synchronized boolean isRequireFoodEnabled() {
        return requireFood;
    }

    public static synchronized float getFoodHungerMultiplier() {
        return foodHungerMultiplier;
    }

    public static synchronized boolean isAutoUnloadProjectionAfterPrintEnabled() {
        return autoUnloadProjectionAfterPrint;
    }

    public static synchronized boolean isClientProjectionUploadEnabled() {
        return clientProjectionUpload;
    }

    public static synchronized boolean isSchematicStrictRemapEnabled() {
        return schematicStrictRemap;
    }

    public static synchronized boolean isPrinterRotationUseBlockFacingApiEnabled() {
        return printerRotationUseBlockFacingApi;
    }

    public static synchronized boolean isPrinterRotationDebugLogEnabled() {
        return printerRotationDebugLog;
    }

    public static synchronized int getEmeraldItemId() {
        return DEFAULT_EMERALD_ITEM_ID;
    }

    public static synchronized int getEmeraldSubtype() {
        return DEFAULT_EMERALD_SUBTYPE;
    }

    public static synchronized int computeRequiredEmeralds(int requiredBlocks) {
        if (!requireEmerald || requiredBlocks <= 0) {
            return 0;
        }
        int divisor = Math.max(1, blocksPerEmerald);
        return (requiredBlocks + divisor - 1) / divisor;
    }

    public static synchronized int computeRequiredFoodHunger(int baseHungerCost) {
        if (!requireFood || baseHungerCost <= 0) {
            return 0;
        }
        double scaled = Math.floor(baseHungerCost * (double) Math.max(0.0F, foodHungerMultiplier));
        if (scaled <= 0.0D) {
            return 0;
        }
        if (scaled > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) scaled;
    }

    public static synchronized void setPrinterCostRules(
            boolean newRequireEmerald,
            int newBlocksPerEmerald,
            boolean newRequireFood,
            float newFoodHungerMultiplier) {
        requireEmerald = newRequireEmerald;
        blocksPerEmerald = clampPositive(newBlocksPerEmerald, DEFAULT_BLOCKS_PER_EMERALD);
        requireFood = newRequireFood;
        foodHungerMultiplier = clampNonNegative(newFoodHungerMultiplier, DEFAULT_FOOD_HUNGER_MULTIPLIER);
        save();
    }

    public static synchronized float getProjectionGhostAlphaSolid() {
        return projectionGhostAlphaSolid;
    }

    public static synchronized float getProjectionGhostAlphaTranslucent() {
        return projectionGhostAlphaTranslucent;
    }

    public static synchronized float getProjectionLineAlpha() {
        return projectionLineAlpha;
    }

    public static synchronized void setProjectionAlphas(float ghostSolid, float ghostTranslucent, float line) {
        projectionGhostAlphaSolid = clampAlpha(ghostSolid, DEFAULT_PROJECTION_GHOST_ALPHA_SOLID);
        projectionGhostAlphaTranslucent = clampAlpha(ghostTranslucent, DEFAULT_PROJECTION_GHOST_ALPHA_TRANSLUCENT);
        projectionLineAlpha = clampAlpha(line, DEFAULT_PROJECTION_LINE_ALPHA);
        save();
    }

    public static synchronized String getPrinterGuiSelectedFile(String positionKey) {
        String normalizedKey = normalizePositionKey(positionKey);
        if (normalizedKey == null) {
            return null;
        }
        return printerGuiSelectedFileByPos.get(normalizedKey);
    }

    public static synchronized int getPrinterGuiRotation(String positionKey) {
        String normalizedKey = normalizePositionKey(positionKey);
        if (normalizedKey == null) {
            return 0;
        }
        Integer value = printerGuiRotationByPos.get(normalizedKey);
        if (value == null) {
            return 0;
        }
        return normalizeRotation(value.intValue());
    }

    public static synchronized boolean isPrinterGuiMirrorX(String positionKey) {
        String normalizedKey = normalizePositionKey(positionKey);
        if (normalizedKey == null) {
            return false;
        }
        Boolean value = printerGuiMirrorXByPos.get(normalizedKey);
        return value != null && value.booleanValue();
    }

    public static synchronized boolean isPrinterGuiMirrorZ(String positionKey) {
        String normalizedKey = normalizePositionKey(positionKey);
        if (normalizedKey == null) {
            return false;
        }
        Boolean value = printerGuiMirrorZByPos.get(normalizedKey);
        return value != null && value.booleanValue();
    }

    public static synchronized String getPrinterGuiLastAppliedSignature(String positionKey) {
        String normalizedKey = normalizePositionKey(positionKey);
        if (normalizedKey == null) {
            return null;
        }
        return printerGuiLastAppliedSignatureByPos.get(normalizedKey);
    }

    public static synchronized void setPrinterGuiState(
            String positionKey,
            String selectedFile,
            int rotationDegrees,
            boolean mirrorX,
            boolean mirrorZ,
            String lastAppliedSignature) {
        String normalizedKey = normalizePositionKey(positionKey);
        if (normalizedKey == null) {
            return;
        }
        String normalizedSelectedFile = normalizeText(selectedFile);
        int normalizedRotation = normalizeRotation(rotationDegrees);
        String normalizedLastAppliedSignature = normalizeText(lastAppliedSignature);

        boolean changed = false;
        changed |= putOrRemoveString(printerGuiSelectedFileByPos, normalizedKey, normalizedSelectedFile);
        changed |= putOrRemoveInt(printerGuiRotationByPos, normalizedKey, normalizedRotation, 0);
        changed |= putOrRemoveBoolean(printerGuiMirrorXByPos, normalizedKey, mirrorX);
        changed |= putOrRemoveBoolean(printerGuiMirrorZByPos, normalizedKey, mirrorZ);
        changed |= putOrRemoveString(printerGuiLastAppliedSignatureByPos, normalizedKey, normalizedLastAppliedSignature);

        if (changed) {
            save();
        }
    }

    private static File resolveConfigFile() {
        File base = null;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            base = mc.mcDataDir;
        }
        if (base == null) {
            base = new File(".");
        }
        File configDir = new File(base, "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new File(configDir, FILE_NAME);
    }

    private static File resolveConfigDir() {
        File base = null;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            base = mc.mcDataDir;
        }
        if (base == null) {
            base = new File(".");
        }
        File configDir = new File(base, "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return configDir;
    }

    private static File resolveManyLibCompatFile() {
        File configDir = resolveConfigDir();
        File preferred = new File(configDir, "schematica_survival.json");
        if (preferred.exists()) {
            return preferred;
        }
        File alt = new File(configDir, "SchematicaSurvival.json");
        if (alt.exists()) {
            return alt;
        }
        return preferred;
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim().toLowerCase();
        if ("true".equals(value) || "1".equals(value) || "yes".equals(value) || "on".equals(value)) {
            return true;
        }
        if ("false".equals(value) || "0".equals(value) || "no".equals(value) || "off".equals(value)) {
            return false;
        }
        return fallback;
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String raw, float fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clampPositive(int value, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.min(128, value);
    }

    private static float clampAlpha(float value, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return fallback;
        }
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }

    private static float clampNonNegative(float value, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return fallback;
        }
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 5.0F) {
            return 5.0F;
        }
        return value;
    }

    private static void loadPrinterGuiState(Properties properties) {
        printerGuiSelectedFileByPos.clear();
        printerGuiRotationByPos.clear();
        printerGuiMirrorXByPos.clear();
        printerGuiMirrorZByPos.clear();
        printerGuiLastAppliedSignatureByPos.clear();
        if (properties == null || properties.isEmpty()) {
            return;
        }
        for (String propertyKey : properties.stringPropertyNames()) {
            if (propertyKey == null || !propertyKey.startsWith(KEY_PRINTER_GUI_STATE_PREFIX)) {
                continue;
            }
            String remaining = propertyKey.substring(KEY_PRINTER_GUI_STATE_PREFIX.length());
            int dotIndex = remaining.lastIndexOf('.');
            if (dotIndex <= 0 || dotIndex >= remaining.length() - 1) {
                continue;
            }
            String positionKey = normalizePositionKey(remaining.substring(0, dotIndex));
            if (positionKey == null) {
                continue;
            }
            String suffix = remaining.substring(dotIndex);
            String rawValue = properties.getProperty(propertyKey);
            if (KEY_PRINTER_GUI_SELECTED_FILE_SUFFIX.equals(suffix)) {
                String normalized = normalizeText(rawValue);
                if (normalized != null) {
                    printerGuiSelectedFileByPos.put(positionKey, normalized);
                }
                continue;
            }
            if (KEY_PRINTER_GUI_ROTATION_SUFFIX.equals(suffix)) {
                int rotation = normalizeRotation(parseInt(rawValue, 0));
                if (rotation != 0) {
                    printerGuiRotationByPos.put(positionKey, Integer.valueOf(rotation));
                }
                continue;
            }
            if (KEY_PRINTER_GUI_MIRROR_X_SUFFIX.equals(suffix)) {
                if (parseBoolean(rawValue, false)) {
                    printerGuiMirrorXByPos.put(positionKey, Boolean.TRUE);
                }
                continue;
            }
            if (KEY_PRINTER_GUI_MIRROR_Z_SUFFIX.equals(suffix)) {
                if (parseBoolean(rawValue, false)) {
                    printerGuiMirrorZByPos.put(positionKey, Boolean.TRUE);
                }
                continue;
            }
            if (KEY_PRINTER_GUI_LAST_APPLIED_SIGNATURE_SUFFIX.equals(suffix)) {
                String normalized = normalizeText(rawValue);
                if (normalized != null) {
                    printerGuiLastAppliedSignatureByPos.put(positionKey, normalized);
                }
            }
        }
    }

    private static String normalizePositionKey(String raw) {
        return normalizeText(raw);
    }

    private static String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    private static int normalizeRotation(int value) {
        int normalized = value % 360;
        if (normalized < 0) {
            normalized += 360;
        }
        if (normalized == 90 || normalized == 180 || normalized == 270) {
            return normalized;
        }
        return 0;
    }

    private static boolean putOrRemoveString(Map<String, String> target, String key, String value) {
        String old = target.get(key);
        if (value == null) {
            if (old == null) {
                return false;
            }
            target.remove(key);
            return true;
        }
        if (value.equals(old)) {
            return false;
        }
        target.put(key, value);
        return true;
    }

    private static boolean putOrRemoveInt(Map<String, Integer> target, String key, int value, int removeWhen) {
        Integer old = target.get(key);
        if (value == removeWhen) {
            if (old == null) {
                return false;
            }
            target.remove(key);
            return true;
        }
        if (old != null && old.intValue() == value) {
            return false;
        }
        target.put(key, Integer.valueOf(value));
        return true;
    }

    private static boolean putOrRemoveBoolean(Map<String, Boolean> target, String key, boolean value) {
        Boolean old = target.get(key);
        if (!value) {
            if (old == null) {
                return false;
            }
            target.remove(key);
            return true;
        }
        if (old != null && old.booleanValue()) {
            return false;
        }
        target.put(key, Boolean.TRUE);
        return true;
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty(KEY_REQUIRE_EMERALD, Boolean.toString(requireEmerald));
        properties.setProperty(KEY_BLOCKS_PER_EMERALD, Integer.toString(blocksPerEmerald));
        properties.setProperty(KEY_REQUIRE_FOOD, Boolean.toString(requireFood));
        properties.setProperty(KEY_FOOD_HUNGER_MULTIPLIER, Float.toString(foodHungerMultiplier));
        properties.setProperty(KEY_AUTO_UNLOAD_PROJECTION_AFTER_PRINT, Boolean.toString(autoUnloadProjectionAfterPrint));
        properties.setProperty(KEY_CLIENT_PROJECTION_UPLOAD, Boolean.toString(clientProjectionUpload));
        properties.setProperty(KEY_SCHEMATIC_STRICT_REMAP, Boolean.toString(schematicStrictRemap));
        properties.setProperty(KEY_PRINTER_ROTATION_USE_BLOCK_FACING_API, Boolean.toString(printerRotationUseBlockFacingApi));
        properties.setProperty(KEY_PRINTER_ROTATION_DEBUG_LOG, Boolean.toString(printerRotationDebugLog));
        properties.setProperty(KEY_PROJECTION_GHOST_ALPHA_SOLID, Float.toString(projectionGhostAlphaSolid));
        properties.setProperty(KEY_PROJECTION_GHOST_ALPHA_TRANSLUCENT, Float.toString(projectionGhostAlphaTranslucent));
        properties.setProperty(KEY_PROJECTION_LINE_ALPHA, Float.toString(projectionLineAlpha));
        Set<String> positionKeys = new HashSet<String>();
        positionKeys.addAll(printerGuiSelectedFileByPos.keySet());
        positionKeys.addAll(printerGuiRotationByPos.keySet());
        positionKeys.addAll(printerGuiMirrorXByPos.keySet());
        positionKeys.addAll(printerGuiMirrorZByPos.keySet());
        positionKeys.addAll(printerGuiLastAppliedSignatureByPos.keySet());
        for (String positionKey : positionKeys) {
            String selectedFile = printerGuiSelectedFileByPos.get(positionKey);
            if (selectedFile != null && !selectedFile.isEmpty()) {
                properties.setProperty(KEY_PRINTER_GUI_STATE_PREFIX + positionKey + KEY_PRINTER_GUI_SELECTED_FILE_SUFFIX, selectedFile);
            }
            Integer rotation = printerGuiRotationByPos.get(positionKey);
            if (rotation != null && rotation.intValue() != 0) {
                properties.setProperty(KEY_PRINTER_GUI_STATE_PREFIX + positionKey + KEY_PRINTER_GUI_ROTATION_SUFFIX, Integer.toString(rotation.intValue()));
            }
            Boolean mirrorX = printerGuiMirrorXByPos.get(positionKey);
            if (mirrorX != null && mirrorX.booleanValue()) {
                properties.setProperty(KEY_PRINTER_GUI_STATE_PREFIX + positionKey + KEY_PRINTER_GUI_MIRROR_X_SUFFIX, Boolean.toString(true));
            }
            Boolean mirrorZ = printerGuiMirrorZByPos.get(positionKey);
            if (mirrorZ != null && mirrorZ.booleanValue()) {
                properties.setProperty(KEY_PRINTER_GUI_STATE_PREFIX + positionKey + KEY_PRINTER_GUI_MIRROR_Z_SUFFIX, Boolean.toString(true));
            }
            String signature = printerGuiLastAppliedSignatureByPos.get(positionKey);
            if (signature != null && !signature.isEmpty()) {
                properties.setProperty(KEY_PRINTER_GUI_STATE_PREFIX + positionKey + KEY_PRINTER_GUI_LAST_APPLIED_SIGNATURE_SUFFIX, signature);
            }
        }

        File file = resolveConfigFile();
        try {
            FileOutputStream output = new FileOutputStream(file);
            try {
                properties.store(output, "Schematica Survival printer config");
            } finally {
                output.close();
            }
        } catch (IOException ignored) {
        }

        saveManyLibCompatibleValues();
    }

    private static void loadManyLibCompatibleValues() {
        File file = resolveManyLibCompatFile();
        if (file == null || !file.exists()) {
            return;
        }
        try {
            java.io.Reader reader = new java.io.InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            JsonElement rootElement;
            try {
                rootElement = new JsonParser().parse(reader);
            } finally {
                reader.close();
            }
            if (rootElement == null || !rootElement.isJsonObject()) {
                return;
            }
            JsonObject root = rootElement.getAsJsonObject();
            JsonObject values = root.has(MANYLIB_SECTION_VALUES) && root.get(MANYLIB_SECTION_VALUES).isJsonObject()
                    ? root.getAsJsonObject(MANYLIB_SECTION_VALUES)
                    : null;
            if (values == null) {
                return;
            }
            requireEmerald = readManyLibBoolean(values, MANYLIB_KEY_REQUIRE_EMERALD, requireEmerald);
            blocksPerEmerald = clampPositive(readManyLibInt(values, MANYLIB_KEY_BLOCKS_PER_EMERALD, blocksPerEmerald), DEFAULT_BLOCKS_PER_EMERALD);
            requireFood = readManyLibBoolean(values, MANYLIB_KEY_REQUIRE_FOOD, requireFood);
            foodHungerMultiplier = clampNonNegative(readManyLibFloat(values, MANYLIB_KEY_FOOD_HUNGER_MULTIPLIER, foodHungerMultiplier), DEFAULT_FOOD_HUNGER_MULTIPLIER);
        } catch (Throwable ignored) {
        }
    }

    private static void saveManyLibCompatibleValues() {
        File file = resolveManyLibCompatFile();
        if (file == null) {
            return;
        }
        try {
            JsonObject root;
            if (file.exists()) {
                java.io.Reader reader = new java.io.InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
                JsonElement existing;
                try {
                    existing = new JsonParser().parse(reader);
                } finally {
                    reader.close();
                }
                root = existing != null && existing.isJsonObject() ? existing.getAsJsonObject() : new JsonObject();
            } else {
                root = new JsonObject();
            }
            JsonObject values = root.has(MANYLIB_SECTION_VALUES) && root.get(MANYLIB_SECTION_VALUES).isJsonObject()
                    ? root.getAsJsonObject(MANYLIB_SECTION_VALUES)
                    : new JsonObject();
            root.add(MANYLIB_SECTION_VALUES, values);

            writeManyLibValue(values, MANYLIB_KEY_REQUIRE_EMERALD, Boolean.valueOf(requireEmerald));
            writeManyLibValue(values, MANYLIB_KEY_BLOCKS_PER_EMERALD, Integer.valueOf(blocksPerEmerald));
            writeManyLibValue(values, MANYLIB_KEY_REQUIRE_FOOD, Boolean.valueOf(requireFood));
            writeManyLibValue(values, MANYLIB_KEY_FOOD_HUNGER_MULTIPLIER, Float.valueOf(foodHungerMultiplier));

            java.io.Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            try {
                writer.write(PRETTY_GSON.toJson(root));
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void writeManyLibValue(JsonObject values, String key, Object value) {
        if (values == null || key == null || key.isEmpty() || value == null) {
            return;
        }
        JsonObject entry = values.has(key) && values.get(key).isJsonObject()
                ? values.getAsJsonObject(key)
                : new JsonObject();
        if (value instanceof Boolean) {
            entry.add("value", new JsonPrimitive((Boolean) value));
        } else if (value instanceof Integer) {
            entry.add("value", new JsonPrimitive((Integer) value));
            entry.add("useSlider", new JsonPrimitive(false));
        } else if (value instanceof Float) {
            entry.add("value", new JsonPrimitive((Float) value));
            entry.add("useSlider", new JsonPrimitive(false));
        }
        values.add(key, entry);
    }

    private static boolean readManyLibBoolean(JsonObject values, String key, boolean fallback) {
        JsonObject entry = readManyLibEntry(values, key);
        if (entry == null || !entry.has("value")) {
            return fallback;
        }
        JsonElement value = entry.get("value");
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return value.getAsBoolean();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int readManyLibInt(JsonObject values, String key, int fallback) {
        JsonObject entry = readManyLibEntry(values, key);
        if (entry == null || !entry.has("value")) {
            return fallback;
        }
        JsonElement value = entry.get("value");
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return value.getAsInt();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static float readManyLibFloat(JsonObject values, String key, float fallback) {
        JsonObject entry = readManyLibEntry(values, key);
        if (entry == null || !entry.has("value")) {
            return fallback;
        }
        JsonElement value = entry.get("value");
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return value.getAsFloat();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static JsonObject readManyLibEntry(JsonObject values, String key) {
        if (values == null || key == null || key.isEmpty()) {
            return null;
        }
        if (!values.has(key)) {
            return null;
        }
        JsonElement raw = values.get(key);
        if (raw == null || !raw.isJsonObject()) {
            return null;
        }
        return raw.getAsJsonObject();
    }
}
