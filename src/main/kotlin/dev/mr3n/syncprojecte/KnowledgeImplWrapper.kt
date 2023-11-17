package dev.mr3n.syncprojecte

import moze_intel.projecte.api.ItemInfo
import moze_intel.projecte.api.ProjectEAPI
import moze_intel.projecte.api.capabilities.IKnowledgeProvider
import moze_intel.projecte.api.event.PlayerKnowledgeChangeEvent
import moze_intel.projecte.capability.managing.SerializableCapabilityResolver
import moze_intel.projecte.emc.EMCMappingHandler
import moze_intel.projecte.emc.nbt.NBTManager
import moze_intel.projecte.gameObjs.items.Tome
import moze_intel.projecte.network.PacketHandler
import moze_intel.projecte.network.packets.to_client.knowledge.KnowledgeSyncChangePKT
import moze_intel.projecte.network.packets.to_client.knowledge.KnowledgeSyncInputsAndLocksPKT
import moze_intel.projecte.network.packets.to_client.knowledge.KnowledgeSyncPKT
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompoundNBT
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.fml.server.ServerLifecycleHooks
import net.minecraftforge.items.IItemHandler
import java.math.BigInteger
import java.util.*
import java.util.function.Consumer

object KnowledgeImplWrapper: IKnowledgeProvider {
    override fun serializeNBT(): CompoundNBT {
        return KnowledgeData.INSTANCE.serializeNBT()
    }

    override fun deserializeNBT(nbt: CompoundNBT?) {
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
        ServerLifecycleHooks.getCurrentServer().playerList.players.forEach { player ->
            if (player != null && !player.level.isClientSide) { MinecraftForge.EVENT_BUS.post(PlayerKnowledgeChangeEvent(player)) }
        }
    }

    override fun hasKnowledge(info: ItemInfo): Boolean {
        if(KnowledgeData.INSTANCE.fullKnowledge) {
            val persistentInfo = getIfPersistent(info)
            return persistentInfo == null || KnowledgeData.INSTANCE.knowledge.contains(persistentInfo)
        }
        return KnowledgeData.INSTANCE.knowledge.contains(NBTManager.getPersistentInfo(info))
    }

    fun getIfPersistent(info: ItemInfo): ItemInfo? {
        if(!info.hasNBT() || EMCMappingHandler.hasEmcValue(info)) { return null }
        val cleanedInfo = NBTManager.getPersistentInfo(info)
        if(cleanedInfo.hasNBT() && !EMCMappingHandler.hasEmcValue(cleanedInfo)) { return cleanedInfo }
        return null
    }

    private fun tryAdd(cleanedInfo: ItemInfo): Boolean {
        if(KnowledgeData.INSTANCE.knowledge.add(cleanedInfo)) {
            this.fireChangedEvent()
            return true
        }
        return false
    }

    private fun tryRemove(cleanedInfo: ItemInfo): Boolean {
        if(KnowledgeData.INSTANCE.knowledge.remove(cleanedInfo)) {
            this.fireChangedEvent()
            return true
        }
        return false
    }

    override fun addKnowledge(info: ItemInfo): Boolean {
        if(KnowledgeData.INSTANCE.fullKnowledge) {
            val persistentInfo = this.getIfPersistent(info)?:return false
            return tryAdd(persistentInfo)
        }
        if(info.item is Tome) {
            var info1 = info
            if(info.hasNBT()) {
                info1 = ItemInfo.fromItem(info.item)
            }
            KnowledgeData.INSTANCE.knowledge.add(info1)
            KnowledgeData.INSTANCE.fullKnowledge = true
            this.fireChangedEvent()
            return true
        }
        return tryAdd(NBTManager.getPersistentInfo(info))
    }

    override fun removeKnowledge(info: ItemInfo): Boolean {
        if(KnowledgeData.INSTANCE.fullKnowledge) {
            if(info.item is Tome) {
                var info1 = info
                if(info.hasNBT()) { info1 = ItemInfo.fromItem(info.item) }
                KnowledgeData.INSTANCE.knowledge.remove(info)
                KnowledgeData.INSTANCE.fullKnowledge = false
                this.fireChangedEvent()
                return true
            }
            val persistentInfo = this.getIfPersistent(info)
            return persistentInfo != null && this.tryRemove(persistentInfo)
        }
        return this.tryRemove(NBTManager.getPersistentInfo(info))
    }

    override fun getKnowledge(): MutableSet<ItemInfo> {
        return if(KnowledgeData.INSTANCE.fullKnowledge) {
            val allKnowledge = EMCMappingHandler.getMappedItems()
            allKnowledge.addAll(KnowledgeData.INSTANCE.knowledge)
            allKnowledge
        } else {
            Collections.unmodifiableSet(KnowledgeData.INSTANCE.knowledge)
        }
    }

    override fun getInputAndLocks(): IItemHandler = KnowledgeData.INSTANCE.inputLocks

    override fun getEmc(): BigInteger = KnowledgeData.INSTANCE.emc

    override fun setEmc(emc: BigInteger) {
        KnowledgeData.INSTANCE.emc = emc
    }

    override fun sync(p0: ServerPlayerEntity) { this.sync() }
    override fun syncEmc(p0: ServerPlayerEntity) {
        val nbt = serializeNBT()
        nbt.putBoolean("projecte-sync", true)
        ServerLifecycleHooks.getCurrentServer().playerList.players.forEach(Consumer { player: ServerPlayerEntity? ->
            PacketHandler.sendTo(KnowledgeSyncPKT(nbt), player)
        })
    }

    override fun syncKnowledgeChange(p0: ServerPlayerEntity, p1: ItemInfo, learned: Boolean) {
        val nbt = serializeNBT()
        nbt.putBoolean("projecte-sync", true)
        ServerLifecycleHooks.getCurrentServer().playerList.players.forEach(Consumer { player: ServerPlayerEntity? ->
            PacketHandler.sendTo(KnowledgeSyncChangePKT(p1, learned), player)
        })
    }

    override fun syncInputAndLocks(player: ServerPlayerEntity, slotsChanged: MutableList<Int>, updateTargets: IKnowledgeProvider.TargetUpdateType) {
        if(slotsChanged.isNotEmpty()) {
            val slots = KnowledgeData.INSTANCE.inputLocks.slots
            val stacksToSync = mutableMapOf<Int, ItemStack>()
            slotsChanged.forEach { slot ->
                if(slot in 0..<slots) {
                    stacksToSync[slot] = KnowledgeData.INSTANCE.inputLocks.getStackInSlot(slot)
                }
            }
            if(stacksToSync.isNotEmpty()) {
                ServerLifecycleHooks.getCurrentServer().playerList.players.forEach(Consumer { p: ServerPlayerEntity ->
                    PacketHandler.sendTo(KnowledgeSyncInputsAndLocksPKT(stacksToSync, updateTargets), p)
                })
            }
        }
    }

    override fun receiveInputsAndLocks(changes: MutableMap<Int, ItemStack>) {
        val slots = KnowledgeData.INSTANCE.inputLocks.slots
        changes.forEach { (slot, value) ->
            if(slot in 0..<slots) { KnowledgeData.INSTANCE.inputLocks.setStackInSlot(slot, value) }
        }
    }

    fun sync() {
        val nbt = serializeNBT()
        nbt.putBoolean("projecte-sync", true)
        ServerLifecycleHooks.getCurrentServer().playerList.players.forEach(Consumer { player: ServerPlayerEntity? ->
            PacketHandler.sendTo(KnowledgeSyncPKT(nbt), player)
        })
    }

    object Provider: SerializableCapabilityResolver<IKnowledgeProvider>(KnowledgeImplWrapper) {
        override fun getMatchingCapability(): Capability<IKnowledgeProvider> {
            return ProjectEAPI.KNOWLEDGE_CAPABILITY
        }
    }
}