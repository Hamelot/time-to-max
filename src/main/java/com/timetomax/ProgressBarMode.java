package com.timetomax;

public enum ProgressBarMode
{
	LEVEL("Level"),
	TARGET("Target");

	private final String name;

	ProgressBarMode(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}