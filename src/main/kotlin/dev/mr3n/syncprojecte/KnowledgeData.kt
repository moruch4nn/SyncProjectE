package dev.mr3n.syncprojecte

import moze_intel.projecte.api.ItemInfo
import moze_intel.projecte.utils.EMCHelper
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompoundNBT
import net.minecraft.nbt.ListNBT
import net.minecraft.world.storage.WorldSavedData
import net.minecraftforge.common.util.Constants
import net.minecraftforge.fml.server.ServerLifecycleHooks
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.IItemHandlerModifiable
import net.minecraftforge.items.ItemStackHandler
import java.math.BigInteger

class KnowledgeData(name: String = ID): WorldSavedData(name) {

    companion object {
        const val ID = "projecte-sync_knowledge"
        val INSTANCE: KnowledgeData
            get() {
                val world = ServerLifecycleHooks.getCurrentServer().overworld()
                val mapStorage = world.dataStorage
                return mapStorage.get({ KnowledgeData() }, ID) ?: KnowledgeData().also { mapStorage.set(it) }
            }
    }


    val knowledge: MutableSet<ItemInfo> = mutableSetOf()

    val inputLocks: IItemHandlerModifiable = ItemStackHandler(9)

    var emc: BigInteger = BigInteger.ZERO
        set(value) {
            if(value == field) { return }
            this.setDirty()
            field = value
        }

    var fullKnowledge: Boolean = false
        set(value) {
            if(value == field) { return }
            this.setDirty()
            field = value
        }

    private fun pruneStateKnowledge() {
        this.knowledge.removeIf { !EMCHelper.doesItemHaveEmc(it) }
    }
    override fun load(nbt: CompoundNBT) {
        this.emc = BigInteger(nbt.getString("transmutationEmc")?:"0")
        val knowledge = nbt.getList("knowledge", Constants.NBT.TAG_COMPOUND)
        knowledge.forEachIndexed { index, _ ->
            val info = ItemInfo.read(knowledge.getCompound(index))
            if(info != null) { this.knowledge.add(info) }
        }
        this.pruneStateKnowledge()
        repeat(this.inputLocks.slots) { this.inputLocks.setStackInSlot(it, ItemStack.EMPTY) }
        CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.readNBT(this.inputLocks, null, nbt.getList("inputlock", Constants.NBT.TAG_COMPOUND))
        this.fullKnowledge = nbt.getBoolean("fullknowledge")
    }

    override fun save(nbt: CompoundNBT): CompoundNBT {
        nbt.putString("transmutationEmc", this.emc.toString())
        val knowledgeWrite = ListNBT().also { l -> this.knowledge.forEach { i -> l.add(i.write(CompoundNBT())) } }
        nbt.put("knowledge", knowledgeWrite)
        val lock = CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.writeNBT(this.inputLocks, null)
        if(lock != null) { nbt.put("inputlock", lock) }
        nbt.putBoolean("fullknowledge", this.fullKnowledge)
        return nbt
    }

}