package com.mlmqol;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MlmQolTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MlmQolPlugin.class);
		RuneLite.main(args);
	}
}
