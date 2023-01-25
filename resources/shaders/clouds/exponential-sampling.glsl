#version 410 core

uniform float cloud_scale;
uniform int cloud_size;

int number_of_steps(float a, float b, int min_samples, int max_samples, float max_step)
{
  return max(min_samples, min(int(ceil(log(b / a) / log(max_step))), max_samples));
}

float step_size(float a, float b, int num_steps)
{
  return pow(b / a, 1.0 / num_steps);
}

float sample_point(float a, int idx, float step_size)
{
  return a * pow(step_size, idx);
}

float initial_lod(float a, float step_size)
{
  float cloud_pixel = cloud_scale / cloud_size;
  return log2(a * (step_size - 1) / cloud_pixel);
}

float lod_increment(float step_size)
{
  return log2(step_size);
}
