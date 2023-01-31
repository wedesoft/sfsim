#version 410 core

uniform float cloud_scale;
uniform int cloud_size;

float scaling_offset(float a, float b, int samples, float max_step)
{
  return 0.0;
}

float step_size(float a, float b, float scaling_offset, int num_steps)
{
  float dist = b - a;
  return dist / num_steps;
}

float sample_point(float a, float scaling_offset, int idx, float step_size)
{
  return a + idx * step_size;
}

float initial_lod(float a, float scaling_offset, float step_size)
{
  float cloud_pixel = cloud_scale / cloud_size;
  return log2(step_size / cloud_pixel);
}

float lod_increment(float step_size)
{
  return 0.0;
}
