#version 410 core

uniform float cloud_scale;
uniform int cloud_size;

int number_of_steps(float a, float b, int min_samples, int max_samples, float max_step)
{
  float dist = b - a;
  int max_step_samples = int(ceil(dist / max_step));
  return max(min_samples, min(max_step_samples, max_samples));
}

float step_size(float a, float b, int num_steps)
{
  float dist = b - a;
  return dist / num_steps;
}

float sample_point(float a, int idx, float step_size)
{
  return a + idx * step_size;
}

float initial_lod(float a, float step_size)
{
  float cloud_pixel = cloud_scale / cloud_size;
  return log2(step_size / cloud_pixel);
}

float lod_increment(float step_size)
{
  return 0.0;
}
