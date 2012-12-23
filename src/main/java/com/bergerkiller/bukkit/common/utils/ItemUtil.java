package com.bergerkiller.bukkit.common.utils;

import java.util.Map;

import org.bukkit.craftbukkit.v1_4_5.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;

import com.bergerkiller.bukkit.common.inventory.InventoryBaseImpl;
import com.bergerkiller.bukkit.common.inventory.ItemParser;

import net.minecraft.server.v1_4_5.EntityItem;
import net.minecraft.server.v1_4_5.Item;
import net.minecraft.server.v1_4_5.ItemStack;

/**
 * Contains item stack, item and inventory utilities
 */
public class ItemUtil {

	/**
	 * Tests if the given ItemStacks can be fully transferred to another array of ItemStacks
	 * 
	 * @param from ItemStack source array
	 * @param to destination Inventory
	 * @return True if full transfer was possible, False if not
	 */
	public static boolean canTransferAll(org.bukkit.inventory.ItemStack[] from, Inventory to) {
		return canTransferAll(from, to.getContents());
	}

	/**
	 * Tests if the given ItemStacks can be fully transferred to another array of ItemStacks
	 * 
	 * @param from ItemStack source array
	 * @param to ItemStack destination array
	 * @return True if full transfer was possible, False if not
	 */
	public static boolean canTransferAll(org.bukkit.inventory.ItemStack[] from, org.bukkit.inventory.ItemStack[] to) {
		Inventory invto = new InventoryBaseImpl(to, true);
		for (org.bukkit.inventory.ItemStack item : cloneItems(from)) {
			transfer(item, invto, Integer.MAX_VALUE);
			if (!LogicUtil.nullOrEmpty(item)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Tests if the given ItemStack can be transferred to the Inventory
	 * 
	 * @return The amount that could be transferred
	 */
	public static int testTransfer(org.bukkit.inventory.ItemStack from, Inventory to) {
		if (LogicUtil.nullOrEmpty(from)) {
			return 0;
		}
		int startAmount = from.getAmount();
		int fromAmount = startAmount;
		for (org.bukkit.inventory.ItemStack item : to.getContents()) {
			fromAmount -= testTransfer(from, item);
			if (fromAmount <= 0) {
				break;
			}
		}
		return startAmount - fromAmount;
	}

	/**
	 * Tests if the two items can be merged
	 * 
	 * @return The amount that could be transferred
	 */
	public static int testTransfer(org.bukkit.inventory.ItemStack from, org.bukkit.inventory.ItemStack to) {
		if (LogicUtil.nullOrEmpty(from)) {
			return 0;
		}
		if (LogicUtil.nullOrEmpty(to)) {
			return Math.min(from.getAmount(), getMaxSize(from));
		}
		if (equalsIgnoreAmount(from, to)) {
			return Math.min(from.getAmount(), getMaxSize(to) - to.getAmount());
		}
		return 0;
	}

	/**
	 * Transfers all ItemStacks from one Inventory to another
	 * 
	 * @param from The Inventory to take ItemStacks from
	 * @param to The Inventory to transfer to
	 * @param maxAmount The maximum amount of items to transfer
	 * @param parser The item parser used to set what items to transfer. Can be null.
	 * @return The amount of items that got transferred
	 */
	public static int transfer(Inventory from, Inventory to, ItemParser parser, int maxAmount) {
		int amountToTransfer = maxAmount;
		if (maxAmount > 0) {
			int tmptrans;
			for (int i = 0; i < from.getSize() && amountToTransfer > 0; i++) {
				org.bukkit.inventory.ItemStack item = from.getItem(i);
				if (LogicUtil.nullOrEmpty(item) || (parser != null && !parser.match(item))) {
					continue;
				}
				tmptrans = transfer(item, to, amountToTransfer);
				if (tmptrans > 0) {
					amountToTransfer -= tmptrans;
					from.setItem(i, item);
				}
			}
		}
		return maxAmount - amountToTransfer;
	}

	/**
	 * Transfers the given ItemStack to multiple slots in the Inventory
	 * 
	 * @param from The ItemStack to transfer
	 * @param to The Inventory to transfer to
	 * @param maxAmount The maximum amount of the item to transfer
	 * @return The amount of the item that got transferred
	 */
	public static int transfer(org.bukkit.inventory.ItemStack from, Inventory to, int maxAmount) {
		if (maxAmount <= 0 || LogicUtil.nullOrEmpty(from)) {
			return 0;
		}
		int tmptrans;
		int amountToTransfer = maxAmount;

		// try to stack to already existing items
		org.bukkit.inventory.ItemStack toitem;
		for (int i = 0; i < to.getSize(); i++) {
			toitem = to.getItem(i);
			if (!LogicUtil.nullOrEmpty(toitem)) {
				tmptrans = transfer(from, toitem, amountToTransfer);
				if (tmptrans > 0) {
					amountToTransfer -= tmptrans;
					to.setItem(i, toitem);
					// everything done?
					if (amountToTransfer <= 0 || LogicUtil.nullOrEmpty(from)){
						break;
					}
				}
			}
		}

		// try to add it to empty slots
		if (amountToTransfer > 0 && from.getAmount() > 0) {
			for (int i = 0; i < to.getSize(); i++) {
				toitem = to.getItem(i);
				if (LogicUtil.nullOrEmpty(toitem)) {
					toitem = emptyItem();
					// Transfer
					tmptrans = transfer(from, toitem, amountToTransfer);
					if (tmptrans > 0) {
						amountToTransfer -= tmptrans;
						to.setItem(i, toitem);
						// everything done?
						if (amountToTransfer <= 0 || LogicUtil.nullOrEmpty(from)) {
							break;
						}
					}
				}
			}
		}
		return maxAmount - amountToTransfer;
	}

	/**
	 * Tries to transfer items from the Inventory to the ItemStack
	 * 
	 * @param from The Inventory to take an ItemStack from
	 * @param to The ItemStack to merge the item taken
	 * @param parser The item parser used to set what item to transfer if the receiving item is empty. Can be null.
	 * @param maxAmount The maximum amount of the item to transfer
	 * @return The amount of the item that got transferred
	 */
	public static int transfer(Inventory from, org.bukkit.inventory.ItemStack to, ItemParser parser, int maxAmount) {
		int trans = 0;
		for (int i = 0; i < from.getSize() && trans < maxAmount; i++) {
			org.bukkit.inventory.ItemStack item = from.getItem(i);
			if (LogicUtil.nullOrEmpty(item)) {
				continue;
			}
			if (LogicUtil.nullOrEmpty(to)) {
				// Parser matching
				if (parser != null && !parser.match(item)) {
					continue;
				}
				// Set item info to this item
				transferInfo(item, to);			
			}
			trans += transfer(item, to, maxAmount - trans);
			from.setItem(i, item);
			
			if (maxAmount == trans) {
				break;
			}
		}
		return trans;
	}

	/**
	 * Merges two ItemStacks together<br>
	 * - If from is empty or null, no transfer happens<br>
	 * - If to is null, no transfer happens<br>
	 * - If to is empty, full transfer occurs
	 * 
	 * @param from The ItemStack to merge
	 * @param to The receiving ItemStack
	 * @param maxAmount The maximum amount of the item to transfer
	 * @return The amount of the item that got transferred
	 */
	public static int transfer(org.bukkit.inventory.ItemStack from, org.bukkit.inventory.ItemStack to, int maxAmount) {
		if (LogicUtil.nullOrEmpty(from) || to == null) {
			return 0;
		}
		int amountToTransfer = Math.min(maxAmount, from.getAmount());

		// Transfering to an empty item, don't bother doing any stacking logic
		if (LogicUtil.nullOrEmpty(to)) {
			// Limit amount by maximum of the from item
			amountToTransfer = Math.min(amountToTransfer, getMaxSize(from));
			if (amountToTransfer <= 0) {
				return 0;
			}
			// Transfer item information
			transferInfo(from, to);
			// Transfer the amount
			to.setAmount(amountToTransfer);
			subtractAmount(from, amountToTransfer);
			return amountToTransfer;
		}

		// Can we stack?
		amountToTransfer = Math.min(amountToTransfer, getMaxSize(to) - to.getAmount());
		if (amountToTransfer <= 0 || !equalsIgnoreAmount(from, to)) {
			return 0;
		}

		// From and to are equal, we can now go ahead and stack them
		addAmount(to, amountToTransfer);
		subtractAmount(from, amountToTransfer);
		return amountToTransfer;
	}

	/**
	 * Transfers the item type, data and enchantments from one item stack to the other
	 * 
	 * @param from which Item Stack to read the info
	 * @param to which Item Stack to transfer the info to
	 */
	public static void transferInfo(org.bukkit.inventory.ItemStack from, org.bukkit.inventory.ItemStack to) {
		to.setTypeId(from.getTypeId());
		to.setDurability(from.getDurability());
		to.addEnchantments(from.getEnchantments());
	}

	/**
	 * Checks whether two item stacks equal, while ignoring the item amounts
	 * 
	 * @param item1 to check
	 * @param item2 to check
	 * @return True if the items have the same type, data and enchantments, False if not
	 */
	public static boolean equalsIgnoreAmount(org.bukkit.inventory.ItemStack item1, org.bukkit.inventory.ItemStack item2) {
		if (item1.getTypeId() != item2.getTypeId() || item1.getDurability() != item2.getDurability()) {
			return false;
		}
		// Enchantment checks
		Map<Enchantment, Integer> ench1 = item1.getEnchantments();
		Map<Enchantment, Integer> ench2 = item2.getEnchantments();
		boolean hasEnchantments = !LogicUtil.nullOrEmpty(ench1);
		// One has enchantments and the other doesn't? Don't stack in that case.
		if (hasEnchantments != !LogicUtil.nullOrEmpty(ench2)) {
			return false;
		}
		// Check if the enchantments are the same
		if (hasEnchantments && (ench1.size() != ench2.size() || !LogicUtil.containsAll(ench1, ench2))) {
			return false;
		}
		return true;
	}

	/**
	 * Removes certain kinds of items from an inventory
	 * 
	 * @param inventory to remove items from
	 * @param item signature of the items to remove
	 */
	public static void removeItems(Inventory inventory, org.bukkit.inventory.ItemStack item) {
		removeItems(inventory, item.getTypeId(), (int) item.getDurability(), item.getAmount());
	}

	/**
	 * Removes certain kinds of items from an inventory
	 * 
	 * @param inventory to remove items from
	 * @param itemid of the items to remove
	 * @param data of the items to remove, -1 for any data
	 * @param amount of items to remove, -1 for infinite amount
	 */
	public static void removeItems(Inventory inventory, int itemid, int data, int amount) {
		int countToRemove = amount < 0 ? Integer.MAX_VALUE : amount;
		for (int i = 0; i < inventory.getSize(); i++) {
			org.bukkit.inventory.ItemStack item = inventory.getItem(i);
			if (LogicUtil.nullOrEmpty(item) || item.getTypeId() != itemid || (data != -1 && item.getDurability() != data)) {
				continue;
			}
			if (item.getAmount() <= countToRemove) {
				countToRemove -= item.getAmount();
				inventory.setItem(i, null);
			} else {
				addAmount(item, -countToRemove);
				countToRemove = 0;
				inventory.setItem(i, item);
				break;
			}
		}
	}

	/**
	 * Obtains an empty item stack that allows mutual changes<br>
	 * This is a CraftItemStack with a NMS ItemStack as buffer
	 * 
	 * @return Empty item stack
	 */
	public static org.bukkit.inventory.ItemStack emptyItem() {
		return CraftItemStack.asCraftMirror(new ItemStack(0, 0, 0));
	}

	/**
	 * Kills the old item and spawns a new item in it's place
	 * 
	 * @param item to respawn
	 * @return Respawned item
	 */
	public static org.bukkit.entity.Item respawnItem(org.bukkit.entity.Item bitem) {
		bitem.remove();
		EntityItem item = NativeUtil.getNative(bitem);
		EntityItem newItem = new EntityItem(item.world, item.locX, item.locY, item.locZ, item.itemStack);
		newItem.fallDistance = item.fallDistance;
		newItem.fireTicks = item.fireTicks;
		newItem.pickupDelay = item.pickupDelay;
		newItem.motX = item.motX;
		newItem.motY = item.motY;
		newItem.motZ = item.motZ;
		newItem.age = item.age;
		newItem.world.addEntity(newItem);
		return NativeUtil.getItem(newItem);
	}

	/**
	 * Clones a single item
	 * 
	 * @param stack to be cloned, can be null
	 * @return Cloned item stack
	 */
	public static org.bukkit.inventory.ItemStack cloneItem(org.bukkit.inventory.ItemStack stack) {
		return stack == null ? null : stack.clone();
	}

	/**
	 * Creates a new itemstack array containing items not referencing the input item stacks
	 * 
	 * @param input array to process
	 * @return Cloned item stack array
	 */
	public static org.bukkit.inventory.ItemStack[] cloneItems(org.bukkit.inventory.ItemStack[] input) {
		org.bukkit.inventory.ItemStack[] cloned = new org.bukkit.inventory.ItemStack[input.length];
		for (int i = 0; i < cloned.length; i++) {
			cloned[i] = cloneItem(input[i]);
		}
		return cloned;
	}

	/**
	 * Subtracts a certain amount from an item, without limiting to the max stack size
	 * 
	 * @param item
	 * @param amount to subtract
	 */
	public static void subtractAmount(org.bukkit.inventory.ItemStack item, int amount) {
		addAmount(item, -amount);
	}

	/**
	 * Adds a certain amount to an item, without limiting to the max stack size
	 * 
	 * @param item
	 * @param amount to add
	 */
	public static void addAmount(org.bukkit.inventory.ItemStack item, int amount) {
		item.setAmount(Math.max(item.getAmount() + amount, 0));
	}

	/**
	 * Obtains an item of the given type and data in the inventory specified<br>
	 * If multiple items with the same type and data exist, their amounts are added together
	 * 
	 * @param inventory to look in
	 * @param typeId of the items to look for, -1 for any item
	 * @param data of the items to look for, -1 for any data
	 * @return Amount of items in the inventory
	 */
	public static org.bukkit.inventory.ItemStack findItem(Inventory inventory, int typeId, int data) {
		org.bukkit.inventory.ItemStack rval = null;
		int itemData = data;
		int itemTypeId = typeId;
		for (org.bukkit.inventory.ItemStack item : inventory.getContents()) {
			if (LogicUtil.nullOrEmpty(item)) {
				continue;
			}
			// Compare type Id
			if (itemTypeId == -1) {
				itemTypeId = item.getTypeId();
			} else if (itemTypeId != item.getTypeId()) {
				continue;
			}
			// Compare data
			if (itemData == -1) {
				itemData = item.getDurability();
			} else if (item.getDurability() != itemData) {
				continue;
			}
			// addition
			if (rval == null) {
				rval = item.clone();
			} else {
				addAmount(rval, item.getAmount());
			}
		}
		return rval;
	}

	/**
	 * Gets the total item count of a given type and data
	 * 
	 * @param inventory to look in
	 * @param typeid of the items to look for, -1 for any item
	 * @param data of the items to look for, -1 for any data
	 * @return Amount of items in the inventory
	 */
	public static int getItemCount(Inventory inventory, int typeid, int data) {
		if (typeid < 0) {
			int count = 0;
			for (org.bukkit.inventory.ItemStack item : inventory.getContents()) {
				if (!LogicUtil.nullOrEmpty(item)) {
					count += item.getAmount();
				}
			}
			return count;
		} else {
			org.bukkit.inventory.ItemStack rval = findItem(inventory, typeid, data);
			return rval == null ? 0 : rval.getAmount();
		}
	}

	/**
	 * Gets the max stacking size for a given item
	 * 
	 * @param stack to get the max stacked size
	 * @return max stacking size
	 */
	public static int getMaxSize(org.bukkit.inventory.ItemStack stack) {
		if (LogicUtil.nullOrEmpty(stack)) {
			return 0;
		} else {
			ItemStack nitem = NativeUtil.getNative(stack);
			if (nitem == null) {
				return 0;
			}
			Item item = nitem.getItem();
			if (item == null) {
				return 0;
			} else {
				return item.getMaxStackSize();
			}
		}
	}
}
