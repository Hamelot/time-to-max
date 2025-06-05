package com.timetomax;

public enum MaxSkillMode
{
	NORMAL("99"),
	COMPLETIONIST("200m");

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
