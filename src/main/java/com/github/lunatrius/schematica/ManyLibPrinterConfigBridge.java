package com.github.lunatrius.schematica;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ManyLibPrinterConfigBridge {
    private static volatile boolean initialized = false;
    private static Object cfgRequireEmerald;
    private static Object cfgBlocksPerEmerald;
    private static Object cfgRequireFood;
    private static Object cfgFoodMultiplier;

    private ManyLibPrinterConfigBridge() {
    }

    static synchronized void initializeIfPresent() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            ClassLoader cl = ManyLibPrinterConfigBridge.class.getClassLoader();
            Class<?> configBooleanClass = Class.forName("fi.dy.masa.malilib.config.options.ConfigBoolean", false, cl);
            Class<?> configIntegerClass = Class.forName("fi.dy.masa.malilib.config.options.ConfigInteger", false, cl);
            Class<?> configDoubleClass = Class.forName("fi.dy.masa.malilib.config.options.ConfigDouble", false, cl);
            Class<?> configBaseClass = Class.forName("fi.dy.masa.malilib.config.options.ConfigBase", false, cl);
            Class<?> callbackInterface = Class.forName("fi.dy.masa.malilib.config.interfaces.IValueChangeCallback", false, cl);
            Class<?> configTabClass = Class.forName("fi.dy.masa.malilib.config.ConfigTab", false, cl);
            Class<?> configHandlerInterface = Class.forName("fi.dy.masa.malilib.config.interfaces.IConfigHandler", false, cl);
            Class<?> configManagerClass = Class.forName("fi.dy.masa.malilib.config.ConfigManager", false, cl);
            Class<?> defaultConfigScreenClass = Class.forName("fi.dy.masa.malilib.gui.screen.DefaultConfigScreen", false, cl);
            final Constructor<?> defaultConfigScreenCtor = defaultConfigScreenClass.getConstructor(
                    Class.forName("net.minecraft.GuiScreen", false, cl),
                    configHandlerInterface);

            cfgRequireEmerald = newBooleanConfig(
                    configBooleanClass,
                    "schematicaSurvival.printer.requireEmerald",
                    SchematicaPrinterConfig.isRequireEmeraldEnabled(),
                    "config.comment.schematicaSurvival.printer.requireEmerald");
            cfgBlocksPerEmerald = newIntegerConfig(
                    configIntegerClass,
                    "schematicaSurvival.printer.blocksPerEmerald",
                    SchematicaPrinterConfig.getBlocksPerEmerald(),
                    1,
                    128,
                    "config.comment.schematicaSurvival.printer.blocksPerEmerald");
            cfgRequireFood = newBooleanConfig(
                    configBooleanClass,
                    "schematicaSurvival.printer.requireFood",
                    SchematicaPrinterConfig.isRequireFoodEnabled(),
                    "config.comment.schematicaSurvival.printer.requireFood");
            cfgFoodMultiplier = newDoubleConfig(
                    configDoubleClass,
                    "schematicaSurvival.printer.foodHungerMultiplier",
                    SchematicaPrinterConfig.getFoodHungerMultiplier(),
                    0.0D,
                    5.0D,
                    "config.comment.schematicaSurvival.printer.foodHungerMultiplier");

            Object valueChangeCallback = Proxy.newProxyInstance(
                    cl,
                    new Class[]{callbackInterface},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if (method != null && "onValueChanged".equals(method.getName())) {
                                applyFromManyLibConfigs();
                            }
                            return null;
                        }
                    });

            Method setValueChangeCallback = configBaseClass.getMethod("setValueChangeCallback", callbackInterface);
            setValueChangeCallback.invoke(cfgRequireEmerald, valueChangeCallback);
            setValueChangeCallback.invoke(cfgBlocksPerEmerald, valueChangeCallback);
            setValueChangeCallback.invoke(cfgRequireFood, valueChangeCallback);
            setValueChangeCallback.invoke(cfgFoodMultiplier, valueChangeCallback);

            final List<Object> values = new ArrayList<Object>();
            values.add(cfgRequireEmerald);
            values.add(cfgBlocksPerEmerald);
            values.add(cfgRequireFood);
            values.add(cfgFoodMultiplier);

            final Constructor<?> configTabCtor = configTabClass.getConstructor(String.class, List.class);
            final List<Object> tabs = Collections.singletonList(configTabCtor.newInstance("generic", values));

            Object handler = Proxy.newProxyInstance(
                    cl,
                    new Class[]{configHandlerInterface},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if (method == null) {
                                return null;
                            }
                            String name = method.getName();
                            if ("load".equals(name)) {
                                syncManyLibConfigsFromSchematica();
                                return null;
                            }
                            if ("save".equals(name)) {
                                applyFromManyLibConfigs();
                                return null;
                            }
                            if ("getConfigTabs".equals(name)) {
                                return tabs;
                            }
                            if ("getValues".equals(name)) {
                                return values;
                            }
                            if ("getHotkeys".equals(name)) {
                                return Collections.emptyList();
                            }
                            if ("getName".equals(name)) {
                                return "SchematicaSurvival";
                            }
                            if ("getMenuComment".equals(name)) {
                                return "config.menu.comment.SchematicaSurvival";
                            }
                            if ("getConfigScreen".equals(name) || "getValueScreen".equals(name)) {
                                Object parent = args != null && args.length > 0 ? args[0] : null;
                                return defaultConfigScreenCtor.newInstance(parent, proxy);
                            }
                            return null;
                        }
                    });

            Object manager = configManagerClass.getMethod("getInstance").invoke(null);
            Method registerWithName = configManagerClass.getMethod("registerConfig", String.class, configHandlerInterface);
            registerWithName.invoke(manager, "SchematicaSurvival", handler);
            Method loadAll = configManagerClass.getMethod("loadAllConfigs");
            loadAll.invoke(manager);
        } catch (Throwable ignored) {
            // Optional compatibility only; ignore when ManyLib is absent or API shape differs.
        }
    }

    private static Object newBooleanConfig(Class<?> configBooleanClass, String key, boolean defaultValue, String comment) throws Exception {
        Constructor<?> ctor = configBooleanClass.getConstructor(String.class, boolean.class, String.class);
        return ctor.newInstance(key, Boolean.valueOf(defaultValue), comment);
    }

    private static Object newIntegerConfig(Class<?> configIntegerClass, String key, int defaultValue, int min, int max, String comment) throws Exception {
        Constructor<?> ctor = configIntegerClass.getConstructor(String.class, int.class, int.class, int.class, String.class);
        return ctor.newInstance(key, Integer.valueOf(defaultValue), Integer.valueOf(min), Integer.valueOf(max), comment);
    }

    private static Object newDoubleConfig(Class<?> configDoubleClass, String key, double defaultValue, double min, double max, String comment) throws Exception {
        Constructor<?> ctor = configDoubleClass.getConstructor(String.class, double.class, double.class, double.class, boolean.class, String.class);
        return ctor.newInstance(key, Double.valueOf(defaultValue), Double.valueOf(min), Double.valueOf(max), Boolean.FALSE, comment);
    }

    private static void syncManyLibConfigsFromSchematica() throws Exception {
        cfgRequireEmerald.getClass().getMethod("setBooleanValue", boolean.class).invoke(cfgRequireEmerald, SchematicaPrinterConfig.isRequireEmeraldEnabled());
        cfgBlocksPerEmerald.getClass().getMethod("setIntegerValue", int.class).invoke(cfgBlocksPerEmerald, SchematicaPrinterConfig.getBlocksPerEmerald());
        cfgRequireFood.getClass().getMethod("setBooleanValue", boolean.class).invoke(cfgRequireFood, SchematicaPrinterConfig.isRequireFoodEnabled());
        cfgFoodMultiplier.getClass().getMethod("setDoubleValue", double.class).invoke(cfgFoodMultiplier, (double) SchematicaPrinterConfig.getFoodHungerMultiplier());
    }

    private static void applyFromManyLibConfigs() throws Exception {
        boolean requireEmerald = ((Boolean) cfgRequireEmerald.getClass().getMethod("getBooleanValue").invoke(cfgRequireEmerald)).booleanValue();
        int blocksPerEmerald = ((Integer) cfgBlocksPerEmerald.getClass().getMethod("getIntegerValue").invoke(cfgBlocksPerEmerald)).intValue();
        boolean requireFood = ((Boolean) cfgRequireFood.getClass().getMethod("getBooleanValue").invoke(cfgRequireFood)).booleanValue();
        double foodMultiplier = ((Double) cfgFoodMultiplier.getClass().getMethod("getDoubleValue").invoke(cfgFoodMultiplier)).doubleValue();
        SchematicaPrinterConfig.setPrinterCostRules(requireEmerald, blocksPerEmerald, requireFood, (float) foodMultiplier);
    }
}
