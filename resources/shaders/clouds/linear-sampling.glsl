#version 410 core

uniform float cloud_scale;
uniform int cloud_size;

int number_of_samples(float a, float b, float max_step)
{
  float dist = b - a;
  float count = ceil(dist / max_step);
  return max(int(count), 1);
}

float step_size(float a, float b, int num_samples)
{
  float dist = b - a;
  return dist / num_samples;
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
