package dev.mr3n.syncprojecte

import moze_intel.projecte.impl.KnowledgeImpl
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventHandler
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.apache.logging.log4j.Logger

@Mod(modid = "syncprojecte", name = "Sync ProjectE Knowledge", version = "1.0", dependencies = "required:projecte", serverSideOnly = true, acceptableRemoteVersions = "*")
class SyncProjectE {

    companion object {
        lateinit var LOGGER: Logger
    }

    @EventHandler
    fun preInt(event: FMLPreInitializationEvent) {
        LOGGER = event.modLog
    }

    @EventHandler
    fun init(event: FMLInitializationEvent) {
        if(FMLCommonHandler.instance().effectiveSide.isServer) {
            MinecraftForge.EVENT_BUS.register(object {
                var tmpEmc: Long = 0L

                @SubscribeEvent
                fun onTick(event: TickEvent.WorldTickEvent) {
                    if (FMLCommonHandler.instance().getEffectiveSide().isServer) {
                        if (tmpEmc != KnowledgeData.INSTANCE.emc) {
                            tmpEmc = KnowledgeData.INSTANCE.emc
                            KnowledgeImplWrapper.sync()
                        }
                    }
                }

                @SubscribeEvent
                fun onPlayerConnect(event: PlayerEvent.PlayerLoggedInEvent) {
                    if(FMLCommonHandler.instance().effectiveSide.isServer) { KnowledgeImplWrapper.sync() }
                }

                @SubscribeEvent
                fun onAttachCaps(event: AttachCapabilitiesEvent<Entity>) {
                    if(FMLCommonHandler.instance().effectiveSide.isServer) {
                        val player = event.`object`
                        if(player is EntityPlayer) {
                            val capsField = event::class.java.getDeclaredField("caps").apply { isAccessible = true }
                            val caps = capsField.get(event)
                            val removeMethod = caps.javaClass.getMethod("put", Any::class.java, Any::class.java)
                            removeMethod(caps, KnowledgeImpl.Provider.NAME, KnowledgeImplWrapper.Provider)
                        }
                    }
                }
            })
        }
    }
}