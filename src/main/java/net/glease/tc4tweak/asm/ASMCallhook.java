package net.glease.tc4tweak.asm;

import net.glease.tc4tweak.ClientProxy;
import thaumcraft.client.gui.GuiResearchTable;
import thaumcraft.common.tiles.TileMagicWorkbench;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ASMCallhook {
	private static final WeakHashMap<TileMagicWorkbench, Void> postponed = new WeakHashMap<>();
	// workbench throttling
	private static long lastUpdate = 0;
	private static final AtomicBoolean cacheUsed = new AtomicBoolean(false);
	private static boolean priorityChanged = false;

	private ASMCallhook() {
	}

	/**
	 * Called from {@link thaumcraft.client.gui.GuiResearchRecipe#getFromCache(int)}
	 */
	@Callhook
	public static void onCacheLookupHead() {
		cacheUsed.lazySet(true);
	}

	/**
	 * Called from {@link thaumcraft.client.gui.MappingThread#run()}
	 */
	@Callhook
	public static void onMappingDidWork() {
		if (!priorityChanged && cacheUsed.get()) {
			Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
			priorityChanged = true;
		}
	}

	public static void updatePostponed() {
		synchronized (postponed) {
			for (Map.Entry<TileMagicWorkbench, Void> workbench : postponed.entrySet()) {
				TileMagicWorkbench tile = workbench.getKey();
				if (tile != null && tile.eventHandler != null && !tile.isInvalid() && tile.hasWorldObj()) {
					// best effort guess on whether tile is valid
					tile.eventHandler.onCraftMatrixChanged(tile);
				}
			}
			postponed.clear();
		}
	}

	/**
	 * called from GuiResearchTable. first arg is this
	 */
	@Callhook
	public static void handleMouseInput(GuiResearchTable screen) {
		ClientProxy.handleMouseInput(screen);
	}

	/**
	 * Throttle the amount of arcane workbench update on client side
	 * called from TileMagicWorkbench.
	 */
	@Callhook
	public static void updateCraftingMatrix(TileMagicWorkbench self) {
		if (!self.getWorldObj().isRemote) {
			self.eventHandler.onCraftMatrixChanged(self);
			return;
		}
		long oldUpdate = lastUpdate;
		lastUpdate = System.currentTimeMillis();
		if (lastUpdate - oldUpdate > 1000 / 5) {
			self.eventHandler.onCraftMatrixChanged(self); // 5 times per second at max
		} else {
			synchronized (postponed) {
				postponed.put(self, null);
			}
		}
	}

}
