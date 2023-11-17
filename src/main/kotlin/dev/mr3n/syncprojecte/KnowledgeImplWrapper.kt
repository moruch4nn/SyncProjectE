package dev.mr3n.syncprojecte

import moze_intel.projecte.api.ProjectEAPI
import moze_intel.projecte.api.capabilities.IKnowledgeProvider
import moze_intel.projecte.api.event.PlayerKnowledgeChangeEvent
import moze_intel.projecte.gameObjs.ObjHandler
import moze_intel.projecte.network.PacketHandler
import moze_intel.projecte.network.packets.KnowledgeSyncPKT
import moze_intel.projecte.playerData.Transmutation
import moze_intel.projecte.utils.ItemHelper
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ICapabilitySerializable
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.items.IItemHandler
import java.util.*
import java.util.function.Consumer

object KnowledgeImplWrapper: IKnowledgeProvider {
    override fun serializeNBT(): NBTTagCompound {
        return KnowledgeData.INSTANCE.serializeNBT()
    }

    override fun deserializeNBT(nbt: NBTTagCompound?) {
        if(nbt?.getBoolean("syncprojecte") != true) { return }
        KnowledgeData.INSTANCE.deserializeNBT(nbt)
    }

    override fun hasFullKnowledge(): Boolean = KnowledgeData.INSTANCE.fullKnowledge

    override fun setFullKnowledge(bool: Boolean) {
        KnowledgeData.INSTANCE.fullKnowledge = bool
    }

    override fun clearKnowledge() {
        KnowledgeData.INSTANCE.knowledge.clear()
        KnowledgeData.INSTANCE.fullKnowledge = false
        this.fireChangedEvent()
    }

    private fun fireChangedEvent() {
        FMLCommonHandler.instance().minecraftServerInstance.playerList.players.forEach { player ->
            if (!player.entityWorld.isRemote) { MinecraftForge.EVENT_BUS.post(PlayerKnowledgeChangeEvent(player)) }
        }
    }

    override fun hasKnowledge(itemStack: ItemStack): Boolean {
        if(itemStack.isEmpty) { return false }
        if(KnowledgeData.INSTANCE.fullKnowledge) { return true }
        return KnowledgeData.INSTANCE.knowledge.any { ItemHelper.basicAreStacksEqual(it, itemStack) }
    }

    override fun addKnowledge(itemStack: ItemStack): Boolean {
        if(KnowledgeData.INSTANCE.fullKnowledge) { return false }
        if(itemStack.item == ObjHandler.tome) {
            if(!this.hasKnowledge(itemStack)) { KnowledgeData.INSTANCE.knowledge.add(itemStack) }
            KnowledgeData.INSTANCE.fullKnowledge = true
            this.fireChangedEvent()
            return true
        } else if(!this.hasKnowledge(itemStack)) {
            KnowledgeData.INSTANCE.knowledge.add(itemStack)
            this.fireChangedEvent()
            return true
        }
        return false
    }

    override fun removeKnowledge(itemStack: ItemStack): Boolean {
        var removed = false
        if(itemStack.item == ObjHandler.tome) {
            KnowledgeData.INSTANCE.fullKnowledge = false
            removed = true
        }
        if(KnowledgeData.INSTANCE.fullKnowledge) { return false }
        if(KnowledgeData.INSTANCE.knowledge.removeAll { ItemHelper.basicAreStacksEqual(itemStack, it) }) {
            removed = true
        }
        return removed
    }

    override fun getKnowledge(): MutableList<ItemStack> {
        return if(KnowledgeData.INSTANCE.fullKnowledge) Transmutation.getCachedTomeKnowledge() else Collections.unmodifiableList(KnowledgeData.INSTANCE.knowledge)
    }

    override fun getInputAndLocks(): IItemHandler = KnowledgeData.INSTANCE.inputLocks

    override fun getEmc(): Long = KnowledgeData.INSTANCE.emc

    override fun setEmc(emc: Long) {
        KnowledgeData.INSTANCE.emc = emc
    }

    override fun sync(p0: EntityPlayerMP) { this.sync() }

    fun sync() {
        val nbt = serializeNBT()
        nbt.setBoolean("projecte-sync", true)
        FMLCommonHandler.instance().minecraftServerInstance.playerList.players.forEach(Consumer { player: EntityPlayerMP? ->
            PacketHandler.sendTo(KnowledgeSyncPKT(nbt), player)
        })
    }

    object Provider: ICapabilitySerializable<NBTTagCompound> {
        override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean {
            return capability == ProjectEAPI.KNOWLEDGE_CAPABILITY
        }

        override fun <T : Any?> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
            return if(this.hasCapability(capability, facing)) ProjectEAPI.KNOWLEDGE_CAPABILITY.cast(KnowledgeImplWrapper) else null
        }

        override fun serializeNBT(): NBTTagCompound = KnowledgeImplWrapper.serializeNBT()

        override fun deserializeNBT(nbt: NBTTagCompound?) {
            KnowledgeImplWrapper.deserializeNBT(nbt)
        }

    }
}