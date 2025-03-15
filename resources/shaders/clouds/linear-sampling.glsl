#version 410 core

float sample_point(float a, float idx, float step_size)
{
  return a + idx * step_size;
}
