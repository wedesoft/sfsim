#version 410 core

uniform float cloud_scale;
uniform int cloud_size;

int number_of_steps(float a, float b, int min_samples, int max_samples, float max_step)
{
  return max(min_samples, min(int(ceil(log(b / a) / log(max_step))), max_samples));
}

float scaling_offset(float a, float b, int samples, float max_step)
{
  float final_scale = pow(max_step, samples);
  return max(0, (b - a * final_scale) / (final_scale - 1));
}

float step_size(float a, float b, float scaling_offset, int num_steps)
{
  return pow((b + scaling_offset) / (a + scaling_offset), 1.0 / num_steps);
}

float next_point(float p, float scaling_offset, float step_size)
{
  return (p + scaling_offset) * step_size - scaling_offset;
}

float initial_lod(float a, float scaling_offset, float step_size)
{
  float cloud_pixel = cloud_scale / cloud_size;
  return log2((a + scaling_offset) * (step_size - 1) / cloud_pixel);
}

float lod_increment(float step_size)
{
  return log2(step_size);
}
