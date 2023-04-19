#version 410 core

// Remap function used in the Decima Engine
// https://www.guerrilla-games.com/read/nubis-authoring-real-time-volumetric-cloudscapes-with-the-decima-engine

float remap(float value, float original_min, float original_max, float new_min, float new_max)
{
  return new_min + (value - original_min) / (original_max - original_min) * (new_max - new_min);
}
