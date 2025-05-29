package com.timetomax;

public enum MaxSkillMode
{
	NORMAL("Normal"),
	COMPLETIONIST("Completionist");

	private final String mode;

	MaxSkillMode(String mode)
	{
		this.mode = mode;
	}

	@Override
	public String toString()
	{
		return mode;
	}
}
