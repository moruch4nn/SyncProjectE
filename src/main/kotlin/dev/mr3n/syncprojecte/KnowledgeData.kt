package dev.mr3n.syncprojecte

import moze_intel.projecte.utils.EMCHelper
import moze_intel.projecte.utils.ItemHelper
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.world.storage.WorldSavedData
import net.minecraftforge.common.util.Constants
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.IItemHandlerModifiable
import net.minecraftforge.items.ItemStackHandler

class KnowledgeData(name: String = ID): WorldSavedData(name) {

    companion object {
        const val ID = "projecte-sync_knowledge"
        val INSTANCE: KnowledgeData
            get() {
                val world = FMLCommonHandler.instance().minecraftServerInstance.worlds[0]
                val mapStorage = world.mapStorage!!
                val data = mapStorage.getOrLoadData(KnowledgeData::class.java, ID)?: KnowledgeData().also { mapStorage.setData(ID, it) }
                return data as KnowledgeData
            }
    }


    val knowledge: MutableList<ItemStack> = mutableListOf()

    val inputLocks: IItemHandlerModifiable = ItemStackHandler(9)

    var emc: Long = 0L
        set(value) {
            if(value == field) { return }
            this.markDirty()
            field = value
        }

    var fullKnowledge: Boolean = false
        set(value) {
            if(value == field) { return }
            this.markDirty()
            field = value
        }

    override fun readFromNBT(nbt: NBTTagCompound) {
        this.emc = nbt.getLong("transmutationEmc")
        val knowledge = nbt.getTagList("knowledge", Constants.NBT.TAG_COMPOUND)
        knowledge.forEachIndexed { index, _ ->
            val item = ItemStack(knowledge.getCompoundTagAt(index))
            if(!item.isEmpty) { this.knowledge.add(item) }
        }
        this.pruneStateKnowledge()
        this.pruneDuplicateKnowledge()
        repeat(this.inputLocks.slots) { this.inputLocks.setStackInSlot(it, ItemStack.EMPTY) }
        CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.readNBT(this.inputLocks, null, nbt.getTagList("inputlock", Constants.NBT.TAG_COMPOUND))
        this.fullKnowledge = nbt.getBoolean("fullknowledge")
    }

    private fun pruneDuplicateKnowledge() {
        ItemHelper.removeEmptyTags(this.knowledge)
        ItemHelper.compactItemListNoStacksize(this.knowledge)
        this.knowledge.forEach { itemStack -> if(itemStack.count > 1) { itemStack.count = 1 } }
    }

    private fun pruneStateKnowledge() {
        this.knowledge.removeIf { !EMCHelper.doesItemHaveEmc(it) }
    }

    override fun writeToNBT(nbt: NBTTagCompound): NBTTagCompound {
        nbt.setLong("transmutationEmc", this.emc)
        val knowledgeWrite = NBTTagList().also { l -> this.knowledge.forEach { i -> l.appendTag(i.writeToNBT(
            NBTTagCompound()
        )) } }
        nbt.setTag("knowledge", knowledgeWrite)
        nbt.setTag("inputlock", CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.writeNBT(this.inputLocks, null)?:NBTTagCompound())
        nbt.setBoolean("fullknowledge", this.fullKnowledge)
        return nbt
    }

}