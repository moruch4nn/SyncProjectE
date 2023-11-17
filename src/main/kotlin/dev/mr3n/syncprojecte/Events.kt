package dev.mr3n.syncprojecte

import moze_intel.projecte.capability.managing.BasicCapabilityResolver
import moze_intel.projecte.handlers.CommonInternalAbilities
import moze_intel.projecte.handlers.InternalAbilities
import moze_intel.projecte.handlers.InternalTimers
import moze_intel.projecte.impl.capability.AlchBagImpl
import moze_intel.projecte.impl.capability.KnowledgeImpl
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.util.ResourceLocation
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.thread.EffectiveSide
import java.math.BigInteger

@Mod.EventBusSubscriber(modid = "syncprojecte")
object Events {
    var tmpEmc: BigInteger = BigInteger("0")

    @JvmStatic
    @SubscribeEvent
    fun onTick(event: TickEvent.WorldTickEvent) {

        if (EffectiveSide.get().isServer) {
            if (tmpEmc != KnowledgeData.INSTANCE.emc) {
                tmpEmc = KnowledgeData.INSTANCE.emc
                KnowledgeImplWrapper.sync()
            }
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onPlayerConnect(event: PlayerEvent.PlayerLoggedInEvent) {
        if(EffectiveSide.get().isServer) { KnowledgeImplWrapper.sync() }
    }

    @JvmStatic
    @SubscribeEvent(priority = EventPriority.LOW)
    fun onAttachCaps(event: AttachCapabilitiesEvent<Entity>) {

        fun attachCapability(evt: AttachCapabilitiesEvent<Entity>, name: ResourceLocation, cap: BasicCapabilityResolver<*>) {
            evt.addCapability(name, cap)
            evt.addListener { cap.invalidateAll() }
        }
        val player = event.`object`
        if(player is PlayerEntity && EffectiveSide.get().isServer) {
            val listenersField = AttachCapabilitiesEvent::class.java.getDeclaredField("listeners").apply { isAccessible = true }
            val listeners = listenersField.get(event)
            (listeners as MutableList<*>).removeAll { it.toString().contains("moze_intel.projecte.events.PlayerEvents") }

            val capsField = event::class.java.getDeclaredField("caps").apply { isAccessible = true }
            (capsField.get(event) as MutableMap<*,*>).also { map ->
                map.remove(AlchBagImpl.Provider.NAME)
                map.remove(KnowledgeImpl.Provider.NAME)
                map.remove(CommonInternalAbilities.NAME)
                map.remove(InternalTimers.NAME)
                map.remove(InternalAbilities.NAME)
            }

            attachCapability(event, AlchBagImpl.Provider.NAME, AlchBagImpl.Provider())
            attachCapability(event, KnowledgeImpl.Provider.NAME, KnowledgeImplWrapper.Provider)
            attachCapability(event, CommonInternalAbilities.NAME, CommonInternalAbilities.Provider(player))
            if(player is ServerPlayerEntity) {
                attachCapability(event, InternalTimers.NAME, InternalTimers.Provider())
                attachCapability(event, InternalAbilities.NAME, InternalAbilities.Provider(player))
            }
        }
    }
}