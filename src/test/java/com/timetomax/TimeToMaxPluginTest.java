package com.timetomax;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TimeToMaxPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TimeToMaxPlugin.class);
		RuneLite.main(args);
	}
}