package com.hmill;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GrandExchangeRestrictorPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GrandExchangeRestrictorPlugin.class);
		RuneLite.main(args);
	}
}
