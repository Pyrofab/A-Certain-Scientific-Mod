package tk.valoeghese.tknm.client.abilityrenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3d;
import tk.valoeghese.tknm.api.ability.AbilityRenderer;
import tk.valoeghese.tknm.api.rendering.WORST;
import tk.valoeghese.tknm.common.ability.ElectromasterAbility;
import tk.valoeghese.tknm.util.MathsUtils;

public class ElectromasterAbilityRenderer implements AbilityRenderer {
	@Override
	public void renderInfo(ClientWorld world, Vec3d pos, float yaw, float pitch, UUID user, int[] data) {
		final int chargeInfo = data[0] & 0b11;
		final int mode = data[0] >> 2;

		if (mode == ElectromasterAbility.USAGE_RAILGUN) {
			this.railguns.add(new RailgunEntry(
					new Vector3f((float)pos.getX(), (float)pos.getY() + 1.25f, (float)pos.getZ()),
					new Quaternion(0, 270 - yaw, 360 - pitch, true),
					Float.intBitsToFloat(data[1]),
					world.getTime() + 40));
		}

		switch (chargeInfo) {
		case ElectromasterAbility.CHARGE_OFF:
			CHARGED.put(user, false);
			TO_DISCHARGE.put(user, new Pair<>(world.getTime(), world.getTime() + (ElectromasterAbility.CHARGE_DELAY / ElectromasterAbility.DISCHARGE_PROPORTION)));
			break;
		case ElectromasterAbility.CHARGE_ON:
			TO_CHARGE.put(user, new Pair<>(world.getTime(), world.getTime() + ElectromasterAbility.CHARGE_DELAY));
			break;
		}
	}

	private final List<RailgunEntry> railguns = new ArrayList<>();
	private static final Map<UUID, Pair<Long, Long>> TO_CHARGE = new HashMap<>();
	private static final Map<UUID, Pair<Long, Long>> TO_DISCHARGE = new HashMap<>();
	private static final Object2BooleanMap<UUID> CHARGED = new Object2BooleanArrayMap<>();

	public static double getOverlayStrength(UUID player, long charge) {
		// if fully charged
		if (CHARGED.getOrDefault(player, false)) {
			return 1.0;
		}

		Pair<Long, Long> entry = TO_CHARGE.get(player);

		// if not building up charge
		if (entry == null) {
			entry = TO_DISCHARGE.get(player);

			// if not discharging
			if (entry == null) {
				return 0.0;
			}

			// swap for discharge
			entry = new Pair<>(entry.getRight(), entry.getLeft());
		}

		// inverse lerp with clamp
		return MathHelper.clamp(MathsUtils.progress(entry.getLeft(), charge, entry.getRight()), 0.0, 1.0);
	}

	@Override
	public void render(ClientWorld world) {
		long time = world.getTime();

		// Charge
		if (!TO_CHARGE.isEmpty()) {
			Set<Map.Entry<UUID, Pair<Long, Long>>> currentSet = new HashSet<>(TO_CHARGE.entrySet());

			for (Map.Entry<UUID, Pair<Long, Long>> entry : currentSet) {
				if (entry.getValue().getRight() < time) {
					CHARGED.put(entry.getKey(), true);
					TO_CHARGE.remove(entry.getKey());
				}
			}
		}

		// Discharge
		if (!TO_DISCHARGE.isEmpty()) {
			Set<Map.Entry<UUID, Pair<Long, Long>>> currentSet = new HashSet<>(TO_DISCHARGE.entrySet());

			for (Map.Entry<UUID, Pair<Long, Long>> entry : currentSet) {
				if (entry.getValue().getRight() < time) {
					TO_DISCHARGE.remove(entry.getKey());
				}
			}
		}

		// for every railgun beam, if they exist
		if (!this.railguns.isEmpty()) {
			int i = this.railguns.size();

			while (--i >= 0) {
				if (this.railguns.get(i).render(world)) {
					this.railguns.remove(i);
				}
			}
		}

		CHARGED.forEach((uuid, charged) -> {
			if (charged) {
				PlayerEntity player = world.getPlayerByUuid(uuid);

				if (player != null) {
				}
			}
		});
	}

	private static class RailgunEntry {
		private RailgunEntry(Vector3f pos, Quaternion rotation, float distance, long tickTarget) {
			this.pos = pos;
			this.rotation = rotation;
			this.distance = distance;
			this.tickTarget = tickTarget;
		}

		Vector3f pos;
		Quaternion rotation;
		final float distance;
		final long tickTarget;

		private boolean render(ClientWorld world) {
			WORST.mesh();
			WORST.bindBlockTexture(new Identifier("block/orange_concrete"));
			WORST.basicCube(null, 0.5f, 0, 0);
			WORST.renderMesh(this.pos, this.rotation, new Vector3f(this.distance, 0.12f, 0.12f));
			return world.getTime() >= this.tickTarget;
		}
	}
}