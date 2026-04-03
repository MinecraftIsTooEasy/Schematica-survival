package com.github.lunatrius.schematica;

import com.github.lunatrius.schematica.network.SchematicaPrinterNetworking;
import net.fabricmc.api.ModInitializer;
import net.xiaoyu233.fml.ModResourceManager;
import net.xiaoyu233.fml.reload.event.MITEEvents;

public class SchematicaSurvival implements ModInitializer {
    public static final String MOD_ID = "schematica_survival";

    @Override
    public void onInitialize() {
        ModResourceManager.addResourcePackDomain(MOD_ID);
        SchematicaPrinterConfig.load();
        SchematicaPrinterNetworking.registerPacketReaders();
        MITEEvents.MITE_EVENT_BUS.register(new SchematicaRegistryEventListener());
        MITEEvents.MITE_EVENT_BUS.register(new SurvivalSchematicaEventListener());
    }
}
