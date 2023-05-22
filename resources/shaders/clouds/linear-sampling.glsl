#version 410 core

uniform float cloud_scale;
uniform int cloud_size;

float step_size(float a, float b, int num_steps)
{
  float dist = b - a;
  return dist / num_steps;
}

float sample_point(float a, float idx, float step_size)
{
  return a + idx * step_size;
}

float initial_lod(float step_size)
{
  float cloud_pixel = cloud_scale / cloud_size;
  return log2(step_size / cloud_pixel);
}
