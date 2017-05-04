package com.creativemd.playerrevive;

import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.util.INBTSerializable;

public class Revival implements INBTSerializable<NBTTagCompound> {
	
	@CapabilityInject(Revival.class)
	public static Capability<Revival> reviveCapa = null;
	
	private boolean healty = true;
	
	private float progress;
	private int timeLeft;
	
	public ArrayList<EntityPlayer> revivingPlayers = new ArrayList<>();
	
	public Revival() {
		this.progress = 0;
		timeLeft = PlayerRevive.playerReviveSurviveTime;
	}
	
	public void tick()
	{
		timeLeft--;
		progress += revivingPlayers.size();
		
		for (int i = 0; i < revivingPlayers.size(); i++) {
			revivingPlayers.get(i).addExhaustion(0.5F);
		}
	}
	
	public boolean isHealty()
	{
		return healty;
	}
	
	public void stopBleeding()
	{
		this.healty = true;
	}
	
	public void startBleeding()
	{
		this.healty = false;
		this.progress = 0;
		timeLeft = PlayerRevive.playerReviveSurviveTime;
	}
	
	public float getProgress()
	{
		return progress;
	}
	
	public boolean isRevived()
	{
		return progress >= PlayerRevive.playerReviveTime;
	}
	
	public boolean isDead()
	{
		return timeLeft <= 0;
	}
	
	public int getTimeLeft()
	{
		return timeLeft;
	}
	
	public void kill()
	{
		timeLeft = 0;
		progress = 0;
	}
	
	public void writeToNBT(NBTTagCompound nbt)
	{
		nbt.setInteger("timeLeft", timeLeft);
		nbt.setFloat("progress", progress);
		nbt.setBoolean("healty", healty);
	}
	
	public void readFromNBT(NBTTagCompound nbt)
	{
		timeLeft = nbt.getInteger("timeLeft");
		progress = nbt.getFloat("progress");
		healty = nbt.getBoolean("healty");
	}

	@Override
	public NBTTagCompound serializeNBT() {
		NBTTagCompound nbt = new NBTTagCompound();
		writeToNBT(nbt);
		return nbt;
	}

	@Override
	public void deserializeNBT(NBTTagCompound nbt) {
		readFromNBT(nbt);
	}

}
