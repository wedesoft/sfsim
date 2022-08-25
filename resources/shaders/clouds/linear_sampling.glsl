#version 410 core

uniform float cloud_scale;
uniform int cloud_size;

int number_of_steps(float a, float b, int max_samples, float max_step)
{
  float dist = b - a;
  int max_step_samples = int(ceil(dist / max_step));
  return min(max_step_samples, max_samples);
}

float scaling_offset(float a, float b, int samples, float max_step)
{
  return 0.0;
}

float step_size(float a, float b, float scaling_offset, int num_steps)
{
  float dist = b - a;
  return dist / num_steps;
}

float next_point(float p, float scaling_offset, float step_size)
{
  return p + step_size;
}

float initial_lod(float a, float scaling_offset, float step_size)
{
  return 6.0;
  float cloud_pixel = cloud_scale / cloud_size;
  return log2(step_size / cloud_pixel);
}

float lod_increment(float step_size)
{
  return 0.0;
}
